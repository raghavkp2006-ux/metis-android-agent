package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.UndoCapability
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.SavedActionRun
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

/** Versioned, closed payload for plain task creation only. Not a general JSON action dispatcher. */
internal object TaskActionEncoding {
    const val OPERATION = "CONFIRMED_TASK_CREATE_V1"
    fun payload(proposal: ActionProposal, confirmation: UUID): String {
        val action = proposal.action as TaskAction
        val create = action.mutation as TaskMutation.Create
        require(create.title.length <= MAX_TITLE && create.title.none { it.isISOControl() })
        require(plainCreate(create))
        require(proposal.risk == dev.metis.agent.domain.agent.RiskLevel.R1)
        return JSONObject().put("operation", OPERATION).put("title", create.title)
            .put("reason", proposal.reason).put("risk", proposal.risk.name)
            .put("taskId", taskId(action.identity.id)).put("createdAt", proposal.createdAt.toEpochMilli())
            .put("expiresAt", proposal.expiresAt.toEpochMilli()).put("confirmationId", confirmation).toString()
    }

    fun data(run: SavedActionRun): JSONObject {
        require(run.actionType == "TASK" && run.safeErrorCode != "PERSONAL_DATA_REMOVED")
        val json = JSONObject(run.payload)
        require(json.getString("operation") == OPERATION)
        require(json.getString("risk") == "R1" && run.risk == "MEDIUM")
        require(json.getString("taskId") == taskId(UUID.fromString(run.metadata.id)).toString())
        val title = json.getString("title")
        require(title.isNotBlank() && title.length <= MAX_TITLE && title.none { it.isISOControl() })
        UUID.fromString(json.getString("confirmationId"))
        require(json.getLong("createdAt") <= run.startedAt && run.startedAt < json.getLong("expiresAt"))
        require(Math.subtractExact(json.getLong("expiresAt"), json.getLong("createdAt")) in 1..MAX_ACCEPTANCE_MILLIS)
        return json
    }

    @Suppress("ReturnCount") // Type guards precede binding every immutable proposal field.
    fun matches(run: SavedActionRun, proposal: ActionProposal): Boolean {
        val action = proposal.action as? TaskAction ?: return false
        val create = action.mutation as? TaskMutation.Create ?: return false
        val data = data(run)
        return matchesIdentity(run, proposal) &&
            data.getString("reason") == proposal.reason && data.getString("risk") == proposal.risk.name &&
            data.getString("title") == create.title && data.getLong("createdAt") == proposal.createdAt.toEpochMilli() &&
            data.getLong("expiresAt") == proposal.expiresAt.toEpochMilli() && plainCreate(create)
    }

    private fun matchesIdentity(run: SavedActionRun, proposal: ActionProposal) =
        run.metadata.id == proposal.action.identity.id.toString() && run.requestId == proposal.requestId.toString() &&
            run.proposalId == proposal.id.toString() &&
            run.idempotencyKey == proposal.action.identity.idempotencyKey.toString()

    private fun plainCreate(create: TaskMutation.Create) = create.due == null && create.priority == 0 &&
        create.projectId == null && create.goalId == null

    fun identity(run: SavedActionRun) = ActionIdentity(UUID.fromString(run.metadata.id),
        UUID.fromString(run.requestId), UUID.fromString(run.idempotencyKey))

    fun receipt(run: SavedActionRun): ActionReceipt {
        val success = run.status == "SUCCEEDED"
        val receipt = run.receipt?.let(::JSONObject)
        return ActionReceipt(receipt?.getString("id")?.let(UUID::fromString) ?: UUID.fromString(run.metadata.id),
            identity(run), ActionType.TASK,
            ReceiptOutcome(ActionStatus.valueOf(run.status), Instant.ofEpochMilli(run.startedAt),
                run.finishedAt?.let(Instant::ofEpochMilli),
                if (success) OutcomeVerification.VERIFIED_LOCAL else OutcomeVerification.UNVERIFIED,
                if (success) UndoCapability.LOCAL_REVISION_CHECKED else UndoCapability.NOT_SUPPORTED,
                run.safeErrorCode?.let { SafeErrorCode.valueOf(it) }),
            if (success) "Task saved and verified on this device." else "The accepted task was not completed.",
            affectedEntities = if (success) listOf(EntityReference(MemoryEntityType.TASK,
                UUID.fromString(requireNotNull(run.entityId)))) else emptyList())
    }

    fun taskId(actionId: UUID): UUID = UUID.nameUUIDFromBytes("task:$actionId".toByteArray(Charsets.UTF_8))
    fun undoId(actionId: UUID): UUID = UUID.nameUUIDFromBytes("undo-task:$actionId".toByteArray(Charsets.UTF_8))
    private const val MAX_TITLE = 500
    private const val MAX_ACCEPTANCE_MILLIS = 300_000L
}

internal object TaskReceiptBinding {
    fun undoable(receipt: ActionReceipt) = receipt.actionType == ActionType.TASK &&
        receipt.outcome.status == ActionStatus.SUCCEEDED &&
        receipt.outcome.undo == UndoCapability.LOCAL_REVISION_CHECKED

    fun matches(stored: ActionReceipt, supplied: ActionReceipt) = stored.id == supplied.id &&
        stored.identity == supplied.identity && stored.outcome == supplied.outcome &&
        stored.affectedEntities == supplied.affectedEntities
}
