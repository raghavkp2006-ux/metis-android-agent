package dev.metis.agent.domain.agent

import java.time.Instant
import java.util.UUID

/**
 * Minimal in-memory, session-scoped pending clarification state.
 * Not persisted. Expires after ~2 turns or ~2 minutes (120 seconds).
 */
data class PendingClarificationState(
    val originalIntent: AgentIntent,
    val filledSlots: Map<String, String>,
    val missingSlot: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val turnsRemaining: Int = 2,
    val originalRequestId: UUID? = null,
    val questionId: UUID = UUID.randomUUID(),
    val sessionId: UUID? = null,
    val prompt: String = "",
) {
    fun isExpired(now: Instant, requestSessionId: UUID? = null): Boolean =
        turnsRemaining <= 0 || now >= expiresAt ||
            (sessionId != null && requestSessionId != null && sessionId != requestSessionId)

    fun decrementTurn(): PendingClarificationState = copy(turnsRemaining = turnsRemaining - 1)
}
