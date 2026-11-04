package dev.metis.agent.domain.agent

import dev.metis.agent.domain.contacts.ContactsResolver
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
    suspend fun answerReminderPerson(originalRequestId: UUID, questionId: UUID, answer: AgentRequest): AgentResult =
        AgentResult(answer.id, AgentResultStatus.DENIED, "No person clarification is active.")
    suspend fun answerReminderContactPermission(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest, granted: Boolean): AgentResult =
        AgentResult(answer.id, AgentResultStatus.DENIED, "No contact clarification is active.")
    fun cancelReminderClarification()
}

@Suppress("TooManyFunctions", "LongParameterList")
// Time, person and permission clarifications have distinct guarded entries.
class ConfirmedReminderAgent(
    private val fallback: ConfirmableAgent,
    private val store: AcceptedReminderStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: Clock = Clock.systemUTC(),
    private val contacts: ContactsResolver? = null,
    private val mayRequestContacts: () -> Boolean = { true },
    registry: ClarificationRegistry? = null,
) : ConfirmableAgent, ReminderClarification {
    override val clarificationRegistry: ClarificationRegistry =
        registry ?: fallback.clarificationRegistry ?: ClarificationRegistry(clock)
    private val pending = AtomicReference<ActionProposal?>()
    private val people = ActionPersonResolver(contacts)

    override fun fork(): ConfirmableAgent? = fallback.fork()?.let {
        ConfirmedReminderAgent(it, store, zone, clock, contacts, mayRequestContacts, ClarificationRegistry(clock))
    }
    override val reminderFollowUp: ReminderClarification get() = this
    override val promiseFollowUp: PromiseClarification? get() = fallback.promiseFollowUp

    @Suppress("ReturnCount")
    override suspend fun process(request: AgentRequest): AgentResult {
        handleActiveClarification(request)?.let { return it }
        cancelReminderClarification()
        discardProposal()
        val capturedZone = zone()
        val rules = EnglishRules { capturedZone }
        val parsed = rules.parse(request)
        if (parsed.intent != AgentIntent.CREATE_REMINDER) return fallback.process(request)
        val result = continueReminder(request, capturedZone, parsed)
        if (result.followUp?.missingFields == setOf("time")) {
            val date = ReminderResolution.resolveDate(request,
                parsed.entities.firstOrNull { it.type == EntityKind.TIME }?.rawValue, capturedZone)
            val title = parsed.entities.firstOrNull { it.type == EntityKind.TITLE }?.rawValue
            if (date != null && title != null) clarificationRegistry.requestSlot(PendingClarificationState(
                ClarificationDomain.REMINDER, "time",
                mapOf("date" to date.toString(), "title" to title),
                request.timestamp, clock.instant().plusSeconds(FOLLOW_UP_SECONDS),
                turnsRemaining = 2,
                originalRequestId = request.id,
                questionId = requireNotNull(result.followUp).id,
                sessionId = request.conversationId,
                prompt = ReminderResolution.TIME_QUESTION,
                payload = ReminderDetails(request.screenContext, date, title, capturedZone)))
        }
        return result
    }

    @Suppress("ReturnCount")
    private suspend fun handleActiveClarification(request: AgentRequest): AgentResult? {
        val active = clarificationRegistry.current() ?: return null
        if (active.domain != ClarificationDomain.REMINDER || active.slot != "time") return null
        val now = clock.instant()
        if (request.timestamp >= active.expiresAt || now >= active.expiresAt) return null
        if (active.sessionId != null && request.conversationId != null &&
            request.conversationId != active.sessionId) return null
        if (isClarificationCancel(request.text)) {
            cancelReminderClarification()
            return AgentResult(request.id, AgentResultStatus.DENIED, "Clarification cancelled.")
        }
        val rules = EnglishRules { zone() }
        val parsed = rules.parse(request)
        return if (ReminderResolution.followUpTime(request.text) != null || parsed.intent == AgentIntent.UNKNOWN) {
            answerReminder(active.originalRequestId, active.questionId, request)
        } else null
    }

    @Suppress("LongParameterList", "CyclomaticComplexMethod", "ReturnCount")
    // Validation, exact matches, permission and ambiguity all exit before proposal creation.
    private suspend fun continueReminder(request: AgentRequest, capturedZone: ZoneId,
        parsed: LanguageParse = EnglishRules { capturedZone }.parse(request),
        overridePerson: String? = null, selected: ActionPersonChoice? = null,
        skipContacts: Boolean = false, allowPermissionQuestion: Boolean = true): AgentResult {
        val time = parsed.entities.firstOrNull { it.type == EntityKind.TIME }
            ?.normalizedValue as? NormalizedValue.Time
        val title = parsed.entities.firstOrNull { it.type == EntityKind.TITLE }?.rawValue
        if (time == null || title == null) return processReminder(request, capturedZone)
        val person = overridePerson ?: ReminderPersonName.fromTitle(title)
            ?: return processReminder(request, capturedZone)
        val matches = store.matchingPeople(person)
        val resolved = if (selected == null) people.resolve(person, matches, skipContacts) else {
            if (selected.savedPersonId != null && selected.contactLookupKey == null &&
                matches.none { it.id == selected.savedPersonId })
                return AgentResult(request.id, AgentResultStatus.DENIED,
                    "The person changed. Submit a fresh reminder.")
            if (selected.contactLookupKey != null) {
                val refreshed = people.resolve(person, matches)
                val stillAvailable = when (refreshed) {
                    is ActionPersonResolution.Resolved -> refreshed.choice.contactLookupKey ==
                        selected.contactLookupKey
                    is ActionPersonResolution.Ambiguous -> refreshed.choices.any {
                        it.contactLookupKey == selected.contactLookupKey
                    }
                    else -> false
                }
                if (!stillAvailable) return processReminder(request, capturedZone)
            }
            ActionPersonResolution.Resolved(selected)
        }
        return when (resolved) {
            is ActionPersonResolution.Resolved -> when (val linked = linkContact(resolved.choice)) {
                is ActionPersonResolution.Resolved -> processReminder(request, capturedZone, linked.choice)
                is ActionPersonResolution.Ambiguous -> beginContactQuestion(request,
                    ContactDetails(title, time.value, person, capturedZone, request.screenContext, linked.choices),
                    ContactSlot.SELECTION)
                else -> processReminder(request, capturedZone)
            }
            ActionPersonResolution.Typed -> processReminder(request, capturedZone)
            ActionPersonResolution.PermissionNeeded -> if (allowPermissionQuestion && mayRequestContacts())
                beginContactQuestion(request, ContactDetails(title, time.value, person, capturedZone,
                    request.screenContext), ContactSlot.PERMISSION)
            else processReminder(request, capturedZone)
            is ActionPersonResolution.Ambiguous -> beginContactQuestion(request,
                ContactDetails(title, time.value, person, capturedZone, request.screenContext, resolved.choices),
                if (resolved.refine) ContactSlot.REFINE else ContactSlot.SELECTION)
        }
    }

    @Suppress("ReturnCount") // Existing identity matches and ambiguous names must stop before proposal creation.
    private suspend fun linkContact(choice: ActionPersonChoice): ActionPersonResolution {
        val key = choice.contactLookupKey ?: return ActionPersonResolution.Resolved(choice)
        if (choice.savedPersonId != null) return ActionPersonResolution.Resolved(choice)
        val keyed = store.matchingContactKey(key)
        val matches = if (keyed.isEmpty()) store.matchingPeople(choice.displayName) else keyed
        return when {
            matches.size > dev.metis.agent.domain.contacts.MAX_CANDIDATES ->
                ActionPersonResolution.Ambiguous(emptyList(), true)
            matches.size > 1 -> ActionPersonResolution.Ambiguous(matches.map {
                ActionPersonChoice(choice.displayName, it.id, key)
            }, false)
            matches.size == 1 -> ActionPersonResolution.Resolved(
                ActionPersonChoice(choice.displayName, matches.single().id, key))
            else -> ActionPersonResolution.Resolved(choice)
        }
    }

    private suspend fun processReminder(request: AgentRequest, capturedZone: ZoneId,
        selected: ActionPersonChoice? = null): AgentResult {
        val rules = EnglishRules { capturedZone }
        val contexts = object : ContextBuilder {
            override suspend fun snapshot(request: AgentRequest) = store.context(request)
        }
        val specialist = object : AgentSpecialist {
            override suspend fun respond(request: ParsedRequest) = suggestion(request, capturedZone, selected)
        }
        val result = LocalAgentOrchestrator(rules, rules, contexts, specialist, ProposalPolicy(),
            "Review this approximate reminder. Nothing is scheduled until you accept.").process(request)
        currentCoroutineContext().ensureActive()
        pending.set(result.proposals.singleOrNull())
        return result
    }

    @Suppress("ReturnCount", "ComplexCondition", "CyclomaticComplexMethod")
    override suspend fun answerReminder(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest): AgentResult {
        val current = clarificationRegistry.current()?.takeIf {
            it.domain == ClarificationDomain.REMINDER && it.slot == "time"
        } ?: return denied(answer)
        if (current.originalRequestId != originalRequestId || current.questionId != questionId) {
            return denied(answer)
        }
        if (current.sessionId != null && answer.conversationId != null &&
            current.sessionId != answer.conversationId) return denied(answer)
        if (answer.timestamp < current.createdAt || answer.id == originalRequestId ||
            answer.id == current.lastAnswerId) return denied(answer)
        if (clock.instant() >= current.expiresAt || answer.timestamp >= current.expiresAt) {
            clarificationRegistry.consume(current)
            return denied(answer)
        }
        val payload = current.payload as? ReminderDetails ?: return denied(answer)
        val time = ReminderResolution.followUpTime(answer.text)
        if (time == null || ReminderResolution.resolveOnDate(answer, payload.date, time,
            payload.zone) == null) {
            if (current.turnsRemaining <= 1) {
                clarificationRegistry.consume(current)
                return AgentResult(answer.id, AgentResultStatus.DENIED,
                    "Reminder clarification cancelled after two invalid time answers.")
            }
            val question = ReminderResolution.TIME_QUESTION
            val next = current.decrementTurn(UUID.randomUUID(), answer.id)
            if (!clarificationRegistry.replace(current, next)) return denied(answer)
            return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, question,
                followUp = FollowUpQuestion(next.questionId, answer.id, question, setOf("time")))
        }
        if (!clarificationRegistry.consume(current)) return denied(answer)
        discardProposal()
        val resumed = answer.copy(text = "remind me ${payload.date} at $time to ${payload.title}",
            screenContext = payload.screenContext, conversationId = current.sessionId)
        return continueReminder(resumed, payload.zone)
    }

    private fun beginContactQuestion(request: AgentRequest, details: ContactDetails,
        slot: ContactSlot): AgentResult {
        val question = when (slot) {
            ContactSlot.PERMISSION -> "Allow Contacts access to check this name? Only a selected display name " +
                "and contact reference are saved if you accept. You can continue without access."
            ContactSlot.SELECTION -> if (details.choices.all { it.savedPersonId != null }) "Which person?"
                else "Which ${details.person.substringBefore(' ')}?"
            ContactSlot.REFINE -> "More than five people match. Type a fuller name."
        }
        val field = when (slot) {
            ContactSlot.PERMISSION -> "reminder_contact_permission"
            ContactSlot.SELECTION -> "reminder_contact_choice"
            ContactSlot.REFINE -> "reminder_contact_refine"
        }
        val followUp = FollowUpQuestion(UUID.randomUUID(), request.id, question, setOf(field),
            details.choices.map { it.displayName })
        clarificationRegistry.requestSlot(PendingClarificationState(
            domain = ClarificationDomain.REMINDER,
            slot = field,
            filledSlots = mapOf("person" to details.person, "title" to details.title),
            createdAt = request.timestamp,
            expiresAt = clock.instant().plusSeconds(FOLLOW_UP_SECONDS),
            turnsRemaining = 2,
            originalRequestId = request.id,
            questionId = followUp.id,
            sessionId = request.conversationId,
            prompt = question,
            suggestedChoices = details.choices.map { it.displayName },
            payload = details
        ))
        return AgentResult(request.id, AgentResultStatus.FOLLOW_UP, question, followUp = followUp)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "ComplexCondition", "LongMethod")
    // Stale, invalid and ambiguous answers fail without proposals.
    override suspend fun answerReminderPerson(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest): AgentResult {
        val current = clarificationRegistry.current()?.takeIf {
            it.domain == ClarificationDomain.REMINDER &&
                it.slot in setOf("reminder_contact_choice", "reminder_contact_refine")
        } ?: return denied(answer)
        if (current.originalRequestId != originalRequestId || current.questionId != questionId) {
            return denied(answer)
        }
        if (current.sessionId != null && answer.conversationId != null &&
            current.sessionId != answer.conversationId) return denied(answer)
        if (answer.timestamp < current.createdAt || answer.id == originalRequestId ||
            answer.id == current.lastAnswerId) return denied(answer)
        if (clock.instant() >= current.expiresAt || answer.timestamp >= current.expiresAt) {
            clarificationRegistry.consume(current)
            return denied(answer)
        }
        val details = current.payload as? ContactDetails ?: return denied(answer)
        val value = answer.text.trim()
        if (value.equals("cancel", true) || value.equals("ignore", true)) {
            clarificationRegistry.consume(current)
            return AgentResult(answer.id, AgentResultStatus.DENIED,
                "Reminder clarification cancelled. Nothing was scheduled.")
        }
        val isSelection = current.slot == "reminder_contact_choice"
        val selected = if (isSelection)
            value.toIntOrNull()?.let { details.choices.getOrNull(it - 1) }
                ?: details.choices.filter { it.displayName.equals(value, true) }.singleOrNull()
            else null
        val valid = value.isNotBlank() && value.length <= MAX_PERSON_ANSWER &&
            value.none(Char::isISOControl) && EnglishRules().parse(answer).intent == AgentIntent.UNKNOWN &&
            (!isSelection || selected != null)
        if (!valid) {
            if (current.turnsRemaining <= 1) {
                clarificationRegistry.consume(current)
                return AgentResult(answer.id, AgentResultStatus.DENIED,
                    "Reminder clarification cancelled after two invalid answers.")
            }
            val next = current.decrementTurn(UUID.randomUUID(), answer.id)
            if (!clarificationRegistry.replace(current, next)) return denied(answer)
            val field = current.slot
            val prompt = if (isSelection) "Choose one listed person." else "Type a fuller name."
            return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, prompt,
                followUp = FollowUpQuestion(next.questionId, answer.id, prompt, setOf(field),
                    details.choices.map { it.displayName }))
        }
        if (!isSelection) {
            val refined = people.resolve(value, store.matchingPeople(value))
            if (refined is ActionPersonResolution.Ambiguous && refined.refine) {
                if (current.turnsRemaining <= 1) {
                    clarificationRegistry.consume(current)
                    return AgentResult(answer.id, AgentResultStatus.DENIED,
                        "Reminder clarification cancelled after two unclear answers.")
                }
                val next = current.copy(questionId = UUID.randomUUID(), turnsRemaining = current.turnsRemaining - 1,
                    lastAnswerId = answer.id, payload = details.copy(person = value))
                if (!clarificationRegistry.replace(current, next)) return denied(answer)
                val prompt = "More than five people match. Type a fuller name."
                return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, prompt,
                    followUp = FollowUpQuestion(next.questionId, answer.id, prompt,
                        setOf("reminder_contact_refine")))
            }
        }
        if (!clarificationRegistry.consume(current)) return denied(answer)
        return resumeContact(answer, details, selected, if (!isSelection) value else details.person)
    }

    @Suppress("ReturnCount", "ComplexCondition", "CyclomaticComplexMethod")
    override suspend fun answerReminderContactPermission(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest, granted: Boolean): AgentResult {
        val current = clarificationRegistry.current()?.takeIf {
            it.domain == ClarificationDomain.REMINDER && it.slot == "reminder_contact_permission"
        } ?: return denied(answer)
        if (current.originalRequestId != originalRequestId || current.questionId != questionId) {
            return denied(answer)
        }
        if (current.sessionId != null && answer.conversationId != null &&
            current.sessionId != answer.conversationId) return denied(answer)
        if (answer.timestamp < current.createdAt || answer.id == originalRequestId ||
            answer.id == current.lastAnswerId) return denied(answer)
        if (clock.instant() >= current.expiresAt || answer.timestamp >= current.expiresAt) {
            clarificationRegistry.consume(current)
            return denied(answer)
        }
        if (!clarificationRegistry.consume(current)) return denied(answer)
        val details = current.payload as? ContactDetails ?: return denied(answer)
        return resumeContact(answer, details, skipContacts = !granted,
            allowPermissionQuestion = false)
    }

    @Suppress("LongParameterList") // Resumption carries the validated trigger and selected person together.
    private suspend fun resumeContact(answer: AgentRequest, details: ContactDetails,
        selected: ActionPersonChoice? = null, person: String = details.person, skipContacts: Boolean = false,
        allowPermissionQuestion: Boolean = true): AgentResult {
        val resumed = answer.copy(text = "remind me ${details.trigger.local.toLocalDate()} at " +
            "${details.trigger.local.toLocalTime()} to ${details.title}",
            screenContext = details.screenContext)
        return continueReminder(resumed, details.zone, overridePerson = person, selected = selected,
            skipContacts = skipContacts, allowPermissionQuestion = allowPermissionQuestion)
    }

    override fun cancelReminderClarification() { clarificationRegistry.clearDomain(ClarificationDomain.REMINDER) }

    private fun denied(answer: AgentRequest) = AgentResult(answer.id, AgentResultStatus.DENIED,
        "This reminder clarification is no longer active. Submit a fresh request.")

    private fun isClarificationCancel(text: String): Boolean {
        val lower = text.trim().lowercase(java.util.Locale.ROOT)
        return lower in setOf("cancel", "never mind", "nevermind", "forget it", "forget that",
            "cancel that", "ignore", "don't remind me", "dont remind me")
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

    private fun suggestion(request: ParsedRequest, capturedZone: ZoneId,
        selected: ActionPersonChoice?): SpecialistResult {
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
            SchedulingPrecision.APPROXIMATE,
            personId = selected?.savedPersonId ?: selected?.contactLookupKey?.let { UUID.randomUUID() },
            personName = selected?.displayName, contactLookupKey = selected?.contactLookupKey),
            "You requested this reminder. Android may deliver it late; scheduling does not prove delivery. " +
                "It only posts a local notification and never calls or messages anyone.")
    }

    private enum class ContactSlot { PERMISSION, SELECTION, REFINE }

    private data class ContactDetails(val title: String, val trigger: ResolvedTime, val person: String,
        val zone: ZoneId, val screenContext: ScreenContext?,
        val choices: List<ActionPersonChoice> = emptyList()) {
        override fun toString(): String = "ReminderContactDetails(redacted)"
    }

    private data class ReminderDetails(
        val screenContext: ScreenContext?,
        val date: LocalDate,
        val title: String,
        val zone: ZoneId,
    )

    private companion object {
        const val MAX_TITLE = 500
        const val FOLLOW_UP_SECONDS = 300L
        const val MAX_PERSON_ANSWER = 100
    }
}
