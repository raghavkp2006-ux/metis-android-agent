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
import dev.metis.agent.domain.agent.ScreenContext
import dev.metis.agent.domain.agent.baselineAgentOrchestrator
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
)

/** Requests/results remain in RAM. The composer is the sole current entry into the shared protocol. */
class RequestViewModel(
    private val orchestrator: AgentOrchestrator = baselineAgentOrchestrator(),
    private val clock: Clock = Clock.systemUTC(),
    private val reminderClarification: ReminderClarification? = null,
) : ViewModel() {
    private val state = MutableStateFlow(RequestUiState())
    val uiState = state.asStateFlow()
    private var processing: Job? = null
    private var generation = 0L
    private var pendingReminder: PendingReminder? = null

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
        val pending = pendingReminder
        val cancelled = pending != null && text.trim().equals("cancel", ignoreCase = true)
        val answering = pending?.takeIf { !cancelled && isReminderFollowUpAnswer(request,
            it.conversationId, it.expiresAt) }
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
                pendingReminder = if (result.status == AgentResultStatus.FOLLOW_UP &&
                    result.followUp?.missingFields == setOf("time") && reminderClarification != null) {
                    if (answering == null) PendingReminder(request.id, requireNotNull(result.followUp).id,
                        request.conversationId, request.timestamp.plusSeconds(FOLLOW_UP_SECONDS))
                    else answering.copy(questionId = requireNotNull(result.followUp).id)
                } else null
                publish(result, outcome = false)
            }
        }
    }

    fun dismiss() {
        if (state.value.accepting) return
        cancelReminderClarification()
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
        if (pendingReminder == null) return
        pendingReminder = null
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

    private data class PendingReminder(val originalRequestId: UUID, val questionId: UUID,
        val conversationId: UUID?, val expiresAt: Instant)

    private companion object { const val FOLLOW_UP_SECONDS = 300L }
}

internal fun isReminderFollowUpAnswer(request: AgentRequest, conversationId: UUID?, expiresAt: Instant): Boolean =
    request.timestamp < expiresAt && request.conversationId == conversationId &&
        EnglishRules().parse(request).intent == AgentIntent.UNKNOWN &&
        (request.text.trim().none(Char::isWhitespace) ||
            SHORT_TIME_ANSWER.matches(request.text.trim()))

private val SHORT_TIME_ANSWER = Regex("\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)", RegexOption.IGNORE_CASE)
