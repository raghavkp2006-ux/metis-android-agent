package dev.metis.agent.domain.agent

import java.time.ZoneId

/** Closed read resources: UNKNOWN and action intents have no representation here. */
enum class ReadResourceV17(val intent: AgentIntent) {
    TASKS(AgentIntent.CHECK_TASKS),
    CALENDAR(AgentIntent.CHECK_CALENDAR),
    MEMORY(AgentIntent.CHECK_MEMORY),
    PROMISES(AgentIntent.WHAT_DID_I_PROMISE),
}

/**
 * Data foundation for a future validator result; construction does not authorize a read.
 * No parsing, semantic validation, entity resolution or repository access happens here.
 *
 * The original request retains exact text, source, timestamp and conversation/screen context.
 * The zone fixes the interpretation of symbolic dates relative to that request's timestamp.
 * Reusing the sealed scope preserves all existing subject/person, status, date-field,
 * requested-field, content-negation and calendar kind/day/period constraints without conversion.
 * Entity annotations retain original spans and optional typed references; they are not resolved here.
 */
class ValidatedReadPlanV17(
    val request: AgentRequest,
    val zoneId: ZoneId,
    val scope: ReadScopeV15,
    entities: List<ExtractedEntity> = emptyList(),
) {
    val entities: List<ExtractedEntity> = frozenList(entities)
    val resource: ReadResourceV17 = when (scope) {
        is ReadScopeV15.Tasks -> ReadResourceV17.TASKS
        is ReadScopeV15.Calendar -> ReadResourceV17.CALENDAR
        is ReadScopeV15.Memories -> ReadResourceV17.MEMORY
        is ReadScopeV15.Promises -> ReadResourceV17.PROMISES
    }
    val intent: AgentIntent get() = resource.intent
}
