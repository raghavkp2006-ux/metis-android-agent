package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.Explanation
import dev.metis.agent.domain.decision.DecisionContext
import dev.metis.agent.domain.decision.DecisionEngine
import dev.metis.agent.domain.decision.DecisionInputs
import dev.metis.agent.domain.decision.DecisionPort
import dev.metis.agent.domain.storage.PreferenceValue
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Snapshot, numeric explanation, recommendation and event commit together; task rows are read only. */
class LocalDecisionStore internal constructor(
    private val repository: LocalPersonalRepository, private val codec: RecordCodec,
    private val clock: Clock = Clock.systemUTC(),
) : DecisionPort {
    override suspend fun recommend(requestId: UUID, availableSeconds: Long?): AgentResult =
        withContext(Dispatchers.IO) {
        repository.database.withTransaction {
            requireReadableKey(repository.database.records(), codec)
            if (repository.database.event().find(eventId(requestId)) != null) {
                return@withTransaction AgentResult(requestId, AgentResultStatus.ANSWER,
                    "This request was already evaluated. Ask again for a fresh recommendation; " +
                        "historical suggestions are not current task status.")
            }
            if (repository.database.records().decisionTaskCount() > MAX_TASKS) {
                return@withTransaction AgentResult(requestId, AgentResultStatus.UNSUPPORTED,
                    "There are too many saved tasks for this decision baseline. No partial ranking was used.")
            }
            evaluate(requestId, availableSeconds)
        }
    }

    private suspend fun evaluate(requestId: UUID, availableSeconds: Long?): AgentResult {
        val preferences = repository.preferences.observePreferences().first()
        val focus = preferences.mapNotNull { it.value as? PreferenceValue.FocusMinutes }.singleOrNull()?.minutes
        val context = DecisionContext(clock.millis(), availableSeconds, focus)
        val inputs = DecisionInputs(repository.observeTasks().first(),
            repository.dependencies.observeDependencies().first(),
            repository.planning.observeGoals().first(), repository.planning.observeProjects().first())
        val ranked = DecisionEngine.rank(inputs, context)
        val chosen = ranked.firstOrNull() ?: return AgentResult(requestId, AgentResultStatus.ANSWER,
            "No eligible saved task was found. Completed/cancelled or recurring tasks, inactive goals/projects, " +
                "and unfinished prerequisites are excluded. With a time budget, a known estimate must fit. " +
                "Calendar availability is unknown. No task was changed.")
        val record = DecisionEncoding.record(requestId, chosen, context, ranked.size, preferences)
        repository.foundation.recommendation.save(record)
        repository.foundation.event.save(DecisionEncoding.event(requestId, record))
        return AgentResult(requestId, AgentResultStatus.ANSWER, record.reason, explanation = Explanation(record.reason))
    }

    companion object {
        private const val MAX_TASKS = 1_000
        fun recommendationId(requestId: UUID): String =
            UUID.nameUUIDFromBytes("decision-v1:$requestId".toByteArray(Charsets.UTF_8)).toString()
        fun eventId(requestId: UUID): String =
            UUID.nameUUIDFromBytes("decision-event-v1:$requestId".toByteArray(Charsets.UTF_8)).toString()
    }
}
