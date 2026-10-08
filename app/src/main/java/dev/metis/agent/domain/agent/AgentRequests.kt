package dev.metis.agent.domain.agent

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class AgentRequest(
    val id: UUID,
    val text: String,
    val source: InputSource,
    val timestamp: Instant,
    val screenContext: ScreenContext? = null,
    val conversationId: UUID? = null,
) {
    init { requireText(text) }
}

enum class AgentIntent {
    CALL_PERSON, SEND_MESSAGE, CREATE_TASK, COMPLETE_TASK, DELETE_TASK, POSTPONE_TASK, CHECK_TASKS,
    CREATE_REMINDER, CREATE_ALARM, CREATE_TIMER, CREATE_CALENDAR_EVENT, CHECK_CALENDAR, CHECK_MEMORY,
    CHECK_PERSON, WHAT_DID_I_PROMISE, CREATE_GOAL, CHECK_GOALS, CHECK_PROGRESS, PLAN_DAY, PLAN_WEEK,
    WHAT_SHOULD_I_DO, DAILY_REVIEW, WEEKLY_REVIEW, WHAT_DID_I_MISS, CATCH_UP, START_FOCUS, STOP_FOCUS,
    START_NAVIGATION, CHECK_HABITS, SET_PREFERENCE, START_REFLECTION, EMOTIONAL_SUPPORT, UNKNOWN, UNSUPPORTED,
}

enum class EntityKind { PERSON, TIME, DURATION, LOCATION, TASK, MEMORY, GOAL, TITLE }
sealed interface NormalizedValue {
    data class Time(val value: ResolvedTime) : NormalizedValue
    data class Duration(val seconds: Long) : NormalizedValue { init { require(seconds > 0) } }
    data class Reference(val value: EntityReference) : NormalizedValue
    data class Text(val value: String) : NormalizedValue { init { requireText(value) } }
}

data class ExtractedEntity(
    val type: EntityKind,
    val rawValue: String,
    val startOffset: Int,
    val endOffset: Int,
    val confidence: Double,
    val normalizedValue: NormalizedValue? = null,
) {
    init {
        requireText(rawValue)
        require(startOffset >= 0 && endOffset > startOffset)
        require(endOffset - startOffset == rawValue.length)
        requireConfidence(confidence)
    }
}

data class IntentPrediction(val intent: AgentIntent, val confidence: Double, val negated: Boolean = false) {
    init { requireConfidence(confidence) }
}

/** Availability is a current implementation/access fact, never derived from classifier confidence. */
class CapabilitySnapshot(val capturedAt: Instant, available: Set<Capability>) {
    val available = frozenSet(available)
}

// Matches the protocol envelope; collections are frozen rather than exposing mutable data-class copies.
@Suppress("LongParameterList")
class ResolvedContext(
    val now: Instant,
    val zoneId: ZoneId,
    val autonomy: AutonomyLevel,
    val capabilities: CapabilitySnapshot,
    unresolvedFields: Set<String> = emptySet(),
    val screenContext: ScreenContext? = null,
    val conversationId: UUID? = null,
    referencedEntities: List<RevisionTarget> = emptyList(),
) {
    val unresolvedFields = frozenSet(unresolvedFields.also { it.forEach(::requireText) })
    val referencedEntities = frozenList(referencedEntities)
    init { require(capabilities.capturedAt == now) }
}

class ParsedRequest(
    val request: AgentRequest,
    val prediction: IntentPrediction,
    entities: List<ExtractedEntity>,
    val context: ResolvedContext,
) {
    val entities = frozenList(entities)
    init {
        require(context.screenContext == request.screenContext && context.conversationId == request.conversationId)
        entities.forEach {
            require(it.endOffset <= request.text.length)
            require(request.text.substring(it.startOffset, it.endOffset) == it.rawValue)
            require(!splitsSurrogate(request.text, it.startOffset) && !splitsSurrogate(request.text, it.endOffset))
        }
    }
}

private fun splitsSurrogate(text: String, offset: Int): Boolean = offset in 1 until text.length &&
    text[offset - 1].isHighSurrogate() && text[offset].isLowSurrogate()
