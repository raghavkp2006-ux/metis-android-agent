package dev.metis.agent.data.storage

import dev.metis.agent.domain.decision.DecisionContext
import dev.metis.agent.domain.decision.DecisionEngine
import dev.metis.agent.domain.decision.TaskDecision
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedEvent
import dev.metis.agent.domain.storage.SavedPreference
import dev.metis.agent.domain.storage.SavedRecommendation
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal object DecisionEncoding {
    fun record(requestId: UUID, decision: TaskDecision, context: DecisionContext, candidates: Int,
        preferences: List<SavedPreference>): SavedRecommendation {
        val score = decision.components
        val components = JSONObject().put("version", DecisionEngine.VERSION).put("deadline", score.deadline)
            .put("priority", score.priority).put("goal", score.goal).put("overdue", score.overdue)
            .put("preference", score.preference).put("effortPenalty", score.effortPenalty).put("total", score.total)
            .put("behavioralSuccess", JSONObject.NULL).put("calendarAvailability", JSONObject.NULL)
            .put("contextSwitch", JSONObject.NULL)
        val sources = JSONArray()
        sources.put(JSONObject().put("type", "TASK").put("id", decision.task.metadata.id))
        decision.goal?.let { sources.put(JSONObject().put("type", "GOAL").put("id", it.metadata.id)) }
        setOfNotNull(decision.task.projectId, decision.goal?.projectId).forEach {
            sources.put(JSONObject().put("type", "PROJECT").put("id", it))
        }
        preferences.filter { it.value is PreferenceValue.FocusMinutes }.forEach {
            sources.put(JSONObject().put("type", "PREFERENCE").put("id", it.metadata.id))
        }
        val evidence = JSONObject().put("version", DecisionEngine.VERSION).put("requestId", requestId)
            .put("taskId", decision.task.metadata.id).put("taskRevision", decision.task.metadata.revision)
            .put("eligibleCandidates", candidates)
            .put("availableSeconds", context.availableSeconds ?: JSONObject.NULL)
            .put("focusMinutes", context.focusMinutes ?: JSONObject.NULL).put("sources", sources)
        return SavedRecommendation(context.now, Math.addExact(context.now, LIFETIME_MILLIS), score.total.toFloat(),
            components.toString(), reason(decision, context, candidates), evidence.toString(), "SHOWN",
            metadata = RecordMetadata(id = LocalDecisionStore.recommendationId(requestId), createdAt = context.now))
    }

    fun event(requestId: UUID, record: SavedRecommendation) = SavedEvent("RECOMMENDATION_SHOWN", "AGENT",
        record.generatedAt, NORMAL_IMPORTANCE, 1, entityType = "RECOMMENDATION", entityId = record.metadata.id,
        payloadMetadata = JSONObject().put("version", 1).put("summary",
            "Task suggestion generated for an explicit request; no task was started, changed or scheduled.")
            .put("status", "SHOWN").put("verification", "VERIFIED_LOCAL").toString(),
        requestId = requestId.toString(), metadata = RecordMetadata(id = LocalDecisionStore.eventId(requestId),
            createdAt = record.generatedAt))

    private fun reason(decision: TaskDecision, context: DecisionContext, candidates: Int): String {
        val task = decision.task
        val score = decision.components
        val due = task.dueAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.of(requireNotNull(task.dueZoneId))).toString()
        }
            ?: "no saved deadline"
        val estimate = task.estimatedSeconds?.let { "$it seconds" } ?: "unknown (fit is unverified)"
        return "Suggested task: ${task.title.take(MAX_TITLE)}\n" +
            "Why: highest score among $candidates eligible saved tasks; ties use deadline, creation time and ID.\n" +
            "Deadline: $due. Priority: ${task.priority}/3. Estimated effort: $estimate.\n" +
            "Score ${number(score.total)} = deadline ${number(score.deadline)} + " +
            "priority ${number(score.priority)} + " +
            "active goal ${number(score.goal)} + overdue ${number(score.overdue)} + focus preference " +
            "${number(score.preference)} − effort ${number(score.effortPenalty)} (clamped to 0–1).\n" +
            "Available duration: " +
            "${context.availableSeconds?.let { "$it seconds, supplied in this request" } ?: "unknown"}. " +
            "Focus preference: ${context.focusMinutes?.let { "$it minutes" } ?: "not set"}; " +
            "it does not prove free time.\n" +
            "Calendar availability, behavioral success and context switching are unknown and unused. " +
            "Snapshot at ${Instant.ofEpochMilli(context.now)}; valid for five minutes, subject to task changes. " +
            "Suggestion only. No task was started, changed or scheduled."
    }

    private fun number(value: Double) = String.format(Locale.ROOT, "%.3f", value)
    private const val MAX_TITLE = 500
    private const val LIFETIME_MILLIS = 300_000L
    private const val NORMAL_IMPORTANCE = 0.5f
}
