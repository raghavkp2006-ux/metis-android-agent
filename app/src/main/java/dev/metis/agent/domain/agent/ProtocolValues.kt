package dev.metis.agent.domain.agent

import dev.metis.agent.domain.storage.MemoryEntityType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Collections
import java.util.UUID

const val PROTOCOL_VERSION = 1
internal const val TEXT_LIMIT = 4_000
internal const val COLLECTION_LIMIT = 64
internal const val PROPOSAL_LIFETIME_SECONDS = 300L

enum class InputSource { TEXT, VOICE, QUICK_ACTION, WIDGET, PROACTIVE }
enum class RiskLevel { R0, R1, R2, R3 }
enum class AgentDestination { TODAY, PLAN, AGENT, TIMELINE, YOU }
enum class AutonomyLevel { ANSWER_ONLY, SUGGEST, SAFE_LOCAL, ROUTINES, DAILY_MANAGEMENT }
enum class ActionType {
    TASK, MEMORY, GOAL, PREFERENCE, CREATE_REMINDER, CANCEL_REMINDER, ALARM, TIMER,
    CALENDAR, CALL, MESSAGE, NAVIGATION, FOCUS, ACCEPT_PLAN,
}
enum class Capability {
    LOCAL_READ, LOCAL_WRITE, REMINDER_SCHEDULE, EXACT_ALARM, NOTIFICATIONS, TIMER,
    CALENDAR_READ, CALENDAR_WRITE, CALENDAR_EDITOR, DIALER, MESSAGE_COMPOSER, MAPS, FOCUS, PLAN_ACCEPTANCE,
}

data class EntityReference(val type: MemoryEntityType, val id: UUID)
data class RevisionTarget(val reference: EntityReference, val expectedRevision: Long) {
    init { require(expectedRevision >= 0) }
}

/** Both local representation and selected offset must agree, including DST overlaps and gaps. */
data class ResolvedTime(val instant: Instant, val local: LocalDateTime, val zone: ZoneId) {
    init { require(zone.rules.getValidOffsets(local).any { local.toInstant(it) == instant }) }
}

data class ScreenContext(val destination: AgentDestination, val entity: EntityReference? = null)
data class Evidence(
    val id: UUID,
    val source: EvidenceSource,
    val observedAt: Instant,
    val summary: String,
    val entity: EntityReference? = null,
) {
    init { requireText(summary) }
}
enum class EvidenceSource { USER_INPUT, LOCAL_RECORD, PLATFORM }

/** Collections are copied and frozen at domain boundaries; caller mutation cannot alter a proposal. */
internal fun <T> frozenList(values: Collection<T>): List<T> {
    require(values.size <= COLLECTION_LIMIT)
    return Collections.unmodifiableList(ArrayList(values))
}
internal fun <T> frozenSet(values: Collection<T>): Set<T> {
    require(values.size <= COLLECTION_LIMIT)
    return Collections.unmodifiableSet(LinkedHashSet(values))
}
internal fun requireText(value: String) { require(value.isNotBlank() && value.length <= TEXT_LIMIT) }
internal fun requireConfidence(value: Double) { require(value.isFinite() && value in 0.0..1.0) }
internal fun requireTarget(target: RevisionTarget, type: MemoryEntityType) {
    require(target.reference.type == type)
}
