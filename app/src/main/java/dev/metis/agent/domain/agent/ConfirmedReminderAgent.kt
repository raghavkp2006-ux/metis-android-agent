package dev.metis.agent.domain.agent

import java.time.Clock
import java.time.Instant
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
    private val clarification = AtomicReference<PendingReminderClarification?>()

    override suspend fun process(request: AgentRequest): AgentResult {
        cancelReminderClarification()
        discardProposal()
        val capturedZone = zone()
        val rules = EnglishRules { capturedZone }
        val parsed = rules.parse(request)
        if (parsed.intent != AgentIntent.CREATE_REMINDER) return fallback.process(request)
        val result = processReminder(request, capturedZone)
        if (result.followUp?.missingFields == setOf("time")) {
            val date = parsed.entities.firstOrNull { it.type == EntityKind.TIME }?.rawValue
                ?.let { ReminderResolution.resolveDate(request, it, capturedZone) }
            val title = parsed.entities.firstOrNull { it.type == EntityKind.TITLE }?.rawValue
            if (date != null && title != null) clarification.set(PendingReminderClarification(
                request.id, requireNotNull(result.followUp).id, request.conversationId,
                request.screenContext, date, title, capturedZone,
                request.timestamp, clock.instant().plusSeconds(FOLLOW_UP_SECONDS)))
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
        val current = clarification.get() ?: return denied(answer)
        if (current.originalRequestId != originalRequestId || current.questionId != questionId ||
            current.conversationId != answer.conversationId || answer.timestamp < current.startedAt ||
            answer.id == originalRequestId || answer.id == current.lastAnswerId) return denied(answer)
        if (clock.instant() >= current.expiresAt || answer.timestamp >= current.expiresAt) {
            clarification.compareAndSet(current, null)
            return denied(answer)
        }
        val time = answer.text.trim()
        if (ReminderResolution.resolveOnDate(answer, current.date, time, current.zone) == null) {
            if (current.invalidAnswers > 0) {
                clarification.compareAndSet(current, null)
                return AgentResult(answer.id, AgentResultStatus.DENIED,
                    "Reminder clarification cancelled after two invalid time answers.")
            }
            val question = "What time? Use AM/PM or HH:mm."
            val next = current.copy(questionId = UUID.randomUUID(), invalidAnswers = 1,
                lastAnswerId = answer.id)
            if (!clarification.compareAndSet(current, next)) return denied(answer)
            return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, question,
                followUp = FollowUpQuestion(next.questionId, answer.id, question, setOf("time")))
        }
        if (!clarification.compareAndSet(current, null)) return denied(answer)
        discardProposal()
        val resumed = answer.copy(text = "remind me ${current.date} at $time to ${current.title}",
            screenContext = current.screenContext, conversationId = current.conversationId)
        return processReminder(resumed, current.zone)
    }

    override fun cancelReminderClarification() { clarification.set(null) }

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
        val timeEntity = request.entities.first { it.type == EntityKind.TIME }
        val time = timeEntity.normalizedValue as? NormalizedValue.Time
        val title = request.entities.firstOrNull { it.type == EntityKind.TITLE }?.rawValue
        val validTitle = title != null && title.length <= MAX_TITLE && title.none { it.isISOControl() }
        if (time == null && validTitle && ReminderResolution.resolveDate(request.request,
            timeEntity.rawValue, capturedZone) != null) {
            return SpecialistResult.Clarification("What time? Use AM/PM or HH:mm.", setOf("time"))
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
    private data class PendingReminderClarification(
        val originalRequestId: UUID,
        val questionId: UUID,
        val conversationId: UUID?,
        val screenContext: ScreenContext?,
        val date: LocalDate,
        val title: String,
        val zone: ZoneId,
        val startedAt: Instant,
        val expiresAt: Instant,
        val invalidAnswers: Int = 0,
        val lastAnswerId: UUID? = null,
    )

    private companion object {
        const val MAX_TITLE = 500
        const val FOLLOW_UP_SECONDS = 300L
    }
}
