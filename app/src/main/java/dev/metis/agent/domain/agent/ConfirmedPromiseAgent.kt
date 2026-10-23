package dev.metis.agent.domain.agent

import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface PromiseClarification {
    suspend fun answerPromise(originalRequestId: UUID, questionId: UUID, answer: AgentRequest): AgentResult
    fun cancelPromiseClarification()
}

interface AcceptedPromiseStore {
    suspend fun context(request: AgentRequest): ResolvedContext
    suspend fun matchingPeople(name: String): List<PromisePersonMatch>
    suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt
}

data class PromisePersonMatch(val id: UUID, val name: String)

/** Keeps incomplete commitments in RAM; storage is reachable only from explicit acceptance. */
class ConfirmedPromiseAgent(
    private val fallback: ConfirmableAgent,
    private val store: AcceptedPromiseStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: Clock = Clock.systemUTC(),
) : ConfirmableAgent, PromiseClarification {
    private val pending = AtomicReference<ActionProposal?>()
    private val clarification = ClarificationCoordinator<Slot, Details>(clock)

    @Suppress("ReturnCount") // An incomplete or unsupported request exits before proposal creation.
    override suspend fun process(request: AgentRequest): AgentResult {
        cancelPromiseClarification()
        discardProposal()
        if (EnglishRules().parse(request).intent != AgentIntent.CREATE_PROMISE) return fallback.process(request)
        val capturedZone = zone()
        val details = PromiseResolution.parse(request, capturedZone)
            ?: return question(request, "Please give a valid commitment and due date.", "promise")
        return proposeOrClarify(request, details, capturedZone)
    }

    @Suppress("ReturnCount") // Each unresolved slot exits without a proposal.
    private suspend fun proposeOrClarify(request: AgentRequest, details: PromiseDetails, capturedZone: ZoneId,
        selectedPerson: UUID? = null): AgentResult {
        val slot = when {
            details.person.isNullOrBlank() -> Slot.PERSON
            details.description.isNullOrBlank() -> Slot.DESCRIPTION
            else -> null
        }
        if (slot != null) return beginQuestion(request, details, capturedZone, slot)
        val person = requireNotNull(details.person)
        val matches = store.matchingPeople(person)
        if (matches.size > 1 && selectedPerson == null)
            return beginQuestion(request, details, capturedZone, Slot.SELECTION, matches)
        if (selectedPerson != null && matches.none { it.id == selectedPerson })
            return AgentResult(request.id, AgentResultStatus.DENIED, "The person changed. Submit a fresh request.")
        val context = store.context(request)
        val dueAt = details.dueDate?.plusDays(1)?.atStartOfDay(capturedZone)?.toInstant()?.minusMillis(1)
        val action = CreatePromiseAction(ActionIdentity(UUID.randomUUID(), request.id, UUID.randomUUID()),
            person, requireNotNull(details.description), details.dueDate, dueAt,
            selectedPerson ?: matches.singleOrNull()?.id)
        return when (val review = ProposalPolicy().review(action, context)) {
            is ValidationResult.Invalid -> AgentResult(request.id, AgentResultStatus.DENIED, review.userMessage)
            ValidationResult.Valid -> {
                val proposal = ActionProposal(UUID.randomUUID(), action, ActionRequirements.risk(action),
                    "Accept to save this promise locally.", emptyList(), true,
                    context.now, context.now.plusSeconds(PROPOSAL_LIFETIME_SECONDS))
                pending.set(proposal)
                AgentResult(request.id, AgentResultStatus.PROPOSAL,
                    "Review the promise. Nothing is saved until you accept.", listOf(proposal))
            }
        }
    }

    private fun beginQuestion(request: AgentRequest, details: PromiseDetails, capturedZone: ZoneId,
        slot: Slot, matches: List<PromisePersonMatch> = emptyList()): AgentResult {
        val result = question(request, when (slot) {
            Slot.PERSON -> "Who did you promise?"
            Slot.DESCRIPTION -> "What did you promise?"
            Slot.SELECTION -> "Which person? Reply with a number: " + matches.take(MAX_CHOICES)
                .mapIndexed { index, match -> "${index + 1}: ${match.name.take(MAX_CHOICE_NAME_LENGTH)} " +
                    "(${match.id.toString().takeLast(PERSON_ID_SUFFIX_LENGTH)})" }
                .joinToString(", ")
        }, slot.name.lowercase())
        clarification.begin(PendingClarification(slot, request.id, requireNotNull(result.followUp).id,
            request.conversationId, request.timestamp, clock.instant().plusSeconds(FOLLOW_UP_SECONDS),
            Details(details, capturedZone)))
        return result
    }

    private fun question(request: AgentRequest, prompt: String, field: String) =
        AgentResult(request.id, AgentResultStatus.FOLLOW_UP, prompt,
            followUp = FollowUpQuestion(UUID.randomUUID(), request.id, prompt, setOf(field)))

    @Suppress("CyclomaticComplexMethod", "ReturnCount") // Identity, expiry and retry checks fail closed.
    override suspend fun answerPromise(originalRequestId: UUID, questionId: UUID, answer: AgentRequest): AgentResult {
        val current = clarification.current() ?: return stale(answer)
        clarification.answer(current.kind, originalRequestId, questionId, answer) ?: return stale(answer)
        val value = answer.text.trim()
        if (value.equals("cancel", true) || value.equals("ignore", true)) {
            clarification.consume(current)
            return AgentResult(answer.id, AgentResultStatus.DENIED,
                "Promise clarification cancelled. Nothing was saved.")
        }
        val matches = if (current.kind == Slot.SELECTION)
            store.matchingPeople(requireNotNull(current.payload.details.person)) else emptyList()
        val selected = if (current.kind == Slot.SELECTION)
            value.toIntOrNull()?.takeIf { it in 1..MAX_CHOICES }?.let { matches.getOrNull(it - 1)?.id } else null
        val acceptable = value.isNotBlank() && value.length <= MAX_FIELD && value.none { it.isISOControl() } &&
            EnglishRules().parse(answer).intent == AgentIntent.UNKNOWN &&
            (current.kind != Slot.SELECTION || selected != null)
        if (!acceptable) {
            if (current.invalidAnswers > 0) {
                clarification.consume(current)
                return AgentResult(answer.id, AgentResultStatus.DENIED,
                    "Promise clarification cancelled after two invalid answers.")
            }
            val next = current.copy(questionId = UUID.randomUUID(), invalidAnswers = 1, lastAnswerId = answer.id)
            if (!clarification.replace(current, next)) return stale(answer)
            val prompt = when (current.kind) {
                Slot.PERSON -> "Who did you promise?"
                Slot.DESCRIPTION -> "What did you promise?"
                Slot.SELECTION -> "Which person? Reply with a listed number."
            }
            return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, prompt,
                followUp = FollowUpQuestion(next.questionId, answer.id, prompt, setOf(current.kind.name.lowercase())))
        }
        if (!clarification.consume(current)) return stale(answer)
        val details = when (current.kind) {
            Slot.PERSON -> current.payload.details.copy(person = value)
            Slot.DESCRIPTION -> current.payload.details.copy(description = value)
            Slot.SELECTION -> current.payload.details
        }
        return proposeOrClarify(answer, details, current.payload.zone, selected)
    }

    private fun stale(request: AgentRequest) = AgentResult(request.id, AgentResultStatus.DENIED,
        "This promise clarification is no longer active. Submit a fresh request.")

    override fun cancelPromiseClarification() { clarification.clear() }
    override fun discardProposal() { pending.set(null); fallback.discardProposal() }

    @Suppress("ReturnCount") // Only the live proposal may cross the acceptance boundary.
    override suspend fun accept(proposal: ActionProposal): AgentResult {
        if (proposal.action !is CreatePromiseAction) return fallback.accept(proposal)
        if (!pending.compareAndSet(proposal, null)) return AgentResult(proposal.requestId, AgentResultStatus.DENIED,
            "This promise proposal is no longer active. Submit a fresh request.")
        return try {
            taskReceiptResult(withContext(NonCancellable) { store.accept(proposal, UUID.randomUUID()) })
        } catch (rejected: ActionRejectedException) {
            AgentResult(proposal.requestId, AgentResultStatus.DENIED, rejected.userMessage)
        }
    }

    override suspend fun undo(receipt: ActionReceipt) =
        if (receipt.actionType == ActionType.CREATE_PROMISE) UndoResult.NOT_SUPPORTED else fallback.undo(receipt)

    private enum class Slot { PERSON, DESCRIPTION, SELECTION }
    private data class Details(val details: PromiseDetails, val zone: ZoneId)
    private companion object {
        const val FOLLOW_UP_SECONDS = 300L
        const val MAX_FIELD = 500
        const val MAX_CHOICES = 20
        const val MAX_CHOICE_NAME_LENGTH = 60
        const val PERSON_ID_SUFFIX_LENGTH = 8
    }
}
