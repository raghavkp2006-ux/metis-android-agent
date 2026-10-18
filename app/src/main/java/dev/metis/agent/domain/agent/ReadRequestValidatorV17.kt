package dev.metis.agent.domain.agent

import java.time.ZoneId

/**
 * Contract for a future deterministic full-request validator. A proposed resource selects a
 * validation route; it does not authorize a read. No model score is part of this contract.
 * Implementations must inspect the complete original request and return a typed decision.
 */
interface ReadRequestValidatorV17 {
    fun validate(request: AgentRequest, proposedResource: ReadResourceV17, zoneId: ZoneId): ReadValidationResultV17
}

sealed interface ReadValidationResultV17 {
    val request: AgentRequest
    val proposedResource: ReadResourceV17

    /** A plan returned by a future validator; holding this result does not execute a read. */
    data class Valid(val plan: ValidatedReadPlanV17) : ReadValidationResultV17 {
        override val request: AgentRequest get() = plan.request
        override val proposedResource: ReadResourceV17 get() = plan.resource
    }

    /** The request concerns a supported read but needs information or disambiguation. */
    data class Clarify(
        override val request: AgentRequest,
        override val proposedResource: ReadResourceV17,
        val reason: ReadClarificationReasonV17,
    ) : ReadValidationResultV17

    /** The complete request cannot be admitted as a supported V17 read. */
    data class Reject(
        override val request: AgentRequest,
        override val proposedResource: ReadResourceV17,
        val reason: ReadRejectionReasonV17,
    ) : ReadValidationResultV17
}

enum class ReadClarificationReasonV17 {
    INCOMPLETE_REQUEST,
    MISSING_SUBJECT,
    AMBIGUOUS_ENTITY,
    AMBIGUOUS_SCOPE,
    AMBIGUOUS_DATE,
    UNRESOLVED_REFERENCE,
}

enum class ReadRejectionReasonV17 {
    UNSUPPORTED_OPERATION,
    UNSUPPORTED_RESOURCE,
    UNSUPPORTED_CONSTRAINT,
    UNSUPPORTED_INPUT_SOURCE,
}
