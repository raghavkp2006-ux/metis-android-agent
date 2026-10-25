package dev.metis.agent.domain.planner

import dev.metis.agent.domain.agent.AcceptPlanAction
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.SavedSchedule
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PlanDraft(val proposal: ActionProposal, val window: PlanningWindow,
    val blocks: List<SavedSchedule>, val fingerprint: String)
data class PlanPreparation(val result: AgentResult, val draft: PlanDraft? = null)
interface PlannerPort {
    suspend fun prepare(request: AgentRequest): PlanPreparation
    suspend fun accept(draft: PlanDraft, confirmationId: UUID): ActionReceipt
}

class PlannerAgent(private val fallback: ConfirmableAgent, private val planner: PlannerPort) : ConfirmableAgent {
    private val pending = AtomicReference<PlanDraft?>()

    override fun fork(): ConfirmableAgent? = fallback.fork()?.let { PlannerAgent(it, planner) }
    override val reminderFollowUp get() = fallback.reminderFollowUp
    override val promiseFollowUp get() = fallback.promiseFollowUp

    override suspend fun process(request: AgentRequest): AgentResult {
        discardProposal()
        if (EnglishRules().parse(request).intent != AgentIntent.PLAN_DAY) return fallback.process(request)
        return try {
            val preparation = planner.prepare(request)
            currentCoroutineContext().ensureActive()
            pending.set(preparation.draft)
            preparation.result
        } catch (rejected: ActionRejectedException) {
            AgentResult(request.id, if (rejected.code == dev.metis.agent.domain.agent.SafeErrorCode.UNSUPPORTED)
                AgentResultStatus.UNSUPPORTED else AgentResultStatus.DENIED, rejected.userMessage)
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
            AgentResult(request.id, AgentResultStatus.FAILED, "The plan could not be read. No blocks were saved.")
        }
    }

    @Suppress("ReturnCount") // Delegate other actions, then reject inactive canonical proposals.
    override suspend fun accept(proposal: ActionProposal): AgentResult {
        if (proposal.action !is AcceptPlanAction) return fallback.accept(proposal)
        val draft = pending.getAndSet(null)
        if (draft?.proposal !== proposal) return AgentResult(proposal.requestId, AgentResultStatus.DENIED,
            "This plan proposal is no longer active. Request a fresh plan.")
        return try {
            val receipt = withContext(NonCancellable) { planner.accept(draft, UUID.randomUUID()) }
            AgentResult(proposal.requestId, AgentResultStatus.ANSWER, receipt.reason,
                completedActions = listOf(receipt))
        } catch (rejected: ActionRejectedException) {
            AgentResult(proposal.requestId, AgentResultStatus.DENIED, rejected.userMessage)
        } catch (_: Exception) {
            AgentResult(proposal.requestId, AgentResultStatus.FAILED,
                "The accepted plan could not be verified. Check saved blocks in Plan before requesting another.")
        }
    }

    override suspend fun undo(receipt: ActionReceipt) = if (receipt.actionType ==
        dev.metis.agent.domain.agent.ActionType.ACCEPT_PLAN) UndoResult.NOT_SUPPORTED else fallback.undo(receipt)
    override fun discardProposal() { pending.set(null); fallback.discardProposal() }
}
