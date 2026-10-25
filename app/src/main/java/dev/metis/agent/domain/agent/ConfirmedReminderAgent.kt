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

@Suppress("TooManyFunctions") // Time, person and permission clarifications have distinct guarded entries.
class ConfirmedReminderAgent(
    private val fallback: ConfirmableAgent,
    private val store: AcceptedReminderStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: Clock = Clock.systemUTC(),
    private val contacts: ContactsResolver? = null,
    private val mayRequestContacts: () -> Boolean = { true },
) : ConfirmableAgent, ReminderClarification {
    private val pending = AtomicReference<ActionProposal?>()
    private val clarification = ClarificationCoordinator<ReminderClarificationKind, ReminderDetails>(clock)
    private val contactClarification = ClarificationCoordinator<ContactSlot, ContactDetails>(clock)
    private val people = ActionPersonResolver(contacts)

    override fun fork(): ConfirmableAgent? = fallback.fork()?.let {
        ConfirmedReminderAgent(it, store, zone, clock, contacts, mayRequestContacts)
    }
    override val reminderFollowUp: ReminderClarification get() = this
    override val promiseFollowUp: PromiseClarification? get() = fallback.promiseFollowUp

    override suspend fun process(request: AgentRequest): AgentResult {
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
            if (date != null && title != null) clarification.begin(PendingClarification(
                ReminderClarificationKind.TIME, request.id, requireNotNull(result.followUp).id,
                request.conversationId, request.timestamp, clock.instant().plusSeconds(FOLLOW_UP_SECONDS),
                ReminderDetails(request.screenContext, date, title, capturedZone)))
        }
        return result
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
        return continueReminder(resumed, current.payload.zone)
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
        contactClarification.begin(PendingClarification(slot, request.id, followUp.id,
            request.conversationId, request.timestamp, clock.instant().plusSeconds(FOLLOW_UP_SECONDS), details))
        return AgentResult(request.id, AgentResultStatus.FOLLOW_UP, question, followUp = followUp)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    // Stale, invalid and ambiguous answers fail without proposals.
    override suspend fun answerReminderPerson(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest): AgentResult {
        val current = contactClarification.current() ?: return denied(answer)
        if (current.kind == ContactSlot.PERMISSION || contactClarification.answer(current.kind,
                originalRequestId, questionId, answer) == null) return denied(answer)
        val value = answer.text.trim()
        if (value.equals("cancel", true) || value.equals("ignore", true)) {
            contactClarification.consume(current)
            return AgentResult(answer.id, AgentResultStatus.DENIED,
                "Reminder clarification cancelled. Nothing was scheduled.")
        }
        val selected = if (current.kind == ContactSlot.SELECTION)
            value.toIntOrNull()?.let { current.payload.choices.getOrNull(it - 1) }
                ?: current.payload.choices.filter { it.displayName.equals(value, true) }.singleOrNull()
            else null
        val valid = value.isNotBlank() && value.length <= MAX_PERSON_ANSWER &&
            value.none(Char::isISOControl) && EnglishRules().parse(answer).intent == AgentIntent.UNKNOWN &&
            (current.kind != ContactSlot.SELECTION || selected != null)
        if (!valid) {
            if (current.invalidAnswers > 0) {
                contactClarification.consume(current)
                return AgentResult(answer.id, AgentResultStatus.DENIED,
                    "Reminder clarification cancelled after two invalid answers.")
            }
            val next = current.copy(questionId = UUID.randomUUID(), invalidAnswers = 1,
                lastAnswerId = answer.id)
            if (!contactClarification.replace(current, next)) return denied(answer)
            val field = if (current.kind == ContactSlot.SELECTION) "reminder_contact_choice"
                else "reminder_contact_refine"
            val prompt = if (current.kind == ContactSlot.SELECTION) "Choose one listed person."
                else "Type a fuller name."
            return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, prompt,
                followUp = FollowUpQuestion(next.questionId, answer.id, prompt, setOf(field),
                    current.payload.choices.map { it.displayName }))
        }
        if (current.kind == ContactSlot.REFINE) {
            val refined = people.resolve(value, store.matchingPeople(value))
            if (refined is ActionPersonResolution.Ambiguous && refined.refine) {
                if (current.invalidAnswers > 0) {
                    contactClarification.consume(current)
                    return AgentResult(answer.id, AgentResultStatus.DENIED,
                        "Reminder clarification cancelled after two unclear answers.")
                }
                val next = current.copy(questionId = UUID.randomUUID(), invalidAnswers = 1,
                    lastAnswerId = answer.id, payload = current.payload.copy(person = value))
                if (!contactClarification.replace(current, next)) return denied(answer)
                val prompt = "More than five people match. Type a fuller name."
                return AgentResult(answer.id, AgentResultStatus.FOLLOW_UP, prompt,
                    followUp = FollowUpQuestion(next.questionId, answer.id, prompt,
                        setOf("reminder_contact_refine")))
            }
        }
        if (!contactClarification.consume(current)) return denied(answer)
        return resumeContact(answer, current.payload, selected,
            if (current.kind == ContactSlot.REFINE) value else current.payload.person)
    }

    @Suppress("ReturnCount") // Expired or mismatched questions cannot resume the reminder.
    override suspend fun answerReminderContactPermission(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest, granted: Boolean): AgentResult {
        val current = contactClarification.answer(ContactSlot.PERMISSION, originalRequestId, questionId, answer)
            ?: return denied(answer)
        if (!contactClarification.consume(current)) return denied(answer)
        return resumeContact(answer, current.payload, skipContacts = !granted,
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

    override fun cancelReminderClarification() { clarification.clear(); contactClarification.clear() }

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
    private enum class ReminderClarificationKind { TIME }

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
