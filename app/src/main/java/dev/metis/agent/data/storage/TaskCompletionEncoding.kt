package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.RevisionTarget
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedTask
import java.util.UUID
import org.json.JSONObject

/** Versioned status-only mutation: no implicit reminder, schedule or recurring-task effects. */
internal object TaskCompletionEncoding {
    const val OPERATION = "CONFIRMED_TASK_COMPLETE_V1"

    fun payload(proposal: ActionProposal, confirmation: UUID, task: SavedTask): String {
        val complete = (proposal.action as TaskAction).mutation as TaskMutation.Complete
        require(complete.target == target(task))
        require(proposal.risk == dev.metis.agent.domain.agent.RiskLevel.R1)
        return JSONObject().put("operation", OPERATION).put("taskId", task.metadata.id)
            .put("revision", task.metadata.revision).put("title", task.title).put("reason", proposal.reason)
            .put("risk", proposal.risk.name).put("createdAt", proposal.createdAt.toEpochMilli())
            .put("expiresAt", proposal.expiresAt.toEpochMilli()).put("confirmationId", confirmation).toString()
    }

    fun data(run: SavedActionRun): JSONObject {
        require(run.actionType == "TASK" && run.safeErrorCode != "PERSONAL_DATA_REMOVED")
        val data = JSONObject(run.payload)
        require(data.getString("operation") == OPERATION && data.getString("risk") == "R1" && run.risk == "MEDIUM")
        require(run.entityType == "TASK" && run.entityId == data.getString("taskId"))
        UUID.fromString(data.getString("taskId"))
        UUID.fromString(data.getString("confirmationId"))
        require(data.getLong("revision") >= 0)
        require(data.getString("title").isNotBlank())
        require(data.getLong("createdAt") <= run.startedAt && run.startedAt < data.getLong("expiresAt"))
        require(Math.subtractExact(data.getLong("expiresAt"), data.getLong("createdAt")) in 1..MAX_ACCEPTANCE_MILLIS)
        return data
    }

    fun matches(run: SavedActionRun, proposal: ActionProposal): Boolean {
        val action = proposal.action as TaskAction
        val complete = action.mutation as TaskMutation.Complete
        val data = data(run)
        return run.metadata.id == action.identity.id.toString() && run.requestId == proposal.requestId.toString() &&
            run.proposalId == proposal.id.toString() &&
            run.idempotencyKey == action.identity.idempotencyKey.toString() &&
            data.getString("reason") == proposal.reason && data.getString("risk") == proposal.risk.name &&
            data.getString("taskId") == complete.target.reference.id.toString() &&
            data.getLong("revision") == complete.target.expectedRevision &&
            data.getLong("createdAt") == proposal.createdAt.toEpochMilli() &&
            data.getLong("expiresAt") == proposal.expiresAt.toEpochMilli()
    }

    fun target(task: SavedTask) = RevisionTarget(
        EntityReference(MemoryEntityType.TASK, UUID.fromString(task.metadata.id)), task.metadata.revision)

    private const val MAX_ACCEPTANCE_MILLIS = 300_000L
}
