package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedReminderStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReminderHistoryEntry
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.presentation.navigation.isReminderFollowUpAnswer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderOptionalTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-10-09T05:30:00Z")
    private val conversation = UUID.randomUUID()
    private val store = NoWriteReminderStore(zone)
    private val fallback = UnrelatedAgent()
    private val agent = ConfirmedReminderAgent(fallback, store, { zone }, Clock.fixed(now, zone))

    @Test fun withTimeKeepsExistingProposalPath(): Unit = runBlocking {
        val input = request("remind me tomorrow at 8 am to call Rahul")
        val result = agent.process(input)
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        assertEquals(input.id, result.proposals.single().requestId)
        assertEquals("call Rahul", (result.proposals.single().action as CreateReminderAction).title)
        assertEquals(0, store.accepts)
    }

    @Test fun withoutTimeParsesAndAsksOnlyForTime(): Unit = runBlocking {
        val input = request("remind me tomorrow to call Rahul")
        val parsed = EnglishRules { zone }.parse(input)
        assertEquals(AgentIntent.CREATE_REMINDER, parsed.intent)
        assertEquals(listOf("tomorrow", "call Rahul"), parsed.entities.map { it.rawValue })
        assertEquals(listOf(EntityKind.TIME, EntityKind.TITLE), parsed.entities.map { it.type })
        assertNull(parsed.entities.first().normalizedValue)
        val result = agent.process(input)
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertTrue(result.message.startsWith("What time?"))
        assertEquals(setOf("time"), result.followUp?.missingFields)
        assertTrue(result.proposals.isEmpty())
        assertEquals(0, store.accepts)
    }

    @Test fun answerMakesFreshProposalWithoutWriting(): Unit = runBlocking {
        val original = request("remind me tomorrow to call Rahul")
        val question = requireNotNull(agent.process(original).followUp)
        val answer = request("8 am", now.plusSeconds(30))
        val result = agent.answerReminder(original.id, question.id, answer)
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        assertEquals(answer.id, result.proposals.single().requestId)
        assertNotEquals(original.id, result.proposals.single().requestId)
        val reminder = result.proposals.single().action as CreateReminderAction
        assertEquals("call Rahul", reminder.title)
        assertEquals(Instant.parse("2026-10-10T02:30:00Z"), reminder.trigger.instant)
        assertEquals(0, store.accepts)
    }

    @Test fun cancelledFollowUpCannotProduceProposal(): Unit = runBlocking {
        val original = request("remind me tomorrow to call Rahul")
        val question = requireNotNull(agent.process(original).followUp)
        agent.cancelReminderClarification()
        val result = agent.answerReminder(original.id, question.id, request("8 am", now.plusSeconds(30)))
        assertEquals(AgentResultStatus.DENIED, result.status)
        assertTrue(result.proposals.isEmpty())
        assertEquals(0, store.accepts)
    }

    @Test fun invalidAnswersReaskOnceThenCancelWithoutGuessing(): Unit = runBlocking {
        listOf("8", "banana", "25:00").forEach { invalid ->
            val original = request("remind me tomorrow to call Rahul")
            val first = requireNotNull(agent.process(original).followUp)
            val retry = agent.answerReminder(original.id, first.id, request(invalid, now.plusSeconds(10)))
            assertEquals(invalid, AgentResultStatus.FOLLOW_UP, retry.status)
            assertEquals(setOf("time"), retry.followUp?.missingFields)
            assertTrue(retry.proposals.isEmpty())
            val cancelled = agent.answerReminder(original.id, requireNotNull(retry.followUp).id,
                request(invalid, now.plusSeconds(20)))
            assertEquals(invalid, AgentResultStatus.DENIED, cancelled.status)
            assertTrue(cancelled.proposals.isEmpty())
        }
        assertEquals(0, store.accepts)
    }

    @Test fun originalDateAndZoneStayFixedAcrossMidnight(): Unit = runBlocking {
        val beforeMidnight = Instant.parse("2026-10-09T18:29:00Z")
        val midnightAgent = ConfirmedReminderAgent(UnrelatedAgent(), NoWriteReminderStore(zone),
            { zone }, Clock.fixed(beforeMidnight, zone))
        val original = request("remind me tomorrow to call Rahul", beforeMidnight)
        val question = requireNotNull(midnightAgent.process(original).followUp)
        val answer = request("8 am", beforeMidnight.plusSeconds(120))
        val result = midnightAgent.answerReminder(original.id, question.id, answer)
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        assertEquals(Instant.parse("2026-10-10T02:30:00Z"),
            (result.proposals.single().action as CreateReminderAction).trigger.instant)
        assertEquals(zone, (result.proposals.single().action as CreateReminderAction).trigger.zone)
    }

    @Test fun onlyNewestClarificationCanBeAnswered(): Unit = runBlocking {
        val firstRequest = request("remind me tomorrow to call Rahul")
        val first = requireNotNull(agent.process(firstRequest).followUp)
        val secondRequest = request("remind me tomorrow to study")
        val second = requireNotNull(agent.process(secondRequest).followUp)
        assertEquals(AgentResultStatus.DENIED, agent.answerReminder(firstRequest.id, first.id,
            request("8 am", now.plusSeconds(10))).status)
        val result = agent.answerReminder(secondRequest.id, second.id, request("9 am", now.plusSeconds(20)))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        assertEquals("study", (result.proposals.single().action as CreateReminderAction).title)
    }

    @Test fun unrelatedMessageClearsClarification(): Unit = runBlocking {
        val original = request("remind me tomorrow to call Rahul")
        val question = requireNotNull(agent.process(original).followUp)
        val unrelated = agent.process(request("show my tasks"))
        assertEquals(AgentResultStatus.UNSUPPORTED, unrelated.status)
        assertEquals(1, fallback.calls)
        assertEquals(AgentResultStatus.DENIED, agent.answerReminder(original.id, question.id,
            request("8 am", now.plusSeconds(20))).status)
        assertFalse(isReminderFollowUpAnswer(request("tell me a joke", now.plusSeconds(20)),
            conversation, now.plusSeconds(300)))
    }

    @Test fun timeoutAndResetLeaveStaleTextAsOrdinaryInput(): Unit = runBlocking {
        val original = request("remind me tomorrow to call Rahul")
        val question = requireNotNull(agent.process(original).followUp)
        val stale = request("8 am", now.plusSeconds(301))
        assertFalse(isReminderFollowUpAnswer(stale, conversation, now.plusSeconds(300)))
        assertFalse(isReminderFollowUpAnswer(request("8 am", now.plusSeconds(30), UUID.randomUUID()),
            conversation, now.plusSeconds(300)))
        assertEquals(AgentResultStatus.DENIED, agent.answerReminder(original.id, question.id, stale).status)
        assertEquals(AgentResultStatus.UNSUPPORTED, agent.process(stale).status)
        agent.process(original)
        agent.cancelReminderClarification() // Session or screen reset.
        assertEquals(AgentResultStatus.UNSUPPORTED, agent.process(request("8 am")).status)
        assertEquals(0, store.accepts)
    }

    @Test fun backdatedAnswerCannotBypassElapsedTimeout(): Unit = runBlocking {
        val ticking = AdjustableClock(now, zone)
        val timedAgent = ConfirmedReminderAgent(UnrelatedAgent(), NoWriteReminderStore(zone),
            { zone }, ticking)
        val original = request("remind me tomorrow to call Rahul")
        val question = requireNotNull(timedAgent.process(original).followUp)
        ticking.current = now.plusSeconds(301)
        val backdated = request("8 am", now.plusSeconds(10))
        assertEquals(AgentResultStatus.DENIED,
            timedAgent.answerReminder(original.id, question.id, backdated).status)
    }

    @Test fun forgedRequestIdAndConversationAreDeniedWithoutConsumingQuestion(): Unit = runBlocking {
        val original = request("remind me tomorrow to call Rahul")
        val question = requireNotNull(agent.process(original).followUp)
        assertEquals(AgentResultStatus.DENIED, agent.answerReminder(UUID.randomUUID(), question.id,
            request("8 am", now.plusSeconds(10))).status)
        assertEquals(AgentResultStatus.DENIED, agent.answerReminder(original.id, question.id,
            request("8 am", now.plusSeconds(10), UUID.randomUUID())).status)
        assertEquals(AgentResultStatus.PROPOSAL, agent.answerReminder(original.id, question.id,
            request("8 am", now.plusSeconds(10))).status)
    }

    @Test fun replayedAnswerIsDenied(): Unit = runBlocking {
        val original = request("remind me tomorrow to call Rahul")
        val question = requireNotNull(agent.process(original).followUp)
        val answer = request("8 am", now.plusSeconds(10))
        assertEquals(AgentResultStatus.PROPOSAL, agent.answerReminder(original.id, question.id, answer).status)
        assertEquals(AgentResultStatus.DENIED, agent.answerReminder(original.id, question.id, answer).status)
        assertEquals(0, store.accepts)
    }

    @Test fun reusedInvalidAnswerIdAndOriginalIdCannotBecomeAnswer(): Unit = runBlocking {
        val original = request("remind me tomorrow to call Rahul")
        val question = requireNotNull(agent.process(original).followUp)
        val invalid = request("8", now.plusSeconds(10))
        val retry = agent.answerReminder(original.id, question.id, invalid)
        assertEquals(AgentResultStatus.FOLLOW_UP, retry.status)
        assertEquals(AgentResultStatus.DENIED, agent.answerReminder(original.id,
            requireNotNull(retry.followUp).id, invalid).status)
        assertEquals(AgentResultStatus.DENIED, agent.answerReminder(original.id,
            requireNotNull(retry.followUp).id, original.copy(text = "8 am")).status)
        assertEquals(AgentResultStatus.PROPOSAL, agent.answerReminder(original.id,
            requireNotNull(retry.followUp).id, request("8 am", now.plusSeconds(20))).status)
    }

    private fun request(text: String, at: Instant = now, session: UUID = conversation) =
        AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, at, conversationId = session)
}

private class NoWriteReminderStore(private val zone: ZoneId) : AcceptedReminderStore {
    var accepts = 0
    override suspend fun context(request: AgentRequest) = ResolvedContext(request.timestamp, zone,
        AutonomyLevel.SUGGEST, CapabilitySnapshot(request.timestamp,
            setOf(Capability.LOCAL_WRITE, Capability.REMINDER_SCHEDULE, Capability.NOTIFICATIONS)),
        screenContext = request.screenContext, conversationId = request.conversationId)
    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
        accepts++
        error("The test never calls canonical accept")
    }
    override suspend fun retry(actionId: UUID): ActionReceipt = error("Unexpected retry")
    override suspend fun cancel(actionId: UUID): UndoResult = error("Unexpected cancellation")
    override suspend fun undo(receipt: ActionReceipt): UndoResult = error("Unexpected undo")
    override suspend fun reconcile() = Unit
    override fun history(): Flow<List<ReminderHistoryEntry>> = emptyFlow()
}

private class UnrelatedAgent : ConfirmableAgent {
    var calls = 0
    override suspend fun process(request: AgentRequest): AgentResult {
        calls++
        return AgentResult(request.id, AgentResultStatus.UNSUPPORTED, "Unrelated synthetic request")
    }
    override suspend fun accept(proposal: ActionProposal): AgentResult = error("Unexpected accept")
    override fun discardProposal() = Unit
    override suspend fun undo(receipt: ActionReceipt): UndoResult = error("Unexpected undo")
}

private class AdjustableClock(var current: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = AdjustableClock(current, zone)
    override fun instant(): Instant = current
}
