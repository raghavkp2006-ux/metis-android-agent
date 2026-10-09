package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedTime
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.SchedulingPrecision
import dev.metis.agent.domain.agent.UndoCapability
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.SavedActionRun
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

internal object ReminderActionEncoding {
    const val OPERATION = "CONFIRMED_REMINDER_V1"

    fun payload(proposal: ActionProposal, confirmation: UUID): String {
        val action = proposal.action as CreateReminderAction
        require(action.precision == SchedulingPrecision.APPROXIMATE && action.taskId == null && action.personId == null)
        require(action.title.length <= MAX_TITLE && action.title.none { it.isISOControl() })
        require(proposal.risk == dev.metis.agent.domain.agent.RiskLevel.R1)
        return JSONObject().put("operation", OPERATION).put("title", action.title)
            .put("triggerAt", action.trigger.instant.toEpochMilli()).put("local", action.trigger.local.toString())
            .put("zone", action.trigger.zone.id).put("precision", action.precision.name)
            .put("createdAt", proposal.createdAt.toEpochMilli()).put("expiresAt", proposal.expiresAt.toEpochMilli())
            .put("reason", proposal.reason).put("confirmation", confirmation).toString()
    }

    fun data(run: SavedActionRun): JSONObject {
        require(run.actionType == "CREATE_REMINDER" && run.safeErrorCode != "PERSONAL_DATA_REMOVED")
        val data = JSONObject(run.payload)
        require(data.getString("operation") == OPERATION && data.getString("precision") == "APPROXIMATE")
        require(run.risk == "MEDIUM" && data.getString("title").isNotBlank())
        require(data.getString("title").length <= MAX_TITLE && data.getString("title").none { it.isISOControl() })
        require(run.entityType == "REMINDER" && run.entityId == reminderId(UUID.fromString(run.metadata.id)).toString())
        require(data.getLong("createdAt") <= run.startedAt && run.startedAt < data.getLong("expiresAt"))
        require(Math.subtractExact(data.getLong("expiresAt"), data.getLong("createdAt")) in 1..MAX_ACCEPTANCE_MILLIS)
        UUID.fromString(data.getString("confirmation"))
        ResolvedTime(Instant.ofEpochMilli(data.getLong("triggerAt")), LocalDateTime.parse(data.getString("local")),
            ZoneId.of(data.getString("zone")))
        require(data.getLong("triggerAt") > run.startedAt)
        return data
    }

    fun matches(run: SavedActionRun, proposal: ActionProposal): Boolean {
        val data = data(run)
        return run.metadata.id == proposal.action.identity.id.toString() &&
            run.requestId == proposal.requestId.toString() && run.proposalId == proposal.id.toString() &&
            run.idempotencyKey == proposal.action.identity.idempotencyKey.toString() &&
            run.payload == payload(proposal, UUID.fromString(data.getString("confirmation")))
    }

    fun receipt(run: SavedActionRun): ActionReceipt {
        val success = run.status == "SUCCEEDED"
        return ActionReceipt(run.receipt?.let { UUID.fromString(JSONObject(it).getString("id")) }
            ?: UUID.fromString(run.metadata.id),
            ActionIdentity(UUID.fromString(run.metadata.id), UUID.fromString(run.requestId),
                UUID.fromString(run.idempotencyKey)), ActionType.CREATE_REMINDER,
            ReceiptOutcome(ActionStatus.valueOf(run.status), Instant.ofEpochMilli(run.startedAt),
                run.finishedAt?.let(Instant::ofEpochMilli),
                if (success) OutcomeVerification.VERIFIED_PLATFORM else OutcomeVerification.UNVERIFIED,
                if (success) UndoCapability.LOCAL_REVISION_CHECKED else UndoCapability.NOT_SUPPORTED,
                run.safeErrorCode?.let(SafeErrorCode::valueOf)),
            if (success) "Approximate reminder registered and verified. Delivery may be late; delivery is not yet " +
                "proven."
            else "The reminder was not scheduled. Review reminders in You before retrying.",
            affectedEntities = if (success) listOf(EntityReference(MemoryEntityType.REMINDER,
                UUID.fromString(requireNotNull(run.entityId)))) else emptyList())
    }

    fun reminderId(actionId: UUID) = UUID.nameUUIDFromBytes("reminder:$actionId".toByteArray(Charsets.UTF_8))
    fun undoId(actionId: UUID) = UUID.nameUUIDFromBytes("undo-reminder:$actionId".toByteArray(Charsets.UTF_8))
    fun deliveryId(actionId: UUID) = UUID.nameUUIDFromBytes("delivery:$actionId".toByteArray(Charsets.UTF_8))
    private const val MAX_TITLE = 500
    private const val MAX_ACCEPTANCE_MILLIS = 300_000L
}
