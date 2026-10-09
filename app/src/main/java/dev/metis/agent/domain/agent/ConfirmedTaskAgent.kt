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
}

/** Storage receives only the canonical, explicitly accepted proposal. No platform effects. */
interface AcceptedTaskStore {
    suspend fun context(request: AgentRequest): ResolvedContext
    suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt
    suspend fun retry(actionId: UUID): ActionReceipt
    suspend fun cancelPending(actionId: UUID)
    suspend fun undo(receipt: ActionReceipt): UndoResult
    fun observeHistory(): Flow<List<TaskActionHistoryEntry>>
}

data class TaskActionHistoryEntry(
    val actionId: UUID,
    val title: String,
    val status: ActionStatus,
    val receipt: ActionReceipt? = null,
)

/** One live proposal, bound by object identity; editing/dismissal invalidates unaccepted proposals. */
class ConfirmedTaskAgent(
    private val reads: AgentReadPort,
    private val store: AcceptedTaskStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ConfirmableAgent {
    private val pending = AtomicReference<ActionProposal?>()

    override suspend fun process(request: AgentRequest): AgentResult {
        discardProposal()
        val capturedZone = zone()
        val rules = EnglishRules { capturedZone }
        val context = object : ContextBuilder {
            override suspend fun snapshot(request: AgentRequest) = store.context(request)
        }
        val language = LanguageSpecialist(reads)
        val specialist = object : AgentSpecialist {
            override suspend fun respond(request: ParsedRequest): SpecialistResult =
                if (request.prediction.intent == AgentIntent.CREATE_TASK) taskSuggestion(request)
                else language.respond(request)
        }
        val result = LocalAgentOrchestrator(rules, rules, context, specialist, ProposalPolicy(),
            "Review the task. Nothing is saved until you accept.").process(request)
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
        return SpecialistResult.Suggestion(TaskAction(identity, TaskMutation.Create(title)),
            "You requested this task. Accept to save it locally; no deadline or reminder is added.")
    }
    private companion object { const val MAX_ACTION_TITLE = 500 }
}

class ActionRejectedException(val code: SafeErrorCode, val userMessage: String) : IllegalStateException(userMessage)

fun taskReceiptResult(receipt: ActionReceipt): AgentResult {
    val succeeded = receipt.outcome.status == ActionStatus.SUCCEEDED
    return AgentResult(receipt.requestId, if (succeeded) AgentResultStatus.ANSWER else AgentResultStatus.FAILED,
        receipt.reason, completedActions = if (succeeded) listOf(receipt) else emptyList())
}
