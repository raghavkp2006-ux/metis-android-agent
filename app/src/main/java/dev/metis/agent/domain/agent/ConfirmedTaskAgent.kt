@file:Suppress("MagicNumber", "MaxLineLength")

package dev.metis.agent.domain.agent

import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface ConfirmableAgent : AgentOrchestrator {
    suspend fun accept(proposal: ActionProposal): AgentResult
    fun discardProposal()
    suspend fun undo(receipt: ActionReceipt): UndoResult
    /** A second card uses the same stores and policy, with independent canonical pending state. */
    fun fork(): ConfirmableAgent? = null
    val reminderFollowUp: ReminderClarification? get() = null
    val promiseFollowUp: PromiseClarification? get() = null
    val clarificationRegistry: ClarificationRegistry? get() = null
    val workingMemory: SessionWorkingMemory? get() = null
}

/** Storage receives only the canonical, explicitly accepted proposal. No platform effects. */
interface AcceptedTaskStore {
    suspend fun context(request: AgentRequest): ResolvedContext
    suspend fun resolveCompletion(title: String): CompletionSelection = CompletionSelection.Unavailable
    suspend fun resolveCompletion(title: String, fuzzy: Boolean): CompletionSelection = resolveCompletion(title)
    suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt
    suspend fun retry(actionId: UUID): ActionReceipt
    suspend fun cancelPending(actionId: UUID)
    suspend fun undo(receipt: ActionReceipt): UndoResult
    fun observeHistory(): Flow<List<TaskActionHistoryEntry>>
}

sealed interface CompletionSelection {
    data object Unavailable : CompletionSelection
    data class Selected(val title: String, val target: RevisionTarget) : CompletionSelection
}

data class TaskActionHistoryEntry(
    val actionId: UUID,
    val title: String,
    val status: ActionStatus,
    val receipt: ActionReceipt? = null,
    val completion: Boolean = false,
    val undone: Boolean = false,
    val operation: String = if (completion) "Task completion" else "Task creation",
)

/** One live proposal, bound by object identity; editing/dismissal invalidates unaccepted proposals. */
class ConfirmedTaskAgent(
    private val reads: AgentReadPort,
    private val store: AcceptedTaskStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val localCalendar: Boolean = false,
    private val readCandidate: ReadCandidate = ReadCandidate.V12,
) : ConfirmableAgent {
    private val pending = AtomicReference<ActionProposal?>()

    override fun fork() = ConfirmedTaskAgent(reads, store, zone, localCalendar, readCandidate)

    override suspend fun process(request: AgentRequest): AgentResult {
        discardProposal()
        val capturedZone = zone()
        val rules = EnglishRules(readCandidate) { capturedZone }
        val prediction = rules.classify(request)
        val entities = rules.extract(request)
        var completion: CompletionSelection = CompletionSelection.Unavailable
        val context = object : ContextBuilder {
            override suspend fun snapshot(request: AgentRequest): ResolvedContext {
                if (prediction.intent in setOf(AgentIntent.COMPLETE_TASK, AgentIntent.UPDATE_TASK,
                    AgentIntent.POSTPONE_TASK, AgentIntent.DELETE_TASK)) {
                    completion = store.resolveCompletion(
                        entities.firstOrNull()?.rawValue ?: "",
                        prediction.isUnderstoodByLm
                    )
                }
                val base = store.context(request)
                val scopedRead = readCandidate.scoped && prediction.intent in ReadSafetyV12.intents
                return ResolvedContext(if (scopedRead) request.timestamp else base.now,
                    if (scopedRead) capturedZone else base.zoneId, base.autonomy,
                    if (scopedRead) CapabilitySnapshot(request.timestamp, base.capabilities.available)
                    else base.capabilities,
                    unresolvedFields = base.unresolvedFields,
                    screenContext = base.screenContext, conversationId = base.conversationId,
                    referencedEntities = listOfNotNull((completion as? CompletionSelection.Selected)?.target))
            }
        }
        val language = LanguageSpecialist(reads, localCalendar, readCandidate)
        val specialist = object : AgentSpecialist {
            override suspend fun respond(request: ParsedRequest): SpecialistResult {
                val currentCompletion = completion
                return when (request.prediction.intent) {
                    AgentIntent.CREATE_TASK -> taskSuggestion(request)
                    AgentIntent.COMPLETE_TASK -> {
                        if (request.prediction.isUnderstoodByLm) {
                            if (currentCompletion is CompletionSelection.Selected) {
                                SpecialistResult.Clarification("Did you mean to complete '${currentCompletion.title}'?", setOf("task_confirmation"))
                            } else {
                                SpecialistResult.Clarification("No matching open task found. Which task did you mean?", setOf("task"))
                            }
                        } else completionSuggestion(request, currentCompletion)
                    }
                    AgentIntent.DELETE_TASK -> {
                        if (request.prediction.isUnderstoodByLm) {
                            if (currentCompletion is CompletionSelection.Selected) {
                                SpecialistResult.Clarification("Did you mean to delete '${currentCompletion.title}'?", setOf("task_confirmation"))
                            } else {
                                SpecialistResult.Clarification("No matching open task found. Which task did you mean?", setOf("task"))
                            }
                        } else TaskEditSuggestions.respond(request, currentCompletion)
                    }
                    AgentIntent.UPDATE_TASK, AgentIntent.POSTPONE_TASK -> {
                        if (request.prediction.isUnderstoodByLm) {
                            if (currentCompletion is CompletionSelection.Selected) {
                                SpecialistResult.Clarification("Did you mean to modify '${currentCompletion.title}'?", setOf("task_confirmation"))
                            } else {
                                SpecialistResult.Clarification("No matching open task found. Which task did you mean?", setOf("task"))
                            }
                        } else TaskEditSuggestions.respond(request, currentCompletion)
                    }
                    else -> language.respond(request)
                }
            }
        }
        val result = LocalAgentOrchestrator(rules, rules, context, specialist, ProposalPolicy(),
            "Review the task action. Nothing changes until you accept.", localCalendar, readCandidate).process(request)
        currentCoroutineContext().ensureActive()
        pending.set(result.proposals.singleOrNull())
        return result
    }

    override suspend fun accept(proposal: ActionProposal): AgentResult {
        if (!pending.compareAndSet(proposal, null)) return AgentResult(proposal.requestId,
            AgentResultStatus.DENIED, "This proposal is no longer active. Submit a new request.")
        // After explicit acceptance, commit/recovery cannot be abandoned by dismissing the composer.
        return try {
            val receipt = withContext(NonCancellable) { store.accept(proposal, UUID.randomUUID()) }
            taskReceiptResult(receipt)
        } catch (rejected: ActionRejectedException) {
            AgentResult(proposal.requestId, AgentResultStatus.DENIED, rejected.userMessage)
        }
    }

    override fun discardProposal() { pending.set(null) }
    override suspend fun undo(receipt: ActionReceipt): UndoResult = withContext(NonCancellable) { store.undo(receipt) }

    private fun taskSuggestion(request: ParsedRequest): SpecialistResult {
        val title = request.entities.single { it.type == EntityKind.TITLE }.rawValue
        if (title.length > MAX_ACTION_TITLE || title.any { it.isISOControl() }) {
            return SpecialistResult.Clarification(
                "Use a task title of at most 500 characters without control characters.",
                setOf("title"))
        }
        val identity = ActionIdentity(UUID.randomUUID(), request.request.id, UUID.randomUUID())
        val reason = if (request.prediction.isUnderstoodByLm) {
            "You requested this task. Accept to save it locally. Understood by language model (${(request.prediction.confidence * 100).toInt()}%)."
        } else {
            "You requested this task. Accept to save it locally; no deadline or reminder is added."
        }
        return SpecialistResult.Suggestion(TaskAction(identity, TaskMutation.Create(title)), reason)
    }

    private fun completionSuggestion(request: ParsedRequest, selection: CompletionSelection): SpecialistResult =
        if (selection is CompletionSelection.Selected) SpecialistResult.Suggestion(
            TaskAction(ActionIdentity(UUID.randomUUID(), request.request.id, UUID.randomUUID()),
                TaskMutation.Complete(selection.target)),
            "Mark this task completed: ${selection.title}. Accept to change only its local status; " +
                "reminders and schedule blocks stay as they are.")
        else SpecialistResult.Clarification(
            "Use complete task: followed by the exact title of one open, nonrecurring task. " +
                "No unique supported task was found. Duplicate titles need distinct names.", setOf("task"))
    private companion object { const val MAX_ACTION_TITLE = 500 }
}

class ActionRejectedException(val code: SafeErrorCode, val userMessage: String) : IllegalStateException(userMessage)

fun taskReceiptResult(receipt: ActionReceipt): AgentResult {
    val succeeded = receipt.outcome.status == ActionStatus.SUCCEEDED
    return AgentResult(receipt.requestId, if (succeeded) AgentResultStatus.ANSWER else AgentResultStatus.FAILED,
        receipt.reason, completedActions = if (succeeded) listOf(receipt) else emptyList())
}
