package dev.metis.agent

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.storage.FoundationRecord
import dev.metis.agent.domain.storage.FoundationRepository
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedRelationship
import dev.metis.agent.domain.storage.SavedUserProfile
import dev.metis.agent.domain.storage.SavedReminder
import dev.metis.agent.domain.storage.SavedFocusSession
import dev.metis.agent.domain.storage.SavedEvent
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedRoutine
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedAgentSession
import dev.metis.agent.domain.storage.SavedRecommendation
import dev.metis.agent.domain.storage.SavedExperiment
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationRoundTripTest {
    private val fixture = StorageTestFixture()
    @After fun cleanup() = fixture.close()

    @Test
    fun personRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedPerson(
            displayName = "Synthetic secret Person displayName",
            phone = "Synthetic secret Person phone",
            email = "Synthetic secret Person email",
            contactLookupKey = "Synthetic secret Person contactLookupKey",
        )
        val edited = original.copy(displayName = "Synthetic secret changed")
        verify("persons", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.person })
    }

    @Test
    fun relationshipRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {
        val person = SavedPerson("Synthetic prerequisite person")
        fixture.repository.foundation.person.save(person)
        val original = SavedRelationship(
            personId = person.metadata.id,
            kind = "FRIEND",
            description = "Synthetic secret Relationship description",
        )
        val edited = original.copy(description = "Synthetic secret changed")
        verify("relationships", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.relationship })
    }

    @Test
    fun userProfileRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedUserProfile(
            displayName = "Synthetic secret UserProfile displayName",
            zoneId = "UTC",
            locale = "en-IN",
            autonomyLevel = 0,
            behavioralAnalysisConsent = false,
            active = true,
        )
        val edited = original.copy(displayName = "Synthetic secret changed")
        verify("user_profile", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.userProfile })
    }

    @Test
    fun reminderRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedReminder(
            title = "Synthetic secret Reminder title",
            triggerAt = 10L,
            localDateTime = "1970-01-01T00:00:00.010",
            zoneId = "UTC",
            precision = "APPROXIMATE",
            schedulingState = "PENDING",
            idempotencyKey = UUID.randomUUID().toString(),
            taskId = null,
            personId = null,
            platformToken = null,
            deliveredAt = null,
        )
        val edited = original.copy(title = "Synthetic secret changed")
        verify("reminders", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.reminder })
    }

    @Test
    fun focusSessionRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedFocusSession(
            startedAt = 10L,
            plannedSeconds = 60L,
            outcome = "RUNNING",
            taskId = null,
            endedAt = null,
        )
        val edited = original.copy(plannedSeconds = 120)
        verify("focus_sessions", original, edited, { edited.copy(metadata = it) },
            false, { it.foundation.focusSession })
    }

    @Test
    fun eventRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedEvent(
            type = "TASK_CREATED",
            source = "USER",
            timestamp = 10L,
            importance = 0.5f,
            schemaVersion = 1,
            entityType = null,
            entityId = null,
            payloadMetadata = """{"synthetic":"Synthetic secret Event payloadMetadata"}""",
            requestId = null,
            actionId = null,
        )
        verify("events", original, null, null,
            true, { it.foundation.event })
    }

    @Test
    fun promiseRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {
        val person = SavedPerson("Synthetic prerequisite person")
        fixture.repository.foundation.person.save(person)
        val original = SavedPromise(
            personId = person.metadata.id,
            content = "Synthetic secret Promise content",
            status = "OPEN",
            taskId = null,
            dueAt = null,
            sourceEventId = null,
        )
        val edited = original.copy(content = "Synthetic secret changed")
        verify("promises", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.promise })
    }

    @Test
    fun routineRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedRoutine(
            name = "Synthetic secret Routine name",
            recurrenceRule = "FREQ=DAILY",
            zoneId = "UTC",
            definition = """{"synthetic":"Synthetic secret Routine definition"}""",
            enabled = false,
        )
        val edited = original.copy(name = "Synthetic secret changed")
        verify("routines", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.routine })
    }

    @Test
    fun actionRunRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedActionRun(
            requestId = UUID.randomUUID().toString(),
            proposalId = UUID.randomUUID().toString(),
            idempotencyKey = UUID.randomUUID().toString(),
            actionType = "TASK",
            payload = """{"synthetic":"Synthetic secret ActionRun payload"}""",
            risk = "LOW",
            status = "PENDING",
            startedAt = 10L,
            verification = "UNVERIFIED",
            finishedAt = null,
            receipt = """{"synthetic":"Synthetic secret ActionRun receipt"}""",
            safeErrorCode = null,
            entityType = null,
            entityId = null,
        )
        val edited = original.copy(status = "RUNNING")
        verify("action_runs", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.actionRun })
    }

    @Test
    fun actionAuditRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {
        val action = SavedActionRun(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "TASK", "{}", "LOW", "PENDING", 10, "UNVERIFIED")
        fixture.repository.foundation.actionRun.save(action)
        val original = SavedActionAudit(
            actionRunId = action.metadata.id,
            timestamp = 10L,
            policyVersion = "TEST_V1",
            autonomyLevel = 0,
            permissionSnapshotJson = """{"synthetic":"Synthetic secret ActionAudit permissionSnapshotJson"}""",
            decision = "ALLOW",
            reason = "Synthetic secret ActionAudit reason",
            evidence = """{"synthetic":"Synthetic secret ActionAudit evidence"}""",
            confirmationId = null,
        )
        verify("action_audit", original, null, null,
            true, { it.foundation.actionAudit })
    }

    @Test
    fun agentSessionRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedAgentSession(
            startedAt = 10L,
            endedAt = null,
            summary = "Synthetic secret AgentSession summary",
        )
        verify("agent_sessions", original, null, null,
            true, { it.foundation.agentSession })
    }

    @Test
    fun recommendationRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedRecommendation(
            generatedAt = 10L,
            expiresAt = 20L,
            score = 0.5f,
            scoreComponentsJson = """{"synthetic":"Synthetic secret Recommendation scoreComponentsJson"}""",
            reason = "Synthetic secret Recommendation reason",
            evidence = """{"synthetic":"Synthetic secret Recommendation evidence"}""",
            status = "SHOWN",
            entityType = null,
            entityId = null,
        )
        val edited = original.copy(scoreComponentsJson = """{"synthetic":"Synthetic secret changed"}""")
        verify("recommendations", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.recommendation })
    }

    @Test
    fun experimentRoundTripRevisionsAndKeyLoss(): Unit = runBlocking {

        val original = SavedExperiment(
            name = "Synthetic secret Experiment name",
            hypothesis = "Synthetic secret Experiment hypothesis",
            startedAt = 10L,
            consentedAt = 5L,
            definition = """{"synthetic":"Synthetic secret Experiment definition"}""",
            status = "ACTIVE",
            endedAt = null,
        )
        val edited = original.copy(name = "Synthetic secret changed")
        verify("experiments", original, edited, { edited.copy(metadata = it) },
            true, { it.foundation.experiment })
    }

    private suspend fun <T : FoundationRecord> verify(
        table: String, original: T, edited: T?, expected: ((RecordMetadata) -> T)?, encrypted: Boolean,
        factory: (LocalPersonalRepository) -> FoundationRepository<T>,
    ) {
        val repository = factory(fixture.repository)
        repository.save(original)
        assertEquals(original, repository.observe().first().single())
        if (edited != null) {
            repository.save(edited)
            val current = repository.observe().first().single()
            assertEquals(1L, current.metadata.revision)
            assertEquals(requireNotNull(expected)(current.metadata), current)
            assertThrows(RevisionConflictException::class.java) { runBlocking { repository.save(original) } }
        } else {
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.save(original) } }
        }
        val saved = repository.observe().first().single()
        fixture.database.close()
        val disk = String(fixture.context.getDatabasePath(fixture.name).readBytes(), Charsets.ISO_8859_1)
        assertFalse("$table contains plaintext", disk.contains("Synthetic secret"))
        val reopened = Room.databaseBuilder(fixture.context, PersonalDatabase::class.java, fixture.name)
            .addCallback(RecordConstraints).build()
        try {
            val stored = factory(LocalPersonalRepository(reopened, fixture.cipher))
            assertEquals(saved, stored.observe().first().single())
            assertThrows(RevisionConflictException::class.java) { runBlocking {
                stored.delete(saved.metadata.id, saved.metadata.revision + 1)
            } }
            stored.delete(saved.metadata.id, saved.metadata.revision)
            assertTrue(stored.observe().first().isEmpty())
            stored.save(original)
            if (encrypted) {
                val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(fixture.alias) }
                assertThrows(Exception::class.java) { runBlocking { stored.observe().first() } }
                assertThrows(Exception::class.java) { runBlocking {
                    LocalPersonalRepository(reopened, fixture.cipher).saveTask(SavedTask("Synthetic blocked task"))
                } }
                assertNull(keys.getKey(fixture.alias, null))
                assertTrue(reopened.records().recordCount() > 0)
            }
        } finally { reopened.close() }
    }
}
