package dev.metis.agent

import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.ClarificationCoordinator
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.PendingClarification
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarificationCoordinatorTest {
    private enum class FakeKind { FIRST, SECOND }

    private val now = Instant.parse("2026-10-09T05:30:00Z")
    private val clock = ClarificationTestClock(now)
    private val coordinator = ClarificationCoordinator<FakeKind, String>(clock)
    private val session = UUID.randomUUID()

    @Test fun anotherFlowCannotAnswerOrConsumeTheActiveQuestion() {
        val first = pending(FakeKind.FIRST)
        coordinator.begin(first)
        assertNull(coordinator.answer(FakeKind.SECOND, first.originalRequestId,
            first.questionId, answer()))
        assertSame(first, coordinator.current())
        assertSame(first, coordinator.answer(FakeKind.FIRST, first.originalRequestId,
            first.questionId, answer()))

        val second = pending(FakeKind.SECOND)
        coordinator.begin(second)
        assertNull(coordinator.answer(FakeKind.FIRST, first.originalRequestId,
            first.questionId, answer()))
        assertSame(second, coordinator.current())
        assertTrue(coordinator.consume(second))
        assertNull(coordinator.current())
    }

    @Test fun requestQuestionSessionAndAnswerIdentityStayBound() {
        val first = pending(FakeKind.FIRST)
        coordinator.begin(first)
        assertNull(coordinator.answer(FakeKind.FIRST, UUID.randomUUID(), first.questionId, answer()))
        assertNull(coordinator.answer(FakeKind.FIRST, first.originalRequestId, UUID.randomUUID(), answer()))
        assertNull(coordinator.answer(FakeKind.FIRST, first.originalRequestId, first.questionId,
            answer(sessionId = UUID.randomUUID())))
        assertNull(coordinator.answer(FakeKind.FIRST, first.originalRequestId, first.questionId,
            answer(id = first.originalRequestId)))
        assertNull(coordinator.answer(FakeKind.FIRST, first.originalRequestId, first.questionId,
            answer(at = now.minusSeconds(1))))
        assertSame(first, coordinator.current())

        val usedAnswer = answer()
        val retry = first.copy(questionId = UUID.randomUUID(), lastAnswerId = usedAnswer.id)
        assertTrue(coordinator.replace(first, retry))
        assertNull(coordinator.answer(FakeKind.FIRST, first.originalRequestId,
            first.questionId, answer()))
        assertNull(coordinator.answer(FakeKind.FIRST, first.originalRequestId,
            retry.questionId, usedAnswer))
        assertEquals("synthetic payload", coordinator.answer(FakeKind.FIRST,
            first.originalRequestId, retry.questionId, answer())?.payload)
        assertFalse(coordinator.consume(first))
        assertTrue(coordinator.consume(retry))
    }

    @Test fun expiryAndResetRejectLateAnswersForEitherFlow() {
        val first = pending(FakeKind.FIRST)
        coordinator.begin(first)
        clock.current = now.plusSeconds(301)
        assertNull(coordinator.answer(FakeKind.FIRST, first.originalRequestId,
            first.questionId, answer(at = now.plusSeconds(10))))
        assertNull(coordinator.current())

        clock.current = now
        val second = pending(FakeKind.SECOND)
        coordinator.begin(second)
        coordinator.clear()
        assertNull(coordinator.answer(FakeKind.SECOND, second.originalRequestId,
            second.questionId, answer()))
    }

    private fun pending(kind: FakeKind) = PendingClarification(kind, UUID.randomUUID(),
        UUID.randomUUID(), session, now, now.plusSeconds(300), "synthetic payload")

    private fun answer(id: UUID = UUID.randomUUID(), at: Instant = now.plusSeconds(10),
        sessionId: UUID = session) = AgentRequest(id, "synthetic answer", InputSource.TEXT,
        at, conversationId = sessionId)
}

private class ClarificationTestClock(var current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current
}
