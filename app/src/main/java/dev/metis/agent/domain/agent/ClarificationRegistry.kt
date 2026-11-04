package dev.metis.agent.domain.agent

import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

enum class ClarificationDomain {
    REMINDER,
    ALARM,
    CALL,
    PROMISE,
}

/**
 * Minimal in-memory, session-scoped pending clarification state.
 * Ephemeral (RAM only). Expires after ~2 turns or 120 seconds.
 */
data class PendingClarificationState(
    val domain: ClarificationDomain,
    val slot: String,
    val filledSlots: Map<String, String> = emptyMap(),
    val createdAt: Instant,
    val expiresAt: Instant,
    val turnsRemaining: Int = 2,
    val originalRequestId: UUID,
    val questionId: UUID = UUID.randomUUID(),
    val sessionId: UUID? = null,
    val prompt: String = "",
    val suggestedChoices: List<String> = emptyList(),
    val payload: Any? = null,
    val lastAnswerId: UUID? = null,
) {
    // Backwards compatibility helpers
    val originalIntent: AgentIntent
        get() = when (domain) {
            ClarificationDomain.REMINDER -> AgentIntent.CREATE_REMINDER
            ClarificationDomain.ALARM -> AgentIntent.CREATE_ALARM
            ClarificationDomain.CALL -> AgentIntent.CALL_PERSON
            ClarificationDomain.PROMISE -> AgentIntent.CREATE_PROMISE
        }

    val missingSlot: String get() = slot

    fun isExpired(now: Instant, requestSessionId: UUID? = null): Boolean =
        turnsRemaining <= 0 || now >= expiresAt ||
            (sessionId != null && requestSessionId != null && sessionId != requestSessionId)

    fun decrementTurn(newQuestionId: UUID = UUID.randomUUID(), answerId: UUID? = null): PendingClarificationState =
        copy(turnsRemaining = turnsRemaining - 1, questionId = newQuestionId, lastAnswerId = answerId)
}

/**
 * Session-scoped registry at orchestrator level holding at most ONE active pending clarification.
 * Agents request a slot and receive the reply; they do not hold their own state.
 */
class ClarificationRegistry(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val active = AtomicReference<PendingClarificationState?>(null)

    @Suppress("ReturnCount")
    fun current(): PendingClarificationState? {
        val entry = active.get() ?: return null
        if (entry.isExpired(clock.instant())) {
            active.compareAndSet(entry, null)
            return null
        }
        return entry
    }

    fun requestSlot(state: PendingClarificationState) {
        active.set(state)
    }

    fun consume(expected: PendingClarificationState): Boolean =
        active.compareAndSet(expected, null)

    fun replace(expected: PendingClarificationState, next: PendingClarificationState): Boolean =
        active.compareAndSet(expected, next)

    fun clear() {
        active.set(null)
    }

    fun clearDomain(domain: ClarificationDomain) {
        val current = active.get()
        if (current?.domain == domain) {
            active.compareAndSet(current, null)
        }
    }
}

