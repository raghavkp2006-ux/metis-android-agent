package dev.metis.agent.domain.agent

import dev.metis.agent.domain.contacts.ContactsResolver
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface PromiseClarification {
    suspend fun answerPromise(originalRequestId: UUID, questionId: UUID, answer: AgentRequest): AgentResult
    suspend fun answerPromiseContactPermission(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest, granted: Boolean): AgentResult
    fun cancelPromiseClarification()
}

interface AcceptedPromiseStore {
    suspend fun context(request: AgentRequest): ResolvedContext
    suspend fun matchingPeople(name: String): List<PromisePersonMatch>
    suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt
}

data class PromisePersonMatch(val id: UUID, val name: String)

/** Keeps incomplete commitments in RAM; storage is reachable only from explicit acceptance. */
@Suppress("TooManyFunctions", "LongParameterList")
class ConfirmedPromiseAgent(
    private val fallback: ConfirmableAgent,
    private val store: AcceptedPromiseStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: Clock = Clock.systemUTC(),
    private val contacts: ContactsResolver? = null,
    private val mayRequestContacts: () -> Boolean = { true },
    registry: ClarificationRegistry? = null,
) : ConfirmableAgent, PromiseClarification {
    override val clarificationRegistry: ClarificationRegistry =
        registry ?: fallback.clarificationRegistry ?: ClarificationRegistry(clock)
    private val pending = AtomicReference<ActionProposal?>()
    private val people = ActionPersonResolver(contacts)

    override fun fork(): ConfirmableAgent? = fallback.fork()?.let {
        ConfirmedPromiseAgent(it, store, zone, clock, contacts, mayRequestContacts,
            it.clarificationRegistry ?: ClarificationRegistry(clock))
    }
    override val reminderFollowUp: ReminderClarification? get() = fallback.reminderFollowUp
    override val promiseFollowUp: PromiseClarification get() = this

    @Suppress("ReturnCount") // An incomplete or unsupported request exits before proposal creation.
    override suspend fun process(request: AgentRequest): AgentResult {
        val active = clarificationRegistry.current()
        if (active != null && active.domain == ClarificationDomain.PROMISE) {
            if (isClarificationCancel(request.text)) {
                cancelPromiseClarification()
                return AgentResult(request.id, AgentResultStatus.DENIED,
                    "Promise clarification cancelled. Nothing was saved.")
            }
            if (EnglishRules().parse(request).intent == AgentIntent.UNKNOWN) {
                return answerPromise(active.originalRequestId, active.questionId, request)
            }
        }
        cancelPromiseClarification()
        discardProposal()
        if (EnglishRules().parse(request).intent != AgentIntent.CREATE_PROMISE) return fallback.process(request)
        val capturedZone = zone()
        val details = PromiseResolution.parse(request, capturedZone)
            ?: return question(request, "Please give a valid commitment and due date.", "promise")
        return proposeOrClarify(request, details, capturedZone)
    }

    @Suppress("ReturnCount", "LongParameterList", "CyclomaticComplexMethod")
    // Every unresolved person or permission branch exits before creating a proposal.
    private suspend fun proposeOrClarify(request: AgentRequest, details: PromiseDetails, capturedZone: ZoneId,
        selected: ActionPersonChoice? = null, skipContacts: Boolean = false,
        allowPermissionQuestion: Boolean = true): AgentResult {
        val slot = when {
            details.person.isNullOrBlank() -> Slot.PERSON
            details.description.isNullOrBlank() -> Slot.DESCRIPTION
            else -> null
        }
        if (slot != null) return beginQuestion(request, details, capturedZone, slot)
        val person = requireNotNull(details.person)
        val matches = store.matchingPeople(person)
        val resolved = if (selected != null) {
            if (selected.savedPersonId != null && matches.none { it.id == selected.savedPersonId })
                return AgentResult(request.id, AgentResultStatus.DENIED,
                    "The person changed. Submit a fresh request.")
            if (selected.contactLookupKey != null) {
                val refreshed = people.resolve(person, matches)
                val valid = when (refreshed) {
                    is ActionPersonResolution.Resolved -> refreshed.choice.contactLookupKey ==
                        selected.contactLookupKey
                    is ActionPersonResolution.Ambiguous -> refreshed.choices.any {
                        it.contactLookupKey == selected.contactLookupKey
                    }
                    else -> false
                }
                if (!valid) return proposeOrClarify(request, details, capturedZone, skipContacts = true)
            }
            ActionPersonResolution.Resolved(selected)
        } else people.resolve(person, matches, skipContacts)
        when (resolved) {
            is ActionPersonResolution.Ambiguous -> return beginQuestion(request, details, capturedZone,
                if (resolved.refine) Slot.REFINE else Slot.SELECTION, resolved.choices)
            ActionPersonResolution.PermissionNeeded -> if (allowPermissionQuestion && mayRequestContacts())
                return beginQuestion(request, details, capturedZone, Slot.CONTACT_PERMISSION)
            else -> Unit
        }
        val choice = (resolved as? ActionPersonResolution.Resolved)?.choice
        val context = store.context(request)
        val dueAt = details.dueDate?.plusDays(1)?.atStartOfDay(capturedZone)?.toInstant()?.minusMillis(1)
        val action = CreatePromiseAction(ActionIdentity(UUID.randomUUID(), request.id, UUID.randomUUID()),
            choice?.displayName ?: person, requireNotNull(details.description), details.dueDate, dueAt,
            choice?.savedPersonId, choice?.contactLookupKey)
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
        slot: Slot, choices: List<ActionPersonChoice> = emptyList()): AgentResult {
        val slotName = when (slot) {
            Slot.SELECTION -> "selection"
            Slot.REFINE -> "contact_refine"
            Slot.CONTACT_PERMISSION -> "contact_permission"
            else -> slot.name.lowercase()
        }
        val prompt = when (slot) {
            Slot.PERSON -> "Who did you promise?"
            Slot.DESCRIPTION -> "What did you promise?"
            Slot.SELECTION -> if (choices.all { it.savedPersonId != null }) "Which person?"
                else "Which ${details.person?.substringBefore(' ')}?"
            Slot.REFINE -> "More than five people match. Type a fuller name."
            Slot.CONTACT_PERMISSION -> "Allow Contacts access to check this name? Only the selected " +
                "display name and contact reference are saved if you accept. You can continue without access."
        }
        val result = question(request, prompt, slotName, choices.map { it.displayName })
        val followUp = requireNotNull(result.followUp)
        val state = PendingClarificationState(
            domain = ClarificationDomain.PROMISE,
            slot = slotName,
            filledSlots = buildMap {
                details.person?.let { put("person", it) }
                details.description?.let { put("description", it) }
                details.dueDate?.let { put("dueDate", it.toString()) }
            },
            createdAt = request.timestamp,
            expiresAt = clock.instant().plusSeconds(FOLLOW_UP_SECONDS),
            turnsRemaining = 2,
            originalRequestId = request.id,
            questionId = followUp.id,
            sessionId = request.conversationId,
            prompt = prompt,
            suggestedChoices = choices.map { it.displayName },
            payload = Details(details, capturedZone, choices)
        )
        clarificationRegistry.requestSlot(state)
        return result
    }

    private fun question(request: AgentRequest, prompt: String, field: String,
        choices: List<String> = emptyList()) =
        AgentResult(request.id, AgentResultStatus.FOLLOW_UP, prompt,
            followUp = FollowUpQuestion(UUID.randomUUID(), request.id, prompt, setOf(field), choices))

    @Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod", "ComplexCondition")
    // Identity, expiry, ambiguity and retry checks fail closed before proposal creation.
    override suspend fun answerPromise(originalRequestId: UUID, questionId: UUID, answer: AgentRequest): AgentResult {
        val current = clarificationRegistry.current()?.takeIf { it.domain == ClarificationDomain.PROMISE }
            ?: return stale(answer)
        val sessionMatches = current.sessionId == null || answer.conversationId == null ||
            current.sessionId == answer.conversationId
        if (current.originalRequestId != originalRequestId || current.questionId != questionId ||
            !sessionMatches || answer.timestamp < current.createdAt || answer.id == originalRequestId ||
            answer.id == current.lastAnswerId) return stale(answer)
        if (clock.instant() >= current.expiresAt || answer.timestamp >= current.expiresAt) {
            clarificationRegistry.consume(current)
            return stale(answer)
        }
        val payload = current.payload as? Details ?: return stale(answer)
        val value = answer.text.trim()
        if (value.equals("cancel", true) || value.equals("ignore", true)) {
            clarificationRegistry.consume(current)
            return AgentResult(answer.id, AgentResultStatus.DENIED,
                "Promise clarification cancelled. Nothing was saved.")
        }
        if (current.slot == "contact_permission" && value.lowercase() in setOf("skip", "no", "continue")) {
            if (!clarificationRegistry.consume(current)) return stale(answer)
            return proposeOrClarify(answer, payload.details, payload.zone, skipContacts = true)
        }
        val selected = if (current.slot == "selection") {
            value.toIntOrNull()?.let { payload.choices.getOrNull(it - 1) }
                ?: payload.choices.filter { it.displayName.equals(value, ignoreCase = true) }
                    .singleOrNull()
        } else null
        val acceptable = value.isNotBlank() && value.length <= MAX_FIELD && value.none { it.isISOControl() } &&
            EnglishRules().parse(answer).intent == AgentIntent.UNKNOWN &&
            (current.slot != "selection" || selected != null) && current.slot != "contact_permission"
        if (!acceptable) {
            if (current.turnsRemaining <= 1) {
                clarificationRegistry.consume(current)
                return AgentResult(answer.id, AgentResultStatus.DENIED,
                    "Promise clarification cancelled after two invalid answers.")
            }
            val next = current.decrementTurn(UUID.randomUUID(), answer.id)
            if (!clarificationRegistry.replace(current, next)) return stale(answer)
            val prompt = when (current.slot) {
                "person" -> "Who did you promise?"
                "description" -> "What did you promise?"
                "selection" -> "Which person? Reply with a listed number."
                "contact_refine" -> "Type a fuller name."
                "contact_permission" -> "Choose Allow contacts or Continue without contacts."
                else -> current.prompt
            }
            return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, prompt,
                followUp = FollowUpQuestion(next.questionId, answer.id, prompt,
                    setOf(current.slot),
                    payload.choices.map { it.displayName }))
        }
        if (current.slot == "contact_refine") {
            val refined = people.resolve(value, store.matchingPeople(value))
            if (refined is ActionPersonResolution.Ambiguous && refined.refine) {
                if (current.turnsRemaining <= 1) {
                    clarificationRegistry.consume(current)
                    return AgentResult(answer.id, AgentResultStatus.DENIED,
                        "Promise clarification cancelled after two unclear answers.")
                }
                val next = current.copy(questionId = UUID.randomUUID(), turnsRemaining = current.turnsRemaining - 1,
                    lastAnswerId = answer.id,
                    payload = payload.copy(details = payload.details.copy(person = value)))
                if (!clarificationRegistry.replace(current, next)) return stale(answer)
                val prompt = "More than five people match. Type a fuller name."
                return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, prompt,
                    followUp = FollowUpQuestion(next.questionId, answer.id, prompt,
                        setOf("contact_refine")))
            }
        }
        if (!clarificationRegistry.consume(current)) return stale(answer)
        val details = when (current.slot) {
            "person" -> payload.details.copy(person = value)
            "description" -> payload.details.copy(description = value)
            "contact_refine" -> payload.details.copy(person = value)
            else -> payload.details
        }
        return proposeOrClarify(answer, details, payload.zone, selected)
    }

    @Suppress("ReturnCount", "ComplexCondition", "CyclomaticComplexMethod")
    // Invalid question IDs and stale answers cannot resume a proposal.
    override suspend fun answerPromiseContactPermission(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest, granted: Boolean): AgentResult {
        val current = clarificationRegistry.current()?.takeIf {
            it.domain == ClarificationDomain.PROMISE && it.slot == "contact_permission"
        } ?: return stale(answer)
        val sessionMatches = current.sessionId == null || answer.conversationId == null ||
            current.sessionId == answer.conversationId
        if (current.originalRequestId != originalRequestId || current.questionId != questionId ||
            !sessionMatches || answer.timestamp < current.createdAt || answer.id == originalRequestId ||
            answer.id == current.lastAnswerId) return stale(answer)
        if (clock.instant() >= current.expiresAt || answer.timestamp >= current.expiresAt) {
            clarificationRegistry.consume(current)
            return stale(answer)
        }
        if (!clarificationRegistry.consume(current)) return stale(answer)
        val payload = current.payload as? Details ?: return stale(answer)
        return proposeOrClarify(answer, payload.details, payload.zone,
            skipContacts = !granted, allowPermissionQuestion = false)
    }

    private fun stale(request: AgentRequest) = AgentResult(request.id, AgentResultStatus.DENIED,
        "This promise clarification is no longer active. Submit a fresh request.")

    private fun isClarificationCancel(text: String): Boolean {
        val lower = text.trim().lowercase(java.util.Locale.ROOT)
        return lower in setOf("cancel", "never mind", "nevermind", "forget it", "forget that",
            "cancel that", "ignore", "don't remind me", "dont remind me")
    }

    override fun cancelPromiseClarification() { clarificationRegistry.clearDomain(ClarificationDomain.PROMISE) }
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

    private enum class Slot { PERSON, DESCRIPTION, SELECTION, REFINE, CONTACT_PERMISSION }
    private data class Details(val details: PromiseDetails, val zone: ZoneId,
        val choices: List<ActionPersonChoice> = emptyList()) {
        override fun toString(): String = "PromiseDetails(redacted)"
    }
    private companion object {
        const val FOLLOW_UP_SECONDS = 300L
        const val MAX_FIELD = 500
    }
}
