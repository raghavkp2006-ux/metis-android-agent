package dev.metis.agent.domain.agent

import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** One ephemeral, typed follow-up. A new question replaces the old one atomically. */
internal class ClarificationCoordinator<Kind : Any, Payload : Any>(private val clock: Clock) {
    private val active = AtomicReference<PendingClarification<Kind, Payload>?>(null)

    fun current(): PendingClarification<Kind, Payload>? = active.get()

    fun begin(pending: PendingClarification<Kind, Payload>) { active.set(pending) }

    @Suppress("ComplexCondition", "ReturnCount") // Each identity and expiry boundary rejects a stale answer.
    fun answer(kind: Kind, originalRequestId: UUID, questionId: UUID,
        request: AgentRequest): PendingClarification<Kind, Payload>? {
        val pending = active.get() ?: return null
        if (pending.kind != kind || pending.originalRequestId != originalRequestId ||
            pending.questionId != questionId || pending.sessionId != request.conversationId ||
            request.timestamp < pending.startedAt || request.id == originalRequestId ||
            request.id == pending.lastAnswerId) return null
        if (clock.instant() >= pending.expiresAt || request.timestamp >= pending.expiresAt) {
            active.compareAndSet(pending, null)
            return null
        }
        return pending
    }

    fun replace(expected: PendingClarification<Kind, Payload>,
        next: PendingClarification<Kind, Payload>): Boolean = active.compareAndSet(expected, next)

    fun consume(expected: PendingClarification<Kind, Payload>): Boolean =
        active.compareAndSet(expected, null)

    fun clear() { active.set(null) }
}

internal data class PendingClarification<Kind : Any, Payload : Any>(
    val kind: Kind,
    val originalRequestId: UUID,
    val questionId: UUID,
    val sessionId: UUID?,
    val startedAt: Instant,
    val expiresAt: Instant,
    val payload: Payload,
    val invalidAnswers: Int = 0,
    val lastAnswerId: UUID? = null,
)
