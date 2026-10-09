package dev.metis.agent

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.FieldCipher
import dev.metis.agent.data.storage.LocalAcceptedTaskStore
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordCodec
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import dev.metis.agent.domain.storage.SavedUserProfile
import dev.metis.agent.domain.storage.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskCompletionStoreTest {
    @Test
    fun onlyOneExactOpenNonrecurringTitleCanBeProposed(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val task = SavedTask("Synthetic study!")
            fixture.repository.saveTask(task)
            assertEquals(AgentResultStatus.FOLLOW_UP, result(fixture.repository, "complete task: study!").status)
            assertEquals(AgentResultStatus.PROPOSAL,
                result(fixture.repository, "complete task: Synthetic study!").status)
            assertEquals(AgentResultStatus.DENIED,
                result(fixture.repository, "don't complete task: Synthetic study!").status)
            fixture.repository.saveTask(SavedTask(task.title.lowercase()))
            assertEquals(AgentResultStatus.FOLLOW_UP,
                result(fixture.repository, "complete task: Synthetic study!").status)
            fixture.repository.saveTask(SavedTask("Synthetic recurring", recurrenceRule = "FREQ=DAILY",
                recurrenceZoneId = "UTC"))
            assertEquals(AgentResultStatus.FOLLOW_UP,
                result(fixture.repository, "complete task: Synthetic recurring").status)
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
            assertTrue(fixture.repository.observeTasks().first().all { it.status == TaskStatus.OPEN })
        }
    }

    @Test
    fun concurrentAcceptanceChangesOnlyStatusOnceWithBoundReceiptAndAudit(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val task = SavedTask("Synthetic study", notes = "Synthetic private notes", priority = 3,
                estimatedSeconds = 1_800)
            fixture.repository.saveTask(task)
            fixture.repository.saveMemory(SavedMemory("Synthetic linked fact", entityType = MemoryEntityType.TASK,
                entityId = task.metadata.id))
            val memories = fixture.repository.observeMemories().first()
            val proposal = result(fixture.repository).proposals.single()
            assertEquals(TaskStatus.OPEN, fixture.repository.observeTasks().first().single().status)
            val store = fixture.repository.taskActions
            val receipts = listOf(async { store.accept(proposal, UUID.randomUUID()) },
                async { store.accept(proposal, UUID.randomUUID()) }).map { it.await() }
            assertEquals(receipts[0].id, receipts[1].id)
            val saved = fixture.repository.observeTasks().first().single()
            assertEquals(task.copy(status = TaskStatus.COMPLETED, completedAt = saved.completedAt,
                metadata = saved.metadata), saved)
            assertEquals(1L, saved.metadata.revision)
            assertEquals(2, fixture.repository.foundation.actionAudit.observe().first().size)
            assertEquals(memories, fixture.repository.observeMemories().first())
            assertTrue(fixture.repository.foundation.event.observe().first().isEmpty())
            val run = fixture.repository.foundation.actionRun.observe().first().single()
            assertEquals(task.metadata.id, run.entityId)
            assertFalse(String(fixture.database.actionRun().find(run.metadata.id)!!.payload).contains(task.title))
        }
    }

    @Test
    fun staleTargetsAndIncompletePrerequisitesDenyBeforeReservation(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val task = SavedTask("Synthetic study")
            fixture.repository.saveTask(task)
            val proposal = result(fixture.repository).proposals.single()
            fixture.repository.saveTask(task.copy(notes = "Synthetic edited notes"))
            assertThrows(ActionRejectedException::class.java) {
                runBlocking { fixture.repository.taskActions.accept(proposal, UUID.randomUUID()) }
            }
            val prerequisite = SavedTask("Synthetic prerequisite")
            fixture.repository.saveTask(prerequisite)
            val currentProposal = result(fixture.repository).proposals.single()
            fixture.repository.dependencies.saveDependency(SavedTaskDependency(task.metadata.id,
                prerequisite.metadata.id))
            assertThrows(ActionRejectedException::class.java) {
                runBlocking { fixture.repository.taskActions.accept(currentProposal, UUID.randomUUID()) }
            }
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
            assertTrue(fixture.repository.observeTasks().first().all { it.status == TaskStatus.OPEN })
        }
    }

    @Test
    fun receiptWriteFailureRollsBackCompletionAndPendingRetryVerifiesOnce(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            fixture.repository.saveTask(SavedTask("Synthetic study"))
            val failing = LocalPersonalRepository(fixture.database, CompletionReceiptFailure(fixture.cipher))
            val proposal = result(failing).proposals.single()
            assertThrows(IllegalStateException::class.java) {
                runBlocking { failing.taskActions.accept(proposal, UUID.randomUUID()) }
            }
            assertEquals(TaskStatus.OPEN, fixture.repository.observeTasks().first().single().status)
            assertEquals(0L, fixture.repository.observeTasks().first().single().metadata.revision)
            assertEquals("PENDING", fixture.repository.foundation.actionRun.observe().first().single().status)
            val receipt = fixture.repository.taskActions.retry(proposal.action.identity.id)
            assertEquals(ActionStatus.SUCCEEDED, receipt.outcome.status)
            assertEquals(receipt.id, fixture.repository.taskActions.retry(proposal.action.identity.id).id)
            assertEquals(1L, fixture.repository.observeTasks().first().single().metadata.revision)
        }
    }

    @Test
    fun expiredCancelledRevokedOrChangedPendingCompletionDoesNotMutate(): Unit = runBlocking {
        listOf("expired", "cancelled", "revoked", "changed", "deleted").forEach { mode ->
            StorageTestFixture().use { fixture ->
                fixture.repository.saveTask(SavedTask("Synthetic study"))
                val failing = LocalPersonalRepository(fixture.database, CompletionReceiptFailure(fixture.cipher))
                val proposal = result(failing).proposals.single()
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { failing.taskActions.accept(proposal, UUID.randomUUID()) }
                }
                val task = fixture.repository.observeTasks().first().single()
                when (mode) {
                    "cancelled" -> fixture.repository.taskActions.cancelPending(proposal.action.identity.id)
                    "revoked" -> fixture.repository.foundation.userProfile.save(SavedUserProfile("Synthetic profile",
                        "UTC", "en-IN", 0, behavioralAnalysisConsent = false, active = true))
                    "changed" -> fixture.repository.saveTask(task.copy(title = "Synthetic changed"))
                    "deleted" -> fixture.repository.deleteTask(task.metadata.id, task.metadata.revision)
                }
                val store = LocalAcceptedTaskStore(fixture.repository, fixture.database, RecordCodec(fixture.cipher),
                    if (mode == "expired") Clock.fixed(proposal.expiresAt, ZoneId.of("UTC")) else Clock.systemUTC())
                if (mode == "deleted") {
                    assertThrows(IllegalArgumentException::class.java) {
                        runBlocking { store.retry(proposal.action.identity.id) }
                    }
                    assertTrue(fixture.repository.observeTasks().first().isEmpty())
                } else {
                    val receipt = store.retry(proposal.action.identity.id)
                    assertEquals(if (mode == "cancelled") ActionStatus.CANCELLED else ActionStatus.FAILED,
                        receipt.outcome.status)
                    assertEquals(TaskStatus.OPEN, fixture.repository.observeTasks().first().single().status)
                }
            }
        }
    }

    @Test
    fun reopenedReceiptCanUndoStatusOnceAndHistoryRecordsUndo(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            fixture.repository.saveTask(SavedTask("Synthetic study"))
            val receipt = fixture.repository.taskActions.accept(result(fixture.repository).proposals.single(),
                UUID.randomUUID())
            fixture.database.close()
            val database = Room.databaseBuilder(fixture.context, PersonalDatabase::class.java, fixture.name)
                .addCallback(RecordConstraints).build()
            try {
                val repository = LocalPersonalRepository(database, fixture.cipher)
                val entry = repository.taskActions.observeHistory().first().single()
                assertTrue(entry.completion)
                assertEquals(receipt.id, entry.receipt!!.id)
                assertEquals(UndoResult.UNDONE, repository.taskActions.undo(entry.receipt))
                val task = repository.observeTasks().first().single()
                assertEquals(TaskStatus.OPEN, task.status)
                assertEquals(null, task.completedAt)
                assertEquals(2L, task.metadata.revision)
                assertEquals(UndoResult.UNDONE, repository.taskActions.undo(entry.receipt))
                assertEquals(2L, repository.observeTasks().first().single().metadata.revision)
                val history = repository.taskActions.observeHistory().first().single()
                assertTrue(history.undone)
                assertEquals(null, history.receipt)
            } finally { database.close() }
        }
    }

    @Test
    fun editedTaskOrCompletedDependentBlocksUndo(): Unit = runBlocking {
        listOf(false, true).forEach { dependency ->
            StorageTestFixture().use { fixture ->
                fixture.repository.saveTask(SavedTask("Synthetic study"))
                val receipt = fixture.repository.taskActions.accept(result(fixture.repository).proposals.single(),
                    UUID.randomUUID())
                val task = fixture.repository.observeTasks().first().single()
                if (dependency) {
                    val dependent = SavedTask("Synthetic dependent", status = TaskStatus.COMPLETED,
                        completedAt = System.currentTimeMillis())
                    fixture.repository.saveTask(dependent)
                    fixture.repository.dependencies.saveDependency(SavedTaskDependency(dependent.metadata.id,
                        task.metadata.id))
                } else fixture.repository.saveTask(task.copy(notes = "Synthetic post-completion edit"))
                assertEquals(UndoResult.CONFLICT, fixture.repository.taskActions.undo(receipt))
                assertEquals(TaskStatus.COMPLETED, fixture.repository.observeTasks().first()
                    .single { it.metadata.id == task.metadata.id }.status)
            }
        }
    }

    private suspend fun result(repository: LocalPersonalRepository, text: String = "complete task: Synthetic study") =
        ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions)
            .process(AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, Instant.now()))
}

private class CompletionReceiptFailure(private val delegate: FieldCipher) : FieldCipher {
    private var fail = true
    override fun encrypt(value: String, binding: String): ByteArray {
        if (fail && binding.startsWith("action_runs/") && binding.endsWith("/receipt")) {
            fail = false
            error("Synthetic completion receipt failure")
        }
        return delegate.encrypt(value, binding)
    }
    override fun decrypt(value: ByteArray, binding: String) = delegate.decrypt(value, binding)
}
