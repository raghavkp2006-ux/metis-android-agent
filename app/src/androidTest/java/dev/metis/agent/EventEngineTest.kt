package dev.metis.agent

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.FieldCipher
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.data.storage.LocalEventTimeline
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.UndoResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventEngineTest {
    @Test fun eventEncryptionFailureRollsBackMutationReceiptAndAuditThenRetryRecordsOnce(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val repository = LocalPersonalRepository(f.database, EventFailureCipher(f.cipher))
            val proposal = agent(repository).process(request("add a task to Synthetic event rollback")).proposals.single()
            assertThrows(IllegalStateException::class.java) { runBlocking {
                repository.taskActions.accept(proposal, UUID.randomUUID())
            } }
            assertTrue(f.repository.observeTasks().first().isEmpty())
            assertTrue(f.repository.foundation.event.observe().first().isEmpty())
            assertEquals("PENDING", f.repository.foundation.actionRun.observe().first().single().status)
            assertEquals(1, f.repository.foundation.actionAudit.observe().first().size)
            val receipt = f.repository.taskActions.retry(proposal.action.identity.id)
            assertEquals(receipt.id, f.repository.taskActions.retry(proposal.action.identity.id).id)
            val event = LocalEventTimeline(f.repository.foundation).observe().first().single()
            assertEquals("TASK_CREATED", event.type)
            assertEquals("Synthetic event rollback", event.title)
            assertEquals("VERIFIED_LOCAL", event.verification)
            assertEquals(setOf("CONFIRM", "ALLOW"), event.audit.map { it.decision }.toSet())
            val bytes = requireNotNull(f.database.event().find(event.id)?.payloadMetadata)
            assertFalse(String(bytes).contains("Synthetic event rollback"))
        }
    }

    @Test fun historySurvivesReopenAndPrivacyDeletionScrubsCorrelatedDetails(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val a = agent(f.repository)
            val proposal = a.process(request("add a task to Synthetic private history")).proposals.single()
            a.accept(proposal)
            val id = f.repository.foundation.event.observe().first().single().metadata.id
            f.database.close()
            val reopened = Room.databaseBuilder(f.context, PersonalDatabase::class.java, f.name)
                .addCallback(RecordConstraints).build()
            try {
                val repository = LocalPersonalRepository(reopened, f.cipher)
                assertEquals(id, LocalEventTimeline(repository.foundation).observe().first().single().id)
                val task = repository.observeTasks().first().single()
                repository.deleteTask(task.metadata.id, task.metadata.revision)
                val entry = LocalEventTimeline(repository.foundation).observe().first().single()
                assertNull(entry.title)
                assertNull(repository.foundation.event.observe().first().single().payloadMetadata)
                assertTrue(entry.audit.all { it.reason == "Personal data removed." })
                assertFalse(entry.toString().contains("Synthetic private history"))
            } finally { reopened.close() }
        }
    }

    @Test fun taskEditsCompletionUndoAndDeletionProduceDistinctOutcomesWithoutBlockingDeletion(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val a = agent(f.repository)
            suspend fun accept(text: String) = a.accept(a.process(request(text)).proposals.single())
            accept("add a task to Synthetic event task")
            accept("prioritize task: Synthetic event task to: 3")
            accept("postpone task: Synthetic event task until tomorrow at 23:00")
            val completed = accept("complete task: Synthetic event task").completedActions.single()
            assertEquals(UndoResult.UNDONE, a.undo(completed))
            assertEquals(UndoResult.UNDONE, a.undo(completed))
            val events = f.repository.foundation.event.observe().first()
            assertEquals(5, events.size)
            assertEquals(setOf("TASK_CREATED", "TASK_UPDATED", "TASK_POSTPONED", "TASK_COMPLETED"),
                events.map { it.type }.toSet())
            accept("delete task: Synthetic event task")
            assertTrue(f.repository.observeTasks().first().isEmpty())
            val deleted = f.repository.foundation.event.observe().first()
            assertEquals(6, deleted.size)
            assertEquals(1, deleted.count { it.type == "TASK_DELETED" })
            assertTrue(deleted.filter { it.type != "TASK_DELETED" }.all { it.payloadMetadata == null })
        }
    }

    @Test fun proposalReviewAndCancellationDoNotCreateOutcomeEvents(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val a = agent(f.repository)
            a.process(request("add a task to Synthetic unaccepted task"))
            a.discardProposal()
            a.process(request("do not add a task to Synthetic negated task"))
            assertTrue(f.repository.foundation.event.observe().first().isEmpty())
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    private fun agent(repository: LocalPersonalRepository) =
        ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions)
    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, Instant.now())
}

private class EventFailureCipher(private val delegate: FieldCipher) : FieldCipher {
    private var fail = true
    override fun encrypt(value: String, binding: String): ByteArray {
        if (fail && binding.startsWith("events/") && binding.endsWith("/metadata")) {
            fail = false
            error("Synthetic event write failure")
        }
        return delegate.encrypt(value, binding)
    }
    override fun decrypt(value: ByteArray, binding: String) = delegate.decrypt(value, binding)
}
