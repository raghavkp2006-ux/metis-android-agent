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
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.ReminderClarification
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
)

data class RequestUiState(
    val busy: Boolean = false,
    val result: AgentResult? = null,
    val accepting: Boolean = false,
    val turns: List<ConversationTurn> = emptyList(),
    val activeRequestId: UUID? = null,
    val sessionId: UUID = UUID.randomUUID(),
    val contactLookup: ContactLookupUiState = ContactLookupUiState.Idle,
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
@Suppress("TooManyFunctions") // Reminder and contact follow-ups share one in-memory coordinator.
class RequestViewModel(
    private val orchestrator: AgentOrchestrator = baselineAgentOrchestrator(),
    private val clock: Clock = Clock.systemUTC(),
    private val reminderClarification: ReminderClarification? = null,
    private val contactsResolver: ContactsResolver? = null,
    private val contactsPermissionBlocked: () -> Boolean = { false },
    private val explainPermanentContactsDenial: () -> Boolean = { false },
) : ViewModel() {
    private val state = MutableStateFlow(RequestUiState())
    val uiState = state.asStateFlow()
    private var processing: Job? = null
    private var generation = 0L
    private val clarifications = ClarificationCoordinator<RequestClarificationKind, RequestClarificationPayload>(clock)
    private var contactJob: Job? = null
    private var contactGeneration = 0L

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

    @Suppress("CyclomaticComplexMethod") // Keeps request routing, cancellation, and stale-result guards together.
    private fun submit(text: String, context: ScreenContext, origin: ConversationOrigin, source: InputSource) {
        if (state.value.busy || text.isBlank() || text.length > ShellViewModel.MAX_DRAFT_LENGTH) return
        val request = AgentRequest(UUID.randomUUID(), text, source, clock.instant(), context, state.value.sessionId)
        val pending = clarifications.current()?.takeIf { it.kind == RequestClarificationKind.REMINDER_TIME }
        val cancelled = pending != null && text.trim().equals("cancel", ignoreCase = true)
        val answering = pending?.takeIf { !cancelled && isReminderFollowUpAnswer(request,
            it.sessionId, it.expiresAt) }?.let {
            clarifications.answer(RequestClarificationKind.REMINDER_TIME, it.originalRequestId,
                it.questionId, request)
        }
        if (pending != null && answering == null) cancelReminderClarification()
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        val currentGeneration = ++generation
        state.value = state.value.copy(busy = true, result = null, activeRequestId = request.id,
            turns = state.value.turns + ConversationTurn(request.id, text, origin))
        processing = viewModelScope.launch {
            val result = try {
                (when {
                    cancelled -> AgentResult(request.id, AgentResultStatus.DENIED,
                        "Reminder clarification cancelled. Nothing was scheduled.")
                    answering != null -> requireNotNull(reminderClarification).answerReminder(
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
                if (result.status == AgentResultStatus.FOLLOW_UP &&
                    result.followUp?.missingFields == setOf("time") && reminderClarification != null) {
                    if (answering == null) clarifications.begin(PendingClarification(
                        RequestClarificationKind.REMINDER_TIME, request.id,
                        requireNotNull(result.followUp).id, request.conversationId,
                        request.timestamp, request.timestamp.plusSeconds(FOLLOW_UP_SECONDS),
                        RequestClarificationPayload.ReminderTime))
                    else clarifications.replace(answering,
                        answering.copy(questionId = requireNotNull(result.followUp).id,
                            lastAnswerId = request.id))
                } else if (pending?.kind == RequestClarificationKind.REMINDER_TIME) clarifications.clear()
                publish(result, outcome = false)
            }
        }
    }

    fun dismiss() {
        if (state.value.accepting) return
        cancelReminderClarification()
        cancelContactLookup()
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        generation++
        processing?.cancel()
        processing = null
        state.value = RequestUiState()
    }

    fun reject() {
        if (state.value.busy) return
        cancelReminderClarification()
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        val active = state.value.activeRequestId
        state.value = state.value.copy(result = null, activeRequestId = null,
            turns = state.value.turns.map { if (it.id == active) it.copy(dismissed = true) else it })
    }

    fun cancelReminderClarification() {
        if (clarifications.current()?.kind != RequestClarificationKind.REMINDER_TIME) return
        clarifications.clear()
        reminderClarification?.cancelReminderClarification()
        generation++
        processing?.cancel()
        processing = null
        (orchestrator as? ConfirmableAgent)?.discardProposal()
        if (state.value.result?.status == AgentResultStatus.FOLLOW_UP || state.value.busy) {
            val active = state.value.activeRequestId
            state.value = state.value.copy(busy = false, result = null, activeRequestId = null,
                turns = state.value.turns.map { if (it.id == active) it.copy(dismissed = true) else it })
        }
    }

    fun findContact(query: String) {
        val resolver = contactsResolver ?: return
        val cleaned = query.trim()
        if (cleaned.isBlank() || cleaned.length > MAX_CONTACT_QUERY_LENGTH || state.value.busy) return
        cancelReminderClarification()
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
                if (it.id != active) it else if (outcome) it.copy(outcomes = it.outcomes + result)
                else it.copy(result = result)
            })
    }

    fun accept(proposal: ActionProposal) {
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

    fun undo(receipt: ActionReceipt) {
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

    private enum class RequestClarificationKind { REMINDER_TIME, CONTACT_SELECTION, CONTACT_PERMISSION }

    private sealed interface RequestClarificationPayload {
        data object ReminderTime : RequestClarificationPayload

        class Permission(val query: String) : RequestClarificationPayload {
            override fun toString(): String = "Permission(redacted)"
        }

        class Candidates(val options: List<ContactCandidate>) : RequestClarificationPayload {
            override fun toString(): String = "Candidates(redacted)"
        }
    }

    private companion object {
        const val FOLLOW_UP_SECONDS = 300L
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
