package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.agent.AgentOrchestrator
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.FollowUpQuestion
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EnglishConversationState
import dev.metis.agent.domain.agent.EnglishConversationRules
import dev.metis.agent.domain.agent.ReferenceQuestion
import dev.metis.agent.domain.agent.ReminderClarification
import dev.metis.agent.domain.agent.PromiseClarification
import dev.metis.agent.domain.agent.ClarificationCoordinator
import dev.metis.agent.domain.agent.PendingClarification
import dev.metis.agent.domain.agent.ScreenContext
import dev.metis.agent.domain.agent.baselineAgentOrchestrator
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import dev.metis.agent.domain.contacts.MAX_CONTACT_QUERY_LENGTH
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConversationTurn(
    val id: UUID,
    val text: String,
    val origin: ConversationOrigin,
    val result: AgentResult? = null,
    val outcomes: List<AgentResult> = emptyList(),
    val dismissed: Boolean = false,
    val parts: List<AgentResult> = emptyList(),
)

data class RequestUiState(
    val busy: Boolean = false,
    val result: AgentResult? = null,
    val accepting: Boolean = false,
    val turns: List<ConversationTurn> = emptyList(),
    val activeRequestId: UUID? = null,
    val sessionId: UUID = UUID.randomUUID(),
    val contactLookup: ContactLookupUiState = ContactLookupUiState.Idle,
    val parts: List<AgentResult> = emptyList(),
)

sealed interface ContactLookupUiState {
    data object Idle : ContactLookupUiState
    data object Searching : ContactLookupUiState
    data object NotFound : ContactLookupUiState
    data object Refine : ContactLookupUiState

    class PermissionNeeded(val questionId: UUID, val permanentlyDenied: Boolean,
        val explainPermanentDenial: Boolean) : ContactLookupUiState {
        override fun toString(): String = "PermissionNeeded(redacted)"
    }

    class Denied(val permanentlyDenied: Boolean,
        val explainPermanentDenial: Boolean) : ContactLookupUiState {
        override fun toString(): String = "Denied(redacted)"
    }

    class Ambiguous(val question: String, val questionId: UUID,
        val candidates: List<ContactCandidate>) : ContactLookupUiState {
        override fun toString(): String = "Ambiguous(redacted)"
    }

    class Resolved(val contact: ContactCandidate) : ContactLookupUiState {
        override fun toString(): String = "Resolved(redacted)"
    }
}

/** Requests/results remain in RAM. The composer is the sole current entry into the shared protocol. */
@Suppress("TooManyFunctions", "LongParameterList") // Follow-ups share one in-memory coordinator.
class RequestViewModel(
    private val orchestrator: AgentOrchestrator = baselineAgentOrchestrator(),
    private val clock: Clock = Clock.systemUTC(),
    private val reminderClarification: ReminderClarification? = null,
    private val contactsResolver: ContactsResolver? = null,
    private val contactsPermissionBlocked: () -> Boolean = { false },
    private val explainPermanentContactsDenial: () -> Boolean = { false },
    private val promiseClarification: PromiseClarification? = null,
) : ViewModel() {
    private val state = MutableStateFlow(RequestUiState())
    val uiState = state.asStateFlow()
    private var processing: Job? = null
    private var generation = 0L
    private val clarifications = ClarificationCoordinator<RequestClarificationKind, RequestClarificationPayload>(clock)
    private var contactJob: Job? = null
    private var contactGeneration = 0L
    private val conversation = EnglishConversationState(orchestrator as? ConfirmableAgent, clock)

    fun submit(text: String, destination: ShellDestination, source: InputSource = InputSource.TEXT) {
        val workspace = when (destination) {
            ShellDestination.TODAY -> Workspace.TODAY
            ShellDestination.PLAN -> Workspace.PLAN
            ShellDestination.AGENT -> Workspace.CHAT
            ShellDestination.TIMELINE -> Workspace.ACTIVITY
            ShellDestination.YOU -> Workspace.SETTINGS
        }
        submit(text, ConversationOrigin(workspace), source)
    }

    fun submit(text: String, origin: ConversationOrigin, source: InputSource = InputSource.TEXT) {
        submit(text, origin.screenContext(), origin, source)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount") // Routing and stale-result guards stay together.
    private fun submit(text: String, context: ScreenContext, origin: ConversationOrigin, source: InputSource) {
        if (state.value.busy || text.isBlank() || text.length > ShellViewModel.MAX_DRAFT_LENGTH) return
        val rawRequest = AgentRequest(UUID.randomUUID(), text, source, clock.instant(), context, state.value.sessionId)
        val request = referenceAnswer(rawRequest, origin) ?: return
        if (conversation.handles(request)) {
            submitConversation(request, origin)
            return
        }
        conversation.observe(request)
        val pending = clarifications.current()?.takeIf { it.kind in setOf(
            RequestClarificationKind.REMINDER_TIME, RequestClarificationKind.REMINDER_PERSON,
            RequestClarificationKind.PROMISE) }
        val cancelled = pending != null && text.trim().lowercase(java.util.Locale.ROOT) in
            setOf("cancel", "ignore")
        val answering = pending?.takeIf { !cancelled && when (it.kind) {
            RequestClarificationKind.REMINDER_TIME -> isReminderFollowUpAnswer(request, it.sessionId, it.expiresAt)
            RequestClarificationKind.REMINDER_PERSON -> request.timestamp < it.expiresAt &&
                request.conversationId == it.sessionId &&
                EnglishRules().parse(request).intent == AgentIntent.UNKNOWN
            RequestClarificationKind.PROMISE -> request.timestamp < it.expiresAt &&
                request.conversationId == it.sessionId &&
                EnglishRules().parse(request).intent == AgentIntent.UNKNOWN
            else -> false
        } }?.let {
            clarifications.answer(it.kind, it.originalRequestId, it.questionId, request)
        }
        if (pending != null && answering == null) {
            cancelReminderClarification()
            cancelPromiseClarification()
        }
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        val currentGeneration = ++generation
        state.value = state.value.copy(busy = true, result = null, activeRequestId = request.id,
            turns = state.value.turns.map { it.copy(result = it.result?.withoutTransientContact()) } +
                ConversationTurn(request.id, text, origin))
        processing = viewModelScope.launch {
            val result = try {
                (when {
                    cancelled -> AgentResult(request.id, AgentResultStatus.DENIED,
                        "Clarification cancelled. Nothing was saved.")
                    answering?.kind == RequestClarificationKind.REMINDER_TIME ->
                        requireNotNull(reminderClarification).answerReminder(
                        answering.originalRequestId, answering.questionId, request)
                    answering?.kind == RequestClarificationKind.REMINDER_PERSON -> {
                        val field = (answering.payload as RequestClarificationPayload.ReminderPerson).field
                        if (field == "reminder_contact_permission" &&
                            text.trim().lowercase(java.util.Locale.ROOT) in setOf("skip", "no", "continue"))
                            requireNotNull(reminderClarification).answerReminderContactPermission(
                                answering.originalRequestId, answering.questionId, request, false)
                        else requireNotNull(reminderClarification).answerReminderPerson(
                            answering.originalRequestId, answering.questionId, request)
                    }
                    answering?.kind == RequestClarificationKind.PROMISE ->
                        requireNotNull(promiseClarification).answerPromise(
                            answering.originalRequestId, answering.questionId, request)
                    else -> orchestrator.process(request)
                }).also {
                    currentCoroutineContext().ensureActive()
                    require(it.requestId == request.id)
                }
            } catch (cancelled: CancellationException) {
                if (generation == currentGeneration) state.value = state.value.copy(busy = false)
                throw cancelled
            } catch (_: Exception) {
                AgentResult(request.id, AgentResultStatus.FAILED, "The request could not be processed. Try again.")
            }
            if (generation == currentGeneration) {
                conversation.observeResult(request, result)
                trackActionFollowUp(result, request, answering, pending)
                publish(result, outcome = false)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // Each clarification kind binds its own request and question IDs.
    private fun trackActionFollowUp(result: AgentResult, request: AgentRequest,
        answering: PendingClarification<RequestClarificationKind, RequestClarificationPayload>?,
        pending: PendingClarification<RequestClarificationKind, RequestClarificationPayload>?) {
        val followUp = result.followUp
        val field = followUp?.missingFields?.singleOrNull()
        val kind = when {
            result.status != AgentResultStatus.FOLLOW_UP -> null
            field == "time" && reminderClarification != null -> RequestClarificationKind.REMINDER_TIME
            field in REMINDER_PERSON_FIELDS && reminderClarification != null ->
                RequestClarificationKind.REMINDER_PERSON
            field in PROMISE_FIELDS && promiseClarification != null -> RequestClarificationKind.PROMISE
            else -> null
        }
        if (kind == null || followUp == null || field == null) {
            if (pending?.kind in ACTION_KINDS) clarifications.clear()
            return
        }
        val payload = when (kind) {
            RequestClarificationKind.REMINDER_TIME -> RequestClarificationPayload.ReminderTime
            RequestClarificationKind.REMINDER_PERSON -> RequestClarificationPayload.ReminderPerson(field)
            RequestClarificationKind.PROMISE -> RequestClarificationPayload.Promise(field)
            else -> return
        }
        if (answering?.kind == kind) {
            val previousField = when (val prior = answering.payload) {
                is RequestClarificationPayload.ReminderPerson -> prior.field
                is RequestClarificationPayload.Promise -> prior.field
                else -> field
            }
            clarifications.replace(answering, answering.copy(
                originalRequestId = if (field == previousField) answering.originalRequestId else request.id,
                questionId = followUp.id, lastAnswerId = request.id, payload = payload))
        } else clarifications.begin(PendingClarification(kind, request.id, followUp.id,
            request.conversationId, request.timestamp, request.timestamp.plusSeconds(FOLLOW_UP_SECONDS), payload))
    }

    /** A choice is a short follow-up answer; the contact name is never inserted into the composer. */
    @Suppress("ReturnCount") // Conversation choices and single-request choices have separate identity guards.
    fun chooseActionPerson(index: Int) {
        if (conversation.active) {
            submit((index + 1).toString(), ConversationOrigin(Workspace.CHAT))
            return
        }
        val result = state.value.result ?: return
        val choices = result.followUp?.suggestedChoices.orEmpty()
        if (result.status != AgentResultStatus.FOLLOW_UP || index !in choices.indices) return
        submit((index + 1).toString(), ConversationOrigin(Workspace.CHAT))
    }

    fun hasPendingActionContactPermission(questionId: UUID): Boolean =
        conversation.hasPermissionQuestion(questionId) || clarifications.current()?.let {
            it.kind in setOf(RequestClarificationKind.REMINDER_PERSON, RequestClarificationKind.PROMISE) &&
                it.questionId == questionId && clock.instant() < it.expiresAt &&
                when (val payload = it.payload) {
                    is RequestClarificationPayload.ReminderPerson -> payload.field ==
                        "reminder_contact_permission"
                    is RequestClarificationPayload.Promise -> payload.field == "contact_permission"
                    else -> false
                }
        } == true

    @Suppress("ReturnCount") // Stale permission callbacks must not resume an action.
    fun onActionContactPermissionResult(questionId: UUID, granted: Boolean) {
        if (conversation.hasPermissionQuestion(questionId) && !state.value.busy) {
            updateConversation { conversation.permission(questionId, granted) }
            return
        }
        val current = clarifications.current()?.takeIf {
            it.questionId == questionId && hasPendingActionContactPermission(questionId)
        } ?: return
        if (state.value.busy) return
        val request = AgentRequest(UUID.randomUUID(), "permission result", InputSource.TEXT, clock.instant(),
            conversationId = state.value.sessionId)
        val accepted = clarifications.answer(current.kind, current.originalRequestId, questionId, request) ?: return
        val currentGeneration = ++generation
        state.value = state.value.copy(busy = true)
        processing = viewModelScope.launch {
            val result = try {
                when (current.kind) {
                    RequestClarificationKind.REMINDER_PERSON -> requireNotNull(reminderClarification)
                        .answerReminderContactPermission(current.originalRequestId, questionId, request, granted)
                    RequestClarificationKind.PROMISE -> requireNotNull(promiseClarification)
                        .answerPromiseContactPermission(current.originalRequestId, questionId, request, granted)
                    else -> return@launch
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AgentResult(request.id, AgentResultStatus.FAILED,
                "The request could not be processed. Try again.") }
            if (generation == currentGeneration) {
                trackActionFollowUp(result, request, accepted, current)
                publish(result, outcome = false)
            }
        }
    }

    fun dismiss() {
        if (state.value.accepting) return
        conversation.reset()
        cancelReminderClarification()
        cancelPromiseClarification()
        cancelContactLookup()
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        generation++
        processing?.cancel()
        processing = null
        state.value = RequestUiState()
    }

    fun reject() {
        if (state.value.busy) return
        if (conversation.active) {
            publishConversation(conversation.reject())
            return
        }
        cancelReminderClarification()
        cancelPromiseClarification()
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        val active = state.value.activeRequestId
        state.value = state.value.copy(result = null, activeRequestId = null,
            turns = state.value.turns.map { if (it.id == active)
                it.copy(result = it.result?.withoutTransientContact(), dismissed = true) else it })
    }

    fun cancelReminderClarification() {
        cancelConversationPending()
        restoreReferenceQuestion(setOf(RequestClarificationKind.REMINDER_TIME,
            RequestClarificationKind.REMINDER_PERSON))
        if (clarifications.current()?.kind !in setOf(RequestClarificationKind.REMINDER_TIME,
                RequestClarificationKind.REMINDER_PERSON)) return
        clarifications.clear()
        reminderClarification?.cancelReminderClarification()
        generation++
        processing?.cancel()
        processing = null
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        if (state.value.result?.status == AgentResultStatus.FOLLOW_UP || state.value.busy) {
            val active = state.value.activeRequestId
            state.value = state.value.copy(busy = false, result = null, activeRequestId = null,
                turns = state.value.turns.map { if (it.id == active)
                    it.copy(result = it.result?.withoutTransientContact(), dismissed = true) else it })
        }
    }

    fun cancelPromiseClarification() {
        cancelConversationPending()
        restoreReferenceQuestion(setOf(RequestClarificationKind.PROMISE))
        if (clarifications.current()?.kind != RequestClarificationKind.PROMISE) return
        clarifications.clear()
        promiseClarification?.cancelPromiseClarification()
        generation++
        processing?.cancel()
        processing = null
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        if (state.value.result?.status == AgentResultStatus.FOLLOW_UP || state.value.busy) {
            val active = state.value.activeRequestId
            state.value = state.value.copy(busy = false, result = null, activeRequestId = null,
                turns = state.value.turns.map { if (it.id == active)
                    it.copy(result = it.result?.withoutTransientContact(), dismissed = true) else it })
        }
    }

    fun findContact(query: String) {
        val resolver = contactsResolver ?: return
        val cleaned = query.trim()
        if (cleaned.isBlank() || cleaned.length > MAX_CONTACT_QUERY_LENGTH || state.value.busy) return
        cancelReminderClarification()
        cancelPromiseClarification()
        cancelContactLookup()
        val originalRequestId = UUID.randomUUID()
        val startedAt = clock.instant()
        val currentGeneration = ++contactGeneration
        state.value = state.value.copy(contactLookup = ContactLookupUiState.Searching)
        contactJob = viewModelScope.launch {
            val resolution = try { resolver.resolve(cleaned) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ContactResolution.NotFound }
            if (contactGeneration != currentGeneration) return@launch
            val next = when (resolution) {
                is ContactResolution.Resolved -> ContactLookupUiState.Resolved(resolution.contact)
                is ContactResolution.Ambiguous -> {
                    if (resolution.requiresRefinement) ContactLookupUiState.Refine
                    else {
                        val questionId = UUID.randomUUID()
                        clarifications.begin(PendingClarification(RequestClarificationKind.CONTACT_SELECTION,
                            originalRequestId, questionId, state.value.sessionId, startedAt,
                            clock.instant().plusSeconds(FOLLOW_UP_SECONDS),
                            RequestClarificationPayload.Candidates(resolution.candidates)))
                        ContactLookupUiState.Ambiguous("Which $cleaned?", questionId,
                            resolution.candidates)
                    }
                }
                ContactResolution.NotFound -> ContactLookupUiState.NotFound
                ContactResolution.PermissionNeeded -> {
                    val questionId = UUID.randomUUID()
                    clarifications.begin(PendingClarification(RequestClarificationKind.CONTACT_PERMISSION,
                        originalRequestId, questionId, state.value.sessionId, startedAt,
                        clock.instant().plusSeconds(FOLLOW_UP_SECONDS),
                        RequestClarificationPayload.Permission(cleaned)))
                    ContactLookupUiState.PermissionNeeded(questionId, contactsPermissionBlocked(),
                        explainPermanentContactsDenial())
                }
            }
            state.value = state.value.copy(contactLookup = next)
        }
    }

    @Suppress("ReturnCount") // Stale or forged selections fail closed at each boundary.
    fun answerContact(questionId: UUID, index: Int) {
        val current = clarifications.current()?.takeIf {
            it.kind == RequestClarificationKind.CONTACT_SELECTION && it.questionId == questionId
        } ?: return
        val payload = current.payload as? RequestClarificationPayload.Candidates ?: return
        val candidate = payload.options.getOrNull(index) ?: return
        val answer = AgentRequest(UUID.randomUUID(), "selection", InputSource.TEXT,
            clock.instant(), conversationId = state.value.sessionId)
        val accepted = clarifications.answer(RequestClarificationKind.CONTACT_SELECTION,
            current.originalRequestId, questionId, answer) ?: return
        if (clarifications.consume(accepted)) {
            state.value = state.value.copy(contactLookup = ContactLookupUiState.Resolved(candidate))
        }
    }

    @Suppress("ReturnCount") // Late permission results cannot start another lookup.
    fun onContactPermissionResult(questionId: UUID, granted: Boolean, permanentlyDenied: Boolean) {
        val current = clarifications.current()?.takeIf {
            it.kind == RequestClarificationKind.CONTACT_PERMISSION && it.questionId == questionId
        } ?: return
        val answer = AgentRequest(UUID.randomUUID(), "permission result", InputSource.TEXT,
            clock.instant(), conversationId = state.value.sessionId)
        val accepted = clarifications.answer(RequestClarificationKind.CONTACT_PERMISSION,
            current.originalRequestId, questionId, answer) ?: return
        if (!clarifications.consume(accepted)) return
        val payload = accepted.payload as? RequestClarificationPayload.Permission ?: return
        if (granted) findContact(payload.query)
        else state.value = state.value.copy(contactLookup = ContactLookupUiState.Denied(
            permanentlyDenied, permanentlyDenied && explainPermanentContactsDenial()))
    }

    fun hasPendingContactPermission(questionId: UUID): Boolean =
        clarifications.current()?.let {
            it.kind == RequestClarificationKind.CONTACT_PERMISSION && it.questionId == questionId &&
                clock.instant() < it.expiresAt
        } == true

    fun cancelContactLookup() {
        contactGeneration++
        contactJob?.cancel()
        contactJob = null
        if (clarifications.current()?.kind in CONTACT_KINDS) clarifications.clear()
        state.value = state.value.copy(contactLookup = ContactLookupUiState.Idle)
    }

    private fun publish(result: AgentResult, outcome: Boolean) {
        val active = state.value.activeRequestId
        state.value = state.value.copy(busy = false, accepting = false, result = result,
            turns = state.value.turns.map {
                if (it.id != active) it else if (outcome) it.copy(
                    result = it.result?.withoutTransientContact(), outcomes = it.outcomes + result)
                else it.copy(result = result)
            })
    }

    @Suppress("ReturnCount") // Only contact-bearing prior results are scrubbed.
    private fun AgentResult.withoutTransientContact(): AgentResult {
        val action = proposals.singleOrNull()?.action
        if ((action as? CreatePromiseAction)?.contactLookupKey != null ||
            (action as? CreateReminderAction)?.contactLookupKey != null)
            return AgentResult(requestId, AgentResultStatus.ANSWER, "Contact proposal closed.")
        val question = followUp ?: return this
        if (question.suggestedChoices.isEmpty()) return this
        return AgentResult(requestId, AgentResultStatus.FOLLOW_UP, message,
            followUp = FollowUpQuestion(question.id, question.requestId, question.question,
                question.missingFields))
    }

    @Suppress("ReturnCount") // Each card must match a currently pending canonical proposal.
    fun accept(proposal: ActionProposal) {
        if (conversation.active) {
            if (!state.value.busy && conversation.results.any { it.proposals.any { value -> value === proposal } })
                updateConversation(accepting = true) { conversation.accept(proposal) }
            return
        }
        val agent = orchestrator as? ConfirmableAgent ?: return
        if (state.value.busy || state.value.result?.proposals?.singleOrNull() !== proposal) return
        val currentGeneration = generation
        state.value = state.value.copy(busy = true, accepting = true)
        processing = viewModelScope.launch {
            val result = try { agent.accept(proposal) } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AgentResult(proposal.requestId, AgentResultStatus.FAILED,
                    "The accepted action could not be verified. Review Activity before retrying.")
            }
            if (generation == currentGeneration) publish(result, outcome = true)
        }
    }

    @Suppress("ReturnCount") // Each receipt must belong to a current result before routing undo.
    fun undo(receipt: ActionReceipt) {
        if (conversation.active) {
            if (!state.value.busy && conversation.results.any {
                    it.completedActions.any { value -> value === receipt }
                })
                updateConversation(accepting = true) { conversation.undo(receipt) }
            return
        }
        val agent = orchestrator as? ConfirmableAgent ?: return
        if (state.value.busy || state.value.result?.completedActions?.singleOrNull() !== receipt) return
        val currentGeneration = generation
        state.value = state.value.copy(busy = true, accepting = true)
        processing = viewModelScope.launch {
            val result = try { agent.undo(receipt) } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { UndoResult.FAILED }
            val message = if (result == UndoResult.UNDONE) {
                if (receipt.actionType == ActionType.CREATE_REMINDER) "Reminder registration cancelled and verified."
                else "Task action undone and verified locally."
            } else "This action could not be undone. Changes, delivery or current policy may prevent undo."
            if (generation == currentGeneration) publish(
                AgentResult(receipt.requestId, AgentResultStatus.ANSWER, message), outcome = true)
        }
    }

    private fun submitConversation(request: AgentRequest, origin: ConversationOrigin) {
        clarifications.clear()
        reminderClarification?.cancelReminderClarification()
        promiseClarification?.cancelPromiseClarification()
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        state.value = state.value.copy(activeRequestId = request.id, result = null, parts = emptyList(),
            turns = state.value.turns.map { it.copy(result = it.result?.withoutTransientContact(),
                parts = it.parts.map { part -> part.withoutTransientContact() }) } +
                ConversationTurn(request.id, request.text, origin))
        updateConversation { conversation.process(request) }
    }

    @Suppress("ReturnCount") // A reference answer must preserve the existing action question and expiry.
    private fun referenceAnswer(request: AgentRequest, origin: ConversationOrigin): AgentRequest? {
        val current = clarifications.current() ?: return request
        if (current.kind == RequestClarificationKind.REFERENCE) {
            val payload = current.payload as RequestClarificationPayload.Reference
            if (EnglishRules().parse(request).intent != AgentIntent.UNKNOWN ||
                request.text.trim().lowercase(java.util.Locale.ROOT) in setOf("cancel", "ignore")) {
                clarifications.begin(payload.previous)
                return request
            }
            val live = clarifications.answer(current.kind, current.originalRequestId, current.questionId, request)
            val refreshed = conversation.reference(payload.question.request.copy(timestamp = request.timestamp))
            val choicesCurrent = payload.question.choices.isEmpty() || refreshed?.choices == payload.question.choices
            val resolved = if (live != null && choicesCurrent)
                EnglishConversationRules.resolve(payload.question, request.text) else null
            if (resolved != null) {
                clarifications.begin(payload.previous)
                return request.copy(text = resolved.text)
            }
            showReferenceAnswer(request, origin, refreshed ?: payload.question, payload.previous)
            return null
        }
        if (current.kind !in setOf(RequestClarificationKind.PROMISE, RequestClarificationKind.REMINDER_PERSON) ||
            !EnglishConversationRules.isBareReference(request.text)) return request
        val question = conversation.reference(request) ?: return request
        if (question.choices.size == 1) return requireNotNull(EnglishConversationRules.resolve(question, "1"))
        showReferenceAnswer(request, origin, question, current)
        return null
    }

    private fun showReferenceAnswer(request: AgentRequest, origin: ConversationOrigin, question: ReferenceQuestion,
        previous: PendingClarification<RequestClarificationKind, RequestClarificationPayload>) {
        val expired = clock.instant() >= previous.expiresAt
        val followUp = if (expired) null else FollowUpQuestion(UUID.randomUUID(), request.id, question.question,
            setOf(if (question.person) "reference_person" else "reference_item"), question.choices)
        val result = AgentResult(request.id,
            if (expired) AgentResultStatus.DENIED else AgentResultStatus.FOLLOW_UP,
            if (expired) "This clarification has expired. Submit a fresh request." else question.question,
            followUp = followUp)
        if (followUp == null) {
            clarifications.clear()
            reminderClarification?.cancelReminderClarification()
            promiseClarification?.cancelPromiseClarification()
        } else clarifications.begin(PendingClarification(RequestClarificationKind.REFERENCE, request.id,
            followUp.id, requireNotNull(request.conversationId), request.timestamp, previous.expiresAt,
            RequestClarificationPayload.Reference(question, previous)))
        conversation.observe(request)
        state.value = state.value.copy(result = result, activeRequestId = request.id,
            turns = state.value.turns + ConversationTurn(request.id, request.text, origin, result))
    }

    private fun restoreReferenceQuestion(kinds: Set<RequestClarificationKind>) {
        val payload = clarifications.current()?.payload as? RequestClarificationPayload.Reference ?: return
        if (payload.previous.kind in kinds) clarifications.begin(payload.previous)
    }

    private fun cancelConversationPending() {
        if (!conversation.active || state.value.accepting) return
        conversation.dropPending()
        generation++
        processing?.cancel()
        processing = null
        state.value = state.value.copy(busy = false, result = null, parts = emptyList(), activeRequestId = null)
    }

    private fun updateConversation(accepting: Boolean = false, operation: suspend () -> List<AgentResult>) {
        val currentGeneration = ++generation
        state.value = state.value.copy(busy = true, accepting = accepting)
        processing = viewModelScope.launch {
            val results = try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                conversation.dropPending()
                listOf(AgentResult(requireNotNull(state.value.activeRequestId), AgentResultStatus.FAILED,
                    if (accepting) "The accepted action could not be verified. Review Activity before retrying."
                    else "The request could not be processed. Try again."))
            }
            if (generation == currentGeneration) publishConversation(results)
        }
    }

    private fun publishConversation(results: List<AgentResult>) {
        val first = results.firstOrNull()
        val rest = results.drop(1)
        state.value = state.value.copy(busy = false, accepting = false, result = first, parts = rest,
            turns = state.value.turns.map {
                if (it.id == state.value.activeRequestId) it.copy(result = first, parts = rest) else it
            })
    }

    override fun onCleared() {
        conversation.reset()
        super.onCleared()
    }

    private enum class RequestClarificationKind {
        REMINDER_TIME, REMINDER_PERSON, PROMISE, CONTACT_SELECTION, CONTACT_PERMISSION, REFERENCE
    }

    private sealed interface RequestClarificationPayload {
        data object ReminderTime : RequestClarificationPayload
        data class ReminderPerson(val field: String) : RequestClarificationPayload
        data class Promise(val field: String) : RequestClarificationPayload
        class Reference(val question: ReferenceQuestion,
            val previous: PendingClarification<RequestClarificationKind, RequestClarificationPayload>) :
            RequestClarificationPayload {
            override fun toString() = "ReferenceAnswer(redacted)"
        }

        class Permission(val query: String) : RequestClarificationPayload {
            override fun toString(): String = "Permission(redacted)"
        }

        class Candidates(val options: List<ContactCandidate>) : RequestClarificationPayload {
            override fun toString(): String = "Candidates(redacted)"
        }
    }

    private companion object {
        const val FOLLOW_UP_SECONDS = 300L
        val PROMISE_FIELDS = setOf("person", "description", "selection", "contact_refine",
            "contact_permission")
        val REMINDER_PERSON_FIELDS = setOf("reminder_contact_permission", "reminder_contact_choice",
            "reminder_contact_refine")
        val ACTION_KINDS = setOf(RequestClarificationKind.REMINDER_TIME,
            RequestClarificationKind.REMINDER_PERSON, RequestClarificationKind.PROMISE)
        val CONTACT_KINDS = setOf(RequestClarificationKind.CONTACT_SELECTION,
            RequestClarificationKind.CONTACT_PERMISSION)
    }
}

internal fun isReminderFollowUpAnswer(request: AgentRequest, conversationId: UUID?, expiresAt: Instant): Boolean =
    request.timestamp < expiresAt && request.conversationId == conversationId &&
        EnglishRules().parse(request).intent == AgentIntent.UNKNOWN &&
        (request.text.trim().none(Char::isWhitespace) ||
            SHORT_TIME_ANSWER.matches(request.text.trim()))

private val SHORT_TIME_ANSWER = Regex("(?:at\\s+)?\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?",
    RegexOption.IGNORE_CASE)
