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
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedUserProfile
import java.security.KeyStore
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
class AcceptedTaskStoreTest {
    @Test
    fun concurrentAcceptanceCreatesOneTaskReceiptAndBoundAudit(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val proposal = proposal(fixture.repository)
            assertTrue(fixture.repository.observeTasks().first().isEmpty())
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
            val store = fixture.repository.taskActions
            val results = listOf(async { store.accept(proposal, UUID.randomUUID()) },
                async { store.accept(proposal, UUID.randomUUID()) }).map { it.await() }
            assertEquals(results[0].id, results[1].id)
            assertEquals(1, fixture.repository.observeTasks().first().size)
            val run = fixture.repository.foundation.actionRun.observe().first().single()
            assertEquals("SUCCEEDED", run.status)
            assertEquals("VERIFIED_LOCAL", run.verification)
            assertEquals(proposal.id.toString(), run.proposalId)
            assertEquals(2, fixture.repository.foundation.actionAudit.observe().first().size)
            assertTrue(fixture.repository.foundation.actionAudit.observe().first().all { it.confirmationId != null })
            assertFalse(String(fixture.database.actionRun().find(run.metadata.id)!!.payload).contains("Synthetic"))
            assertTrue(fixture.repository.foundation.event.observe().first().isEmpty())
        }
    }

    @Test
    fun receiptAndUndoSurviveDatabaseReopenWithoutInMemoryProposal(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val proposal = proposal(fixture.repository)
            val receipt = fixture.repository.taskActions.accept(proposal, UUID.randomUUID())
            fixture.database.close()
            val reopened = Room.databaseBuilder(fixture.context, PersonalDatabase::class.java, fixture.name)
                .addCallback(RecordConstraints).build()
            try {
                val repository = LocalPersonalRepository(reopened, fixture.cipher)
                val restored = repository.taskActions.observeHistory().first().single().receipt!!
                assertEquals(receipt.id, restored.id)
                assertEquals(UndoResult.UNDONE, repository.taskActions.undo(restored))
                assertTrue(repository.observeTasks().first().isEmpty())
                assertEquals(UndoResult.UNDONE, repository.taskActions.undo(restored))
                val original = repository.foundation.actionRun.observe().first()
                    .single { it.metadata.id == receipt.identity.id.toString() }
                assertEquals("PERSONAL_DATA_REMOVED", original.safeErrorCode)
                assertEquals("{}", original.payload)
                assertEquals(null, original.receipt)
            } finally { reopened.close() }
        }
    }

    @Test
    fun expiryAndPolicyRevocationDenyUnacceptedProposalWithoutWrites(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val proposal = proposal(fixture.repository)
            val clock = Clock.fixed(proposal.expiresAt, ZoneId.of("UTC"))
            val expiredStore = LocalAcceptedTaskStore(fixture.repository, fixture.database,
                RecordCodec(fixture.cipher), clock)
            assertThrows(ActionRejectedException::class.java) {
                runBlocking { expiredStore.accept(proposal, UUID.randomUUID()) }
            }
            fixture.repository.foundation.userProfile.save(SavedUserProfile("Synthetic profile", "UTC", "en-IN",
                0, behavioralAnalysisConsent = false, active = true))
            assertThrows(ActionRejectedException::class.java) {
                runBlocking { fixture.repository.taskActions.accept(proposal, UUID.randomUUID()) }
            }
            assertTrue(fixture.repository.observeTasks().first().isEmpty())
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    @Test
    fun receiptFailureRollsBackTaskThenDurablePendingCanRetry(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val failing = ReceiptFailureCipher(fixture.cipher)
            val repository = LocalPersonalRepository(fixture.database, failing)
            val proposal = proposal(repository)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.taskActions.accept(proposal, UUID.randomUUID()) }
            }
            assertTrue(fixture.repository.observeTasks().first().isEmpty())
            assertEquals("PENDING", fixture.repository.foundation.actionRun.observe().first().single().status)
            assertEquals(1, fixture.repository.foundation.actionAudit.observe().first().size)
            val receipt = fixture.repository.taskActions.retry(proposal.action.identity.id)
            assertEquals(ActionStatus.SUCCEEDED, receipt.outcome.status)
            assertEquals(1, fixture.repository.observeTasks().first().size)
            assertEquals(receipt.id, fixture.repository.taskActions.retry(proposal.action.identity.id).id)
        }
    }

    @Test
    fun expiredOrCancelledPendingAcceptanceNeverCreatesTask(): Unit = runBlocking {
        listOf("expired", "cancelled", "revoked").forEach { mode ->
            StorageTestFixture().use { fixture ->
                val repository = LocalPersonalRepository(fixture.database, ReceiptFailureCipher(fixture.cipher))
                val proposal = proposal(repository)
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { repository.taskActions.accept(proposal, UUID.randomUUID()) }
                }
                val id = proposal.action.identity.id
                val store = fixture.repository.taskActions
                if (mode == "cancelled") store.cancelPending(id)
                if (mode == "revoked") fixture.repository.foundation.userProfile.save(SavedUserProfile(
                    "Synthetic revoked profile", "UTC", "en-IN", 0, behavioralAnalysisConsent = false, active = true))
                val expiredStore = LocalAcceptedTaskStore(fixture.repository, fixture.database,
                    RecordCodec(fixture.cipher), if (mode == "revoked") Clock.systemUTC()
                    else Clock.fixed(proposal.expiresAt, ZoneId.of("UTC")))
                val receipt = expiredStore.retry(id)
                assertEquals(if (mode == "cancelled") ActionStatus.CANCELLED else ActionStatus.FAILED,
                    receipt.outcome.status)
                if (mode == "revoked") assertTrue(fixture.repository.foundation.actionAudit.observe().first()
                    .any { it.decision == "DENY" && it.autonomyLevel == 0 })
                assertTrue(fixture.repository.observeTasks().first().isEmpty())
            }
        }
    }

    @Test
    fun editedOrNewlyLinkedTaskBlocksUndoWithoutRemovingData(): Unit = runBlocking {
        listOf(false, true).forEach { linked ->
            StorageTestFixture().use { fixture ->
                val receipt = fixture.repository.taskActions.accept(proposal(fixture.repository), UUID.randomUUID())
                val task = fixture.repository.observeTasks().first().single()
                if (linked) fixture.repository.saveMemory(SavedMemory("Synthetic linked task fact",
                    entityType = MemoryEntityType.TASK, entityId = task.metadata.id))
                else fixture.repository.saveTask(task.copy(title = "Synthetic edited task"))
                assertEquals(UndoResult.CONFLICT, fixture.repository.taskActions.undo(receipt))
                assertEquals(1, fixture.repository.observeTasks().first().size)
                assertEquals("SUCCEEDED", fixture.repository.foundation.actionRun.observe().first().single().status)
                if (linked) assertEquals(1, fixture.repository.observeMemories().first().size)
            }
        }
    }

    @Test
    fun missingKeyCannotReplaceKeyOrRetryAcceptedTask(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val repository = LocalPersonalRepository(fixture.database, ReceiptFailureCipher(fixture.cipher))
            val proposal = proposal(repository)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.taskActions.accept(proposal, UUID.randomUUID()) }
            }
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(fixture.alias) }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { fixture.repository.taskActions.retry(proposal.action.identity.id) }
            }
            assertFalse(keys.containsAlias(fixture.alias))
            assertTrue(fixture.repository.observeTasks().first().isEmpty())
            assertEquals("PENDING", fixture.database.actionRun().find(proposal.action.identity.id.toString())!!.status)
        }
    }

    private suspend fun proposal(repository: LocalPersonalRepository) =
        ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions)
            .process(AgentRequest(UUID.randomUUID(), "add a task to Synthetic study", InputSource.TEXT, Instant.now()))
            .proposals.single()
}

private class ReceiptFailureCipher(private val delegate: FieldCipher) : FieldCipher {
    private var fail = true
    override fun encrypt(value: String, binding: String): ByteArray {
        if (fail && binding.startsWith("action_runs/") && binding.endsWith("/receipt")) {
            fail = false
            error("Synthetic receipt write failure")
        }
        return delegate.encrypt(value, binding)
    }
    override fun decrypt(value: ByteArray, binding: String) = delegate.decrypt(value, binding)
}
