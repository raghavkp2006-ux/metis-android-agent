package dev.metis.agent

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.FieldCipher
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.data.storage.LocalDecisionStore
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordCodec
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.decision.DecisionAgent
import dev.metis.agent.domain.decision.DecisionEngine
import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedPreference
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedUserProfile
import dev.metis.agent.domain.storage.TaskStatus
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
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
class DecisionStoreTest {
    private val at = Instant.parse("2026-10-09T10:00:00Z")

    @Test fun explicitQueryStoresEncryptedComponentsAndCorrelatedEventWithoutActionOrTaskMutation(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val task = SavedTask("Synthetic decision private title", notes = "Synthetic private notes",
                dueAt = at.minusSeconds(10).toEpochMilli(), dueZoneId = "UTC", priority = 3, estimatedSeconds = 600)
            f.repository.saveTask(task)
            f.repository.foundation.userProfile.save(SavedUserProfile("Synthetic answer only", "UTC", "en", 0,
                behavioralAnalysisConsent = false, active = true))
            val before = f.repository.observeTasks().first()
            val requestId = UUID.randomUUID()
            val result = store(f).recommend(requestId, 1_800)
            assertEquals(AgentResultStatus.ANSWER, result.status)
            assertTrue(result.message.contains(task.title))
            assertTrue(result.message.contains("Calendar availability, behavioral success"))
            assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
            assertEquals(before, f.repository.observeTasks().first())
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
            assertTrue(f.repository.foundation.reminder.observe().first().isEmpty())
            val record = f.repository.foundation.recommendation.observe().first().single()
            val scores = JSONObject(record.scoreComponentsJson)
            assertEquals(DecisionEngine.VERSION, scores.getString("version"))
            val total = scores.getDouble("deadline") + scores.getDouble("priority") + scores.getDouble("goal") +
                scores.getDouble("overdue") + scores.getDouble("preference") - scores.getDouble("effortPenalty")
            assertEquals(record.score.toDouble(), total.coerceIn(0.0, 1.0), EPSILON)
            assertTrue(scores.isNull("calendarAvailability") && scores.isNull("behavioralSuccess"))
            assertEquals(0L, JSONObject(record.evidence).getLong("taskRevision"))
            val event = f.repository.foundation.event.observe().first().single()
            assertEquals("RECOMMENDATION_SHOWN", event.type)
            assertEquals(requestId.toString(), event.requestId)
            assertEquals(record.metadata.id, event.entityId)
            assertNull(event.actionId)
            val row = requireNotNull(f.database.recommendation().find(record.metadata.id))
            assertFalse(String(row.reason).contains(task.title))
            assertFalse(String(row.scoreComponentsJson).contains("deadline"))
            assertFalse(record.evidence.contains("Synthetic private notes"))
        }
    }

    @Test fun concurrentReplayAndReplayAfterReopenCannotGenerateDuplicateOrStaleSuggestions(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic replay decision"))
            val id = UUID.randomUUID()
            val s = store(f)
            listOf(async { s.recommend(id, null) }, async { s.recommend(id, null) }).forEach { it.await() }
            assertEquals(1, f.repository.foundation.recommendation.observe().first().size)
            assertEquals(1, f.repository.foundation.event.observe().first().size)
            f.database.close()
            val reopened = Room.databaseBuilder(f.context, PersonalDatabase::class.java, f.name)
                .addCallback(RecordConstraints).build()
            try {
                val repository = LocalPersonalRepository(reopened, f.cipher)
                val later = LocalDecisionStore(repository, RecordCodec(f.cipher),
                    Clock.fixed(at.plusSeconds(301), ZoneId.of("UTC")))
                val result = later.recommend(id, null)
                assertTrue(result.message.contains("Ask again for a fresh recommendation"))
                assertEquals(1, repository.foundation.recommendation.observe().first().size)
                assertTrue(repository.foundation.recommendation.observe().first().single().expiresAt <
                    at.plusSeconds(301).toEpochMilli())
            } finally { reopened.close() }
        }
    }

    @Test fun eventFailureRollsBackRecommendationAndRetryCommitsOneOutcome(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic rollback recommendation"))
            val repository = LocalPersonalRepository(f.database, DecisionEventFailureCipher(f.cipher))
            val id = UUID.randomUUID()
            val failing = LocalDecisionStore(repository, RecordCodec(f.cipher), Clock.fixed(at, ZoneId.of("UTC")))
            assertThrows(IllegalStateException::class.java) { runBlocking { failing.recommend(id, null) } }
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            assertTrue(f.repository.foundation.event.observe().first().isEmpty())
            assertEquals(TaskStatus.OPEN, f.repository.observeTasks().first().single().status)
            assertEquals(AgentResultStatus.ANSWER, store(f).recommend(id, null).status)
            assertEquals(1, f.repository.foundation.recommendation.observe().first().size)
        }
    }

    @Test fun emptyAndBudgetIneligibleResultsDoNotFabricateRecommendations(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            assertTrue(store(f).recommend(UUID.randomUUID(), null).message.contains("No eligible saved task"))
            f.repository.saveTask(SavedTask("Synthetic unknown effort", priority = 3))
            f.repository.saveTask(SavedTask("Synthetic too long", estimatedSeconds = 3_600, priority = 3))
            assertTrue(store(f).recommend(UUID.randomUUID(), 1_800).message.contains("No eligible saved task"))
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            assertTrue(f.repository.foundation.event.observe().first().isEmpty())
        }
    }

    @Test fun deletingScoredSourcesAndTaskRemovesRecommendationsAndRedactsDerivedTimeline(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val goal = SavedGoal("Synthetic scored goal", priority = 3)
            val preference = SavedPreference(PreferenceKey.FOCUS_BLOCK_MINUTES, PreferenceValue.FocusMinutes(30))
            f.repository.planning.saveGoal(goal)
            f.repository.preferences.savePreference(preference)
            f.repository.saveTask(SavedTask("Synthetic recommendation source", goalId = goal.metadata.id,
                estimatedSeconds = 600))
            val first = UUID.randomUUID()
            store(f).recommend(first, null)
            f.repository.preferences.deletePreference(preference.metadata.id, 0)
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            assertNull(f.repository.foundation.event.observe().first().single().payloadMetadata)
            store(f).recommend(UUID.randomUUID(), null)
            f.repository.planning.deleteGoal(goal.metadata.id, 0)
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            store(f).recommend(UUID.randomUUID(), null)
            val task = f.repository.observeTasks().first().single()
            f.repository.deleteTask(task.metadata.id, task.metadata.revision)
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            assertTrue(f.repository.foundation.event.observe().first().all {
                it.payloadMetadata == null && it.entityId == null
            })
            store(f).recommend(first, null)
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
        }
    }

    @Test fun recommendationRequestInvalidatesOutstandingMutationAndNegationMakesNoStorageWrites(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val base = ConfirmedTaskAgent(LocalAgentReads(f.repository), f.repository.taskActions)
            val agent = DecisionAgent(base, store(f))
            val proposal = agent.process(request("add a task to Synthetic unaccepted decision task")).proposals.single()
            agent.process(request("what should I do?"))
            assertEquals(AgentResultStatus.DENIED, agent.accept(proposal).status)
            assertEquals(AgentResultStatus.DENIED, agent.process(request("don't tell me what should I do")).status)
            assertEquals(AgentResultStatus.FOLLOW_UP,
                agent.process(request("what should I do with 0 minutes?")).status)
            assertTrue(f.repository.observeTasks().first().isEmpty())
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    @Test fun deletingScoredProjectRemovesDerivedExplanationWhileKeepingTheTask(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val project = SavedProject("Synthetic recommendation project")
            val goal = SavedGoal("Synthetic project goal", projectId = project.metadata.id)
            f.repository.planning.saveProject(project)
            f.repository.planning.saveGoal(goal)
            f.repository.saveTask(SavedTask("Synthetic surviving project task", projectId = project.metadata.id,
                goalId = goal.metadata.id))
            store(f).recommend(UUID.randomUUID(), null)
            assertEquals(1, f.repository.foundation.recommendation.observe().first().size)
            f.repository.planning.deleteProject(project.metadata.id, 0)
            assertEquals(1, f.repository.observeTasks().first().size)
            assertNull(f.repository.observeTasks().first().single().projectId)
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            assertNull(f.repository.foundation.event.observe().first().single().payloadMetadata)
        }
    }

    @Test fun generatedSuggestionsDoNotBlockGuardedCreationUndoOrConfirmedTaskDeletion(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val agent = ConfirmedTaskAgent(LocalAgentReads(f.repository), f.repository.taskActions)
            val creation = agent.process(request("add a task to Synthetic undo after advice")).proposals.single()
            val receipt = agent.accept(creation).completedActions.single()
            store(f).recommend(UUID.randomUUID(), null)
            assertEquals(UndoResult.UNDONE, agent.undo(receipt))
            assertTrue(f.repository.observeTasks().first().isEmpty())
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            val second = agent.process(request("add a task to Synthetic delete after advice")).proposals.single()
            agent.accept(second)
            store(f).recommend(UUID.randomUUID(), null)
            val deletion = agent.process(request("delete task: Synthetic delete after advice")).proposals.single()
            assertEquals(AgentResultStatus.ANSWER, agent.accept(deletion).status)
            assertTrue(f.repository.observeTasks().first().isEmpty())
            assertTrue(f.repository.foundation.recommendation.observe().first().isEmpty())
            assertTrue(f.repository.foundation.event.observe().first().filter {
                it.type == "RECOMMENDATION_SHOWN"
            }.all { it.payloadMetadata == null })
        }
    }

    @Test fun missingKeyFailsVisibleQueryWithoutRecreationOrFalseEmptyAnswer(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic missing decision key"))
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(f.alias) }
            val agent = DecisionAgent(ConfirmedTaskAgent(LocalAgentReads(f.repository), f.repository.taskActions), store(f))
            assertEquals(AgentResultStatus.FAILED, agent.process(request("what should I do?")).status)
            assertFalse(keys.containsAlias(f.alias))
            assertEquals(0, f.database.recommendation().observe().first().size)
            assertEquals(0, f.database.event().observe().first().size)
        }
    }

    private fun store(f: StorageTestFixture) = LocalDecisionStore(f.repository, RecordCodec(f.cipher),
        Clock.fixed(at, ZoneId.of("UTC")))
    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, at)
    private companion object { const val EPSILON = 0.000001 }
}

private class DecisionEventFailureCipher(private val delegate: FieldCipher) : FieldCipher {
    private var fail = true
    override fun encrypt(value: String, binding: String): ByteArray {
        if (fail && binding.startsWith("events/") && binding.endsWith("/metadata")) {
            fail = false
            error("Synthetic decision event write failure")
        }
        return delegate.encrypt(value, binding)
    }
    override fun decrypt(value: ByteArray, binding: String) = delegate.decrypt(value, binding)
}
