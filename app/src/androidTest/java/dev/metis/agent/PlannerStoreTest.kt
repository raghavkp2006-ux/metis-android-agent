package dev.metis.agent

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.FieldCipher
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.LocalPlannerStore
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordCodec
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.planner.PlanDraft
import dev.metis.agent.domain.planner.PlannerAgent
import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.SavedPreference
import dev.metis.agent.domain.storage.SavedRoutine
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedUserProfile
import dev.metis.agent.domain.storage.ScheduleStatus
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerStoreTest {
    private val at = Instant.parse("2026-10-09T07:00:00Z")
    private val zone = ZoneId.of("UTC")

    @Test fun previewWritesNothingAndAcceptanceAtomicallyStoresEncryptedBlocksReceiptAuditAndOneEvent(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic private planned task", estimatedSeconds = 600))
            val before = f.repository.observeTasks().first()
            val store = store(f)
            val draft = requireNotNull(store.prepare(request()).draft)
            assertTrue(f.repository.observeSchedules().first().isEmpty())
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
            val receipt = store.accept(draft, UUID.randomUUID())
            assertTrue(receipt.reason.contains("No reminders were scheduled"))
            assertEquals(before, f.repository.observeTasks().first())
            val blocks = f.repository.observeSchedules().first()
            assertEquals(draft.blocks, blocks)
            assertEquals(ScheduleStatus.ACCEPTED, blocks.single().status)
            val encoded = requireNotNull(f.database.records().schedule(blocks.single().metadata.id))
            assertFalse(encoded.title.toString(Charsets.UTF_8).contains("Synthetic"))
            assertFalse(encoded.scoreComponentsJson!!.toString(Charsets.UTF_8).contains("DEADLINE_PLAN_V1"))
            assertEquals("DEADLINE_PLAN_V1", JSONObject(blocks.single().scoreComponentsJson!!).getString("version"))
            assertEquals(1, f.repository.foundation.actionRun.observe().first().size)
            assertEquals(1, f.repository.foundation.actionAudit.observe().first().size)
            val event = f.repository.foundation.event.observe().first().single()
            assertEquals("PLAN_ACCEPTED", event.type)
            assertEquals(receipt.identity.id.toString(), event.actionId)
            assertTrue(f.repository.foundation.reminder.observe().first().isEmpty())
        }
    }

    @Test fun concurrentAcceptanceAndDatabaseReopenReplayDoNotDuplicateBlocksOrEvents(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic replay plan", estimatedSeconds = 600))
            val store = store(f)
            val draft = requireNotNull(store.prepare(request()).draft)
            val results = List(2) { async { store.accept(draft, UUID.randomUUID()) } }.awaitAll()
            assertEquals(results[0].id, results[1].id)
            f.database.close()
            val reopened = Room.databaseBuilder(f.context, PersonalDatabase::class.java, f.name)
                .addCallback(RecordConstraints).build()
            try {
                val repository = LocalPersonalRepository(reopened, f.cipher)
                val restored = LocalPlannerStore(repository, RecordCodec(f.cipher), Clock.fixed(at, zone), { zone })
                assertEquals(results[0].id, restored.accept(draft, UUID.randomUUID()).id)
                assertEquals(1, repository.observeSchedules().first().size)
                assertEquals(1, repository.foundation.event.observe().first().size)
            } finally { reopened.close() }
        }
    }

    @Test fun changingTasksPreferencesOrCommitmentsInvalidatesTheEntireSnapshot(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic stale plan", estimatedSeconds = 600))
            val store = store(f)
            val taskDraft = requireNotNull(store.prepare(request()).draft)
            f.repository.saveTask(f.repository.observeTasks().first().single().copy(priority = 3))
            rejected(store, taskDraft)
            val preferenceDraft = requireNotNull(store.prepare(request()).draft)
            f.repository.preferences.savePreference(SavedPreference(PreferenceKey.DAY_START_TIME,
                PreferenceValue.DayStart(LocalTime.of(10, 0))))
            rejected(store, preferenceDraft)
            val busyDraft = requireNotNull(store.prepare(request()).draft)
            f.repository.saveSchedule(SavedSchedule("Synthetic new commitment", busyDraft.window.span.start,
                busyDraft.window.span.start + 60_000, "UTC", "Busy", status = ScheduleStatus.ACCEPTED))
            rejected(store, busyDraft)
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    @Test fun competingPlansCannotBothCommitTheSameSnapshot(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic competing plans", estimatedSeconds = 600))
            val store = store(f)
            val first = requireNotNull(store.prepare(request()).draft)
            val second = requireNotNull(store.prepare(request()).draft)
            store.accept(first, UUID.randomUUID())
            rejected(store, second)
            assertEquals(1, f.repository.observeSchedules().first().size)
        }
    }

    @Test fun expiryPolicyChangesForgedBlocksAndDiscardedProposalPreventAcceptance(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic policy plan", estimatedSeconds = 600))
            val clock = PlannerTestClock(at)
            val store = LocalPlannerStore(f.repository, RecordCodec(f.cipher), clock, { zone })
            val draft = requireNotNull(store.prepare(request()).draft)
            rejected(store, draft.copy(blocks = listOf(draft.blocks.single().copy(title = "Forged"))))
            clock.current = at.plusSeconds(301)
            rejected(store, draft)
            clock.current = at
            f.repository.foundation.userProfile.save(SavedUserProfile("Synthetic answer only", "UTC", "en-IN",
                0, false, true))
            rejected(store, draft)
            val agent = agent(f, store)
            assertEquals(AgentResultStatus.DENIED, agent.process(request()).status)
            assertTrue(f.repository.observeSchedules().first().isEmpty())
        }
    }

    @Test fun explicitPlanRequestInvalidatesPreviousTaskProposalAndDismissedPlanCannotExecute(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic existing plan task", estimatedSeconds = 600))
            val agent = agent(f, store(f))
            val task = agent.process(request("add a task to Synthetic unaccepted task")).proposals.single()
            val plan = agent.process(request()).proposals.single()
            assertEquals(AgentResultStatus.DENIED, agent.accept(task).status)
            agent.discardProposal()
            assertEquals(AgentResultStatus.DENIED, agent.accept(plan).status)
            assertEquals(AgentResultStatus.DENIED, agent.process(request("don't plan my day")).status)
            assertTrue(f.repository.observeSchedules().first().isEmpty())
        }
    }

    @Test fun eventEncryptionFailureRollsBackEveryAcceptedBlockAndAllowsSafeRetry(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic atomic plan", estimatedSeconds = 600))
            val repository = LocalPersonalRepository(f.database, PlanEventFailureCipher(f.cipher))
            val store = LocalPlannerStore(repository, RecordCodec(f.cipher), Clock.fixed(at, zone), { zone })
            val draft = requireNotNull(store.prepare(request()).draft)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { store.accept(draft, UUID.randomUUID()) }
            }
            assertTrue(f.repository.observeSchedules().first().isEmpty())
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
            assertTrue(f.repository.foundation.actionAudit.observe().first().isEmpty())
            assertTrue(f.repository.foundation.event.observe().first().isEmpty())
            store.accept(draft, UUID.randomUUID())
            assertEquals(1, f.repository.observeSchedules().first().size)
        }
    }

    @Test fun deletingAnyGeneratedBlockRedactsItsPlanRunAuditAndTimelineWithoutResurrection(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic first privacy plan task", estimatedSeconds = 600))
            f.repository.saveTask(SavedTask("Synthetic second privacy plan task", estimatedSeconds = 600))
            val store = store(f)
            val request = request()
            val draft = requireNotNull(store.prepare(request).draft)
            store.accept(draft, UUID.randomUUID())
            val block = f.repository.observeSchedules().first().last()
            f.repository.deleteSchedule(block.metadata.id, block.metadata.revision)
            val run = f.repository.foundation.actionRun.observe().first().single()
            assertEquals("PERSONAL_DATA_REMOVED", run.safeErrorCode)
            assertEquals("{}", run.payload)
            assertNull(run.receipt)
            assertNull(f.repository.foundation.event.observe().first().single().payloadMetadata)
            assertEquals("Personal data removed.", f.repository.foundation.actionAudit.observe().first().single().reason)
            rejected(store, draft)
            assertNull(store.prepare(request).draft)
            assertEquals(1, f.repository.observeSchedules().first().size)
        }
    }

    @Test fun deletingTaskRemovesOnlyItsGeneratedBlocksAndRedactsAcceptedPlanHistory(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic task privacy source", estimatedSeconds = 600))
            val store = store(f)
            val draft = requireNotNull(store.prepare(request()).draft)
            store.accept(draft, UUID.randomUUID())
            val task = f.repository.observeTasks().first().single()
            f.repository.deleteTask(task.metadata.id, task.metadata.revision)
            assertTrue(f.repository.observeSchedules().first().isEmpty())
            assertEquals("PERSONAL_DATA_REMOVED", f.repository.foundation.actionRun.observe().first().single().safeErrorCode)
            assertNull(f.repository.foundation.event.observe().first().single().payloadMetadata)
        }
    }

    @Test fun generatedPlanBlocksDoNotBlockConfirmedDeletionAndAreRemovedWithTheirSourceTask(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic task deletion after plan", estimatedSeconds = 600))
            val taskAgent = ConfirmedTaskAgent(LocalAgentReads(f.repository), f.repository.taskActions)
            val proposal = taskAgent.process(request("delete task: Synthetic task deletion after plan")).proposals.single()
            val planner = store(f)
            val draft = requireNotNull(planner.prepare(request()).draft)
            planner.accept(draft, UUID.randomUUID())
            val result = taskAgent.accept(proposal)
            assertEquals(AgentResultStatus.ANSWER, result.status)
            assertEquals("PERSONAL_DATA_REMOVED", f.repository.foundation.actionRun.observe().first()
                .single { it.actionType == "ACCEPT_PLAN" }.safeErrorCode)
            assertTrue(f.repository.observeTasks().first().isEmpty())
            assertTrue(f.repository.observeSchedules().first().isEmpty())
            assertTrue(f.repository.foundation.event.observe().first().filter { it.type == "PLAN_ACCEPTED" }
                .all { it.payloadMetadata == null })
        }
    }

    @Test fun emptyInputsEnabledRoutinesInvalidWindowAndMissingKeyNeverFabricateAcceptedPlans(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val store = store(f)
            assertNull(store.prepare(request()).draft)
            assertEquals(AgentResultStatus.FOLLOW_UP, store.prepare(request("plan my day from 25:00 to 26:00")).result.status)
            f.repository.saveTask(SavedTask("Synthetic unavailable planning key", estimatedSeconds = 600))
            f.repository.foundation.routine.save(SavedRoutine("Synthetic enabled routine", "FREQ=DAILY", "UTC", "{}", true))
            assertNull(store.prepare(request()).draft)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(f.alias) }
            assertEquals(AgentResultStatus.FAILED, agent(f, store).process(request()).status)
            assertTrue(f.database.records().observeSchedules().first().isEmpty())
            assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(f.alias))
        }
    }

    private fun store(f: StorageTestFixture) = LocalPlannerStore(f.repository, RecordCodec(f.cipher),
        Clock.fixed(at, zone), { zone })
    private fun agent(f: StorageTestFixture, store: LocalPlannerStore) = PlannerAgent(
        ConfirmedTaskAgent(LocalAgentReads(f.repository), f.repository.taskActions), store)
    private fun request(text: String = "plan my day tomorrow from 09:00 to 12:00") =
        AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, at)
    private fun rejected(store: LocalPlannerStore, draft: PlanDraft) {
        assertThrows(ActionRejectedException::class.java) { runBlocking { store.accept(draft, UUID.randomUUID()) } }
    }
}

private class PlannerTestClock(var current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current
}
private class PlanEventFailureCipher(private val delegate: FieldCipher) : FieldCipher {
    private var fail = true
    override fun encrypt(value: String, binding: String): ByteArray {
        if (fail && binding.startsWith("events/") && binding.endsWith("/metadata")) {
            fail = false
            error("Synthetic plan event write failure")
        }
        return delegate.encrypt(value, binding)
    }
    override fun decrypt(value: ByteArray, binding: String) = delegate.decrypt(value, binding)
}
