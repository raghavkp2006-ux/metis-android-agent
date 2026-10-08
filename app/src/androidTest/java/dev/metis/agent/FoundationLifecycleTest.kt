package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.DerivedInsightCodec
import dev.metis.agent.data.storage.FieldCipher
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.RecordCodec
import dev.metis.agent.data.storage.StoredMetadata
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedDerivedInsight
import dev.metis.agent.domain.storage.SavedEvent
import dev.metis.agent.domain.storage.SavedHabit
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedRecommendation
import dev.metis.agent.domain.storage.SavedRelationship
import dev.metis.agent.domain.storage.SavedReminder
import dev.metis.agent.domain.storage.SavedRoutine
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedUserProfile
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationLifecycleTest {
    private val fixture = StorageTestFixture()
    private val repository = fixture.repository
    private val stores = repository.foundation
    @After fun cleanup() = fixture.close()

    @Test
    fun personDeletionCascadesOwnedRowsAndDetachesReminders(): Unit = runBlocking {
        val person = SavedPerson("Synthetic person")
        stores.person.save(person)
        stores.relationship.save(SavedRelationship(person.metadata.id, "FRIEND", "Synthetic relationship"))
        stores.promise.save(SavedPromise(person.metadata.id, "Synthetic promise", "OPEN"))
        val reminder = reminder().copy(personId = person.metadata.id)
        stores.reminder.save(reminder)
        repository.saveMemory(SavedMemory("Synthetic linked person fact", entityType = MemoryEntityType.PERSON,
            entityId = person.metadata.id))
        stores.person.delete(person.metadata.id, 0)
        assertTrue(stores.relationship.observe().first().isEmpty())
        assertTrue(stores.promise.observe().first().isEmpty())
        assertEquals(reminder.copy(personId = null), stores.reminder.observe().first().single())
        assertTrue(repository.observeMemories().first().isEmpty())
    }

    @Test
    fun routineAndEventDeletionRespectMemorySourcesAndKeepTasks(): Unit = runBlocking {
        val routine = SavedRoutine("Synthetic routine", "FREQ=DAILY", "UTC", "{}", false)
        stores.routine.save(routine)
        val task = SavedTask("Synthetic recurrence", recurrenceRule = "FREQ=DAILY", recurrenceZoneId = "UTC")
        repository.saveTask(task)
        val block = SavedSchedule("Synthetic block", 10, 20, "UTC", "Synthetic reason", taskId = task.metadata.id,
            routineId = routine.metadata.id, scoreComponentsJson = "{\"deadline\":0.5}")
        repository.saveSchedule(block)
        val event = event()
        stores.event.save(event)
        repository.saveMemory(SavedMemory("Synthetic event-sourced fact", sourceEventId = event.metadata.id))
        assertThrows(Exception::class.java) { runBlocking { stores.event.delete(event.metadata.id, 1) } }
        assertEquals(1, repository.observeMemories().first().size)
        stores.event.delete(event.metadata.id, 0)
        assertTrue(repository.observeMemories().first().isEmpty())
        stores.routine.delete(routine.metadata.id, 0)
        assertEquals(block.copy(routineId = null), repository.observeSchedules().first().single())
        assertEquals(task, repository.observeTasks().first().single())
    }

    @Test
    fun taskDeletionRedactsHistoryAndRetainsNonpersonalActionTombstone(): Unit = runBlocking {
        val task = SavedTask("Synthetic private task")
        repository.saveTask(task)
        val action = action().copy(status = "SUCCEEDED", verification = "VERIFIED_LOCAL", finishedAt = 20,
            receipt = "{\"synthetic\":true}", entityType = "TASK", entityId = task.metadata.id)
        val event = event().copy(entityType = "TASK", entityId = task.metadata.id, actionId = action.metadata.id,
            requestId = action.requestId, payloadMetadata = "{\"private\":\"synthetic\"}")
        val audit = audit(action)
        repository.outcomes.record(action, event, audit)
        repository.saveMemory(SavedMemory("Synthetic source-only task fact", sourceEventId = event.metadata.id))
        stores.recommendation.save(SavedRecommendation(10, 20, 0.5f, "{}", "Synthetic reason", "{}", "SHOWN",
            entityType = "TASK", entityId = task.metadata.id))
        val insight = SavedDerivedInsight("TEST_STATS", 30, 10, 20, 2, "TEST_V1", 0.5f, sourceWatermark = 1,
            statisticsJson = "{\"synthetic\":true}", entityType = "TASK", entityId = task.metadata.id)
        // Isolated fixture only: inject an old derived row to exercise cleanup; production analysis stays blocked.
        fixture.database.derivedInsight().insert(DerivedInsightCodec(RecordCodec(fixture.cipher)).encode(insight,
            StoredMetadata(insight.metadata.id, 30, 30, 0)))
        repository.deleteTask(task.metadata.id, 0)
        assertTrue(repository.observeMemories().first().isEmpty())
        assertTrue(stores.recommendation.observe().first().isEmpty())
        assertTrue(stores.derivedInsight.observe().first().isEmpty())
        val history = stores.event.observe().first().single()
        assertEquals(event.timestamp, history.timestamp)
        assertNull(history.payloadMetadata)
        assertNull(history.entityId)
        val tombstone = stores.actionRun.observe().first().single()
        assertEquals(action.idempotencyKey, tombstone.idempotencyKey)
        assertEquals("UNKNOWN", tombstone.status)
        assertEquals("PERSONAL_DATA_REMOVED", tombstone.safeErrorCode)
        assertEquals("{}", tombstone.payload)
        assertNull(tombstone.receipt)
        val scrubbedAudit = stores.actionAudit.observe().first().single()
        assertEquals("Personal data removed.", scrubbedAudit.reason)
        assertEquals("{}", scrubbedAudit.evidence)
        assertThrows(Exception::class.java) { runBlocking { stores.actionRun.save(tombstone.copy(status = "RUNNING")) } }
        assertThrows(Exception::class.java) { runBlocking {
            stores.actionRun.save(tombstone.copy(status = "SUCCEEDED", verification = "VERIFIED_LOCAL", receipt = "{}"))
        } }
    }

    @Test
    fun duplicateAuditRollsBackEntireOutcome(): Unit = runBlocking {
        val first = action()
        stores.actionRun.save(first)
        val audit = audit(first)
        stores.actionAudit.save(audit)
        val second = action()
        val event = event().copy(actionId = second.metadata.id, requestId = second.requestId)
        assertThrows(Exception::class.java) { runBlocking {
            repository.outcomes.record(second, event, audit.copy(actionRunId = second.metadata.id))
        } }
        assertEquals(listOf(first), stores.actionRun.observe().first())
        assertTrue(stores.event.observe().first().isEmpty())
        assertEquals(listOf(audit), stores.actionAudit.observe().first())
    }

    @Test
    fun encryptionFailureDuringPrivacyCleanupRollsBackDeletionAndRedaction(): Unit = runBlocking {
        val task = SavedTask("Synthetic private task")
        repository.saveTask(task)
        val block = SavedSchedule("Synthetic linked block", 10, 20, "UTC", "Synthetic reason", taskId = task.metadata.id)
        repository.saveSchedule(block)
        val action = action().copy(entityType = "TASK", entityId = task.metadata.id)
        val event = event().copy(entityType = "TASK", entityId = task.metadata.id, actionId = action.metadata.id,
            requestId = action.requestId, payloadMetadata = "{\"private\":\"synthetic\"}")
        val audit = audit(action)
        repository.outcomes.record(action, event, audit)
        val memory = SavedMemory("Synthetic private memory", entityType = MemoryEntityType.TASK,
            entityId = task.metadata.id, sourceEventId = event.metadata.id)
        repository.saveMemory(memory)
        val failing = LocalPersonalRepository(fixture.database, object : FieldCipher {
            override fun encrypt(value: String, binding: String): ByteArray {
                check(!binding.startsWith("action_audit/") || !binding.endsWith("/evidence"))
                return fixture.cipher.encrypt(value, binding)
            }
            override fun decrypt(value: ByteArray, binding: String) = fixture.cipher.decrypt(value, binding)
        })
        assertThrows(Exception::class.java) { runBlocking { failing.deleteTask(task.metadata.id, 0) } }
        assertEquals(listOf(task), repository.observeTasks().first())
        assertEquals(listOf(block), repository.observeSchedules().first())
        assertEquals(listOf(memory), repository.observeMemories().first())
        assertEquals(listOf(action), stores.actionRun.observe().first())
        assertEquals(listOf(event), stores.event.observe().first())
        assertEquals(listOf(audit), stores.actionAudit.observe().first())
    }

    @Test
    fun identitiesUniquenessAndTerminalOutcomesCannotBeRewritten(): Unit = runBlocking {
        val action = action()
        stores.actionRun.save(action)
        assertThrows(Exception::class.java) { runBlocking {
            stores.actionRun.save(action.copy(metadata = RecordMetadata()))
        } }
        assertThrows(Exception::class.java) { runBlocking {
            stores.actionRun.save(action.copy(idempotencyKey = UUID.randomUUID().toString()))
        } }
        assertThrows(Exception::class.java) { runBlocking {
            stores.actionRun.save(action.copy(payload = "{\"changed\":true}"))
        } }
        stores.actionRun.save(action.copy(status = "SUCCEEDED", verification = "VERIFIED_LOCAL", finishedAt = 20,
            receipt = "{}"))
        val terminal = stores.actionRun.observe().first().single()
        assertThrows(Exception::class.java) { runBlocking {
            stores.actionRun.save(terminal.copy(status = "PENDING", verification = "UNVERIFIED", finishedAt = null,
                receipt = null))
        } }
        assertEquals(terminal, stores.actionRun.observe().first().single())
        val reminder = reminder()
        stores.reminder.save(reminder)
        assertThrows(Exception::class.java) { runBlocking {
            stores.reminder.save(reminder.copy(metadata = RecordMetadata()))
        } }
    }

    @Test
    fun behavioralWritesAndInferredConsentStayUnavailable(): Unit = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            SavedUserProfile("Synthetic name", "UTC", "en-IN", 0, true, true)
        }
        val habit = SavedHabit("Synthetic habit", "{}", 2, 0.5f, 10, 20, "TEST_V1", true)
        val insight = SavedDerivedInsight("TEST_STATS", 30, 10, 20, 2, "TEST_V1", 0.5f, sourceWatermark = 1)
        assertThrows(Exception::class.java) { runBlocking { stores.habit.save(habit) } }
        assertThrows(Exception::class.java) { runBlocking { stores.derivedInsight.save(insight) } }
        assertEquals(0, fixture.database.records().recordCount())
        assertNull(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(fixture.alias, null))
    }

    @Test
    fun malformedOrOverdeepJsonDoesNotLeavePartialRecords(): Unit = runBlocking {
        val routine = SavedRoutine("Synthetic routine", "FREQ=DAILY", "UTC", "{} trailing", false)
        assertThrows(Exception::class.java) { runBlocking { stores.routine.save(routine) } }
        assertEquals(0, fixture.database.records().recordCount())
        val deep = "{\"nested\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}"
        assertThrows(Exception::class.java) { runBlocking { stores.routine.save(routine.copy(definition = deep)) } }
        assertEquals(0, fixture.database.records().recordCount())
        stores.routine.save(routine.copy(definition = "{}"))
        val sql = fixture.database.openHelper.writableDatabase
        sql.execSQL("UPDATE routines SET definition = ?", arrayOf(fixture.cipher.encrypt("not an object",
            "routines/${routine.metadata.id}/definition")))
        assertThrows(Exception::class.java) { runBlocking { stores.routine.observe().first() } }
    }

    @Test
    fun sqlChecksAndSingletonProfileRejectInvalidMetadata(): Unit = runBlocking {
        val profile = SavedUserProfile("Synthetic name", "UTC", "en-IN", 0, false, true)
        stores.userProfile.save(profile)
        assertThrows(Exception::class.java) { runBlocking { stores.userProfile.save(profile.copy(metadata = RecordMetadata())) } }
        val sql = fixture.database.openHelper.writableDatabase
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE user_profile SET behavioral_analysis_consent = 1") }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE user_profile SET autonomy_level = 5") }
        val person = SavedPerson("Synthetic person")
        stores.person.save(person)
        val relation = SavedRelationship(person.metadata.id, "FRIEND")
        stores.relationship.save(relation)
        assertThrows(Exception::class.java) { runBlocking { stores.relationship.save(relation.copy(metadata = RecordMetadata())) } }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE persons SET revision = -1") }
        assertEquals(profile, stores.userProfile.observe().first().single())
    }

    private fun action() = SavedActionRun(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        UUID.randomUUID().toString(), "TASK", "{}", "LOW", "PENDING", 10, "UNVERIFIED")
    private fun event() = SavedEvent("TASK_CREATED", "USER", 10, 0.5f, 1)
    private fun audit(action: SavedActionRun) = SavedActionAudit(action.metadata.id, 10, "POLICY_V1", 0,
        "{}", "ALLOW", "Synthetic private reason", "{\"private\":\"synthetic\"}")
    private fun reminder() = SavedReminder("Synthetic reminder", 10, "1970-01-01T00:00:00.010", "UTC",
        "APPROXIMATE", "PENDING", UUID.randomUUID().toString())
}
