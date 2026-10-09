package dev.metis.agent.domain.agent

import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Uses the same canonical acceptance boundary for reminders and delegates task/read requests. */
class ConfirmedReminderAgent(
    private val fallback: ConfirmableAgent,
    private val store: AcceptedReminderStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ConfirmableAgent {
    private val pending = AtomicReference<ActionProposal?>()

    override suspend fun process(request: AgentRequest): AgentResult {
        discardProposal()
        val capturedZone = zone()
        val rules = EnglishRules { capturedZone }
        if (rules.parse(request).intent != AgentIntent.CREATE_REMINDER) return fallback.process(request)
        val contexts = object : ContextBuilder {
            override suspend fun snapshot(request: AgentRequest) = store.context(request)
        }
        val specialist = object : AgentSpecialist {
            override suspend fun respond(request: ParsedRequest) = suggestion(request)
        }
        val result = LocalAgentOrchestrator(rules, rules, contexts, specialist, ProposalPolicy(),
            "Review this approximate reminder. Nothing is scheduled until you accept.").process(request)
        currentCoroutineContext().ensureActive()
        pending.set(result.proposals.singleOrNull())
        return result
    }

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

    private fun suggestion(request: ParsedRequest): SpecialistResult {
        val time = request.entities.first { it.type == EntityKind.TIME }.normalizedValue as? NormalizedValue.Time
        val title = request.entities.firstOrNull { it.type == EntityKind.TITLE }?.rawValue
        val validTitle = title != null && title.length <= MAX_TITLE && title.none { it.isISOControl() }
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
    private companion object { const val MAX_TITLE = 500 }
}
