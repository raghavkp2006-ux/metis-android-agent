package dev.metis.agent.domain.agent

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Uses the same canonical acceptance boundary for reminders and delegates task/read requests. */
interface ReminderClarification {
    suspend fun answerReminder(originalRequestId: UUID, questionId: UUID, answer: AgentRequest): AgentResult
    fun cancelReminderClarification()
}

class ConfirmedReminderAgent(
    private val fallback: ConfirmableAgent,
    private val store: AcceptedReminderStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: Clock = Clock.systemUTC(),
) : ConfirmableAgent, ReminderClarification {
    private val pending = AtomicReference<ActionProposal?>()
    private val clarification = ClarificationCoordinator<ReminderClarificationKind, ReminderDetails>(clock)

    override suspend fun process(request: AgentRequest): AgentResult {
        cancelReminderClarification()
        discardProposal()
        val capturedZone = zone()
        val rules = EnglishRules { capturedZone }
        val parsed = rules.parse(request)
        if (parsed.intent != AgentIntent.CREATE_REMINDER) return fallback.process(request)
        val result = processReminder(request, capturedZone)
        if (result.followUp?.missingFields == setOf("time")) {
            val date = ReminderResolution.resolveDate(request,
                parsed.entities.firstOrNull { it.type == EntityKind.TIME }?.rawValue, capturedZone)
            val title = parsed.entities.firstOrNull { it.type == EntityKind.TITLE }?.rawValue
            if (date != null && title != null) clarification.begin(PendingClarification(
                ReminderClarificationKind.TIME, request.id, requireNotNull(result.followUp).id,
                request.conversationId, request.timestamp, clock.instant().plusSeconds(FOLLOW_UP_SECONDS),
                ReminderDetails(request.screenContext, date, title, capturedZone)))
        }
        return result
    }

    private suspend fun processReminder(request: AgentRequest, capturedZone: ZoneId): AgentResult {
        val rules = EnglishRules { capturedZone }
        val contexts = object : ContextBuilder {
            override suspend fun snapshot(request: AgentRequest) = store.context(request)
        }
        val specialist = object : AgentSpecialist {
            override suspend fun respond(request: ParsedRequest) = suggestion(request, capturedZone)
        }
        val result = LocalAgentOrchestrator(rules, rules, contexts, specialist, ProposalPolicy(),
            "Review this approximate reminder. Nothing is scheduled until you accept.").process(request)
        currentCoroutineContext().ensureActive()
        pending.set(result.proposals.singleOrNull())
        return result
    }

    @Suppress("ReturnCount", "ComplexCondition") // Each identity, expiry, and retry boundary fails closed.
    override suspend fun answerReminder(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest): AgentResult {
        val current = clarification.answer(ReminderClarificationKind.TIME, originalRequestId,
            questionId, answer) ?: return denied(answer)
        val time = ReminderResolution.followUpTime(answer.text)
        if (time == null || ReminderResolution.resolveOnDate(answer, current.payload.date, time,
            current.payload.zone) == null) {
            if (current.invalidAnswers > 0) {
                clarification.consume(current)
                return AgentResult(answer.id, AgentResultStatus.DENIED,
                    "Reminder clarification cancelled after two invalid time answers.")
            }
            val question = ReminderResolution.TIME_QUESTION
            val next = current.copy(questionId = UUID.randomUUID(), invalidAnswers = 1,
                lastAnswerId = answer.id)
            if (!clarification.replace(current, next)) return denied(answer)
            return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, question,
                followUp = FollowUpQuestion(next.questionId, answer.id, question, setOf("time")))
        }
        if (!clarification.consume(current)) return denied(answer)
        discardProposal()
        val resumed = answer.copy(text = "remind me ${current.payload.date} at $time to ${current.payload.title}",
            screenContext = current.payload.screenContext, conversationId = current.sessionId)
        return processReminder(resumed, current.payload.zone)
    }

    override fun cancelReminderClarification() { clarification.clear() }

    private fun denied(answer: AgentRequest) = AgentResult(answer.id, AgentResultStatus.DENIED,
        "This reminder clarification is no longer active. Submit a fresh request.")

    @Suppress("ReturnCount") // Delegate other action types before consuming the canonical reminder proposal.
    override suspend fun accept(proposal: ActionProposal): AgentResult {
        if (proposal.action !is CreateReminderAction) return fallback.accept(proposal)
        if (!pending.compareAndSet(proposal, null)) return AgentResult(proposal.requestId, AgentResultStatus.DENIED,
            "This reminder proposal is no longer active. Submit a fresh request.")
        return try {
            val receipt = withContext(NonCancellable) { store.accept(proposal, UUID.randomUUID()) }
            AgentResult(receipt.requestId,
                if (receipt.outcome.status == ActionStatus.SUCCEEDED) AgentResultStatus.ANSWER
                else AgentResultStatus.FAILED,
                receipt.reason, completedActions = if (receipt.outcome.status == ActionStatus.SUCCEEDED)
                    listOf(receipt) else emptyList())
        } catch (rejected: ActionRejectedException) {
            AgentResult(proposal.requestId, AgentResultStatus.DENIED, rejected.userMessage)
        }
    }

    override suspend fun undo(receipt: ActionReceipt) = if (receipt.actionType == ActionType.CREATE_REMINDER) {
        withContext(NonCancellable) { store.undo(receipt) }
    } else fallback.undo(receipt)

    override fun discardProposal() { pending.set(null); fallback.discardProposal() }

    private fun suggestion(request: ParsedRequest, capturedZone: ZoneId): SpecialistResult {
        val timeEntity = request.entities.firstOrNull { it.type == EntityKind.TIME }
        val time = timeEntity?.normalizedValue as? NormalizedValue.Time
        val title = request.entities.firstOrNull { it.type == EntityKind.TITLE }?.rawValue
        val validTitle = title != null && title.length <= MAX_TITLE && title.none { it.isISOControl() }
        val dateAvailable = timeEntity == null || ReminderResolution.resolveDate(request.request,
            timeEntity.rawValue, capturedZone) != null
        if (time == null && validTitle && dateAvailable) {
            return SpecialistResult.Clarification(ReminderResolution.TIME_QUESTION, setOf("time"))
        }
        return if (time == null || !validTitle) {
            SpecialistResult.Clarification("Specify a future date, time with AM/PM or HH:mm, and what to remember. " +
                "Ambiguous or invalid local times need clarification.",
                setOf("date/time/title"))
        } else SpecialistResult.Suggestion(CreateReminderAction(
            ActionIdentity(UUID.randomUUID(), request.request.id, UUID.randomUUID()), requireNotNull(title), time.value,
            SchedulingPrecision.APPROXIMATE),
            "You requested this reminder. Android may deliver it late; scheduling does not prove delivery. " +
                "It only posts a local notification and never calls or messages anyone.")
    }
    private enum class ReminderClarificationKind { TIME }

    private data class ReminderDetails(
        val screenContext: ScreenContext?,
        val date: LocalDate,
        val title: String,
        val zone: ZoneId,
    )

    private companion object {
        const val MAX_TITLE = 500
        const val FOLLOW_UP_SECONDS = 300L
    }
}
