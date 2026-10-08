package dev.metis.agent.domain.agent

import java.time.Instant
import java.util.UUID

enum class AgentEventType {
    TASK_CREATED, TASK_UPDATED, TASK_COMPLETED, TASK_DELETED, TASK_MISSED, TASK_POSTPONED,
    REMINDER_CREATED, REMINDER_CANCELLED, REMINDER_TRIGGERED, REMINDER_DELIVERY_FAILED,
    CALENDAR_EVENT_CREATED, CALENDAR_EVENT_STARTED, CALENDAR_EVENT_ENDED, EXTERNAL_HANDOFF,
    GOAL_CREATED, GOAL_UPDATED, MEMORY_CREATED, MEMORY_UPDATED, MEMORY_DELETED, PREFERENCE_UPDATED,
    PLAN_ACCEPTED, FOCUS_STARTED, FOCUS_COMPLETED, RECOMMENDATION_SHOWN, RECOMMENDATION_ACCEPTED,
    RECOMMENDATION_REJECTED, ACTION_FAILED,
}
enum class EventSource { USER, AGENT, ANDROID, WORKER }

/** Typed metadata only. Encoding/encrypted persistence belongs to the event adapter in Phase 9. */
data class EventMetadata(val summary: String, val verification: OutcomeVerification) {
    init { requireText(summary) }
}
data class AgentEvent(
    val id: UUID,
    val type: AgentEventType,
    val source: EventSource,
    val timestamp: Instant,
    val details: EventDetails,
    val correlation: EventCorrelation? = null,
    val schemaVersion: Int = PROTOCOL_VERSION,
) { init { require(schemaVersion == PROTOCOL_VERSION) } }
data class EventDetails(val importance: Double, val metadata: EventMetadata, val entity: EntityReference? = null) {
    init { requireConfidence(importance) }
}
data class EventCorrelation(val requestId: UUID, val actionId: UUID? = null)
