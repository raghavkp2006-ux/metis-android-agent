package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.ReminderPlatform
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class ReminderActionCancellation(private val records: ReminderActionRecords,
    private val platform: ReminderPlatform) {
    suspend fun undo(receipt: ActionReceipt): UndoResult = withContext(Dispatchers.IO) {
        records.database.withTransaction {
            if (receipt.actionType != ActionType.CREATE_REMINDER || receipt.outcome.status != ActionStatus.SUCCEEDED) {
                return@withTransaction UndoResult.NOT_SUPPORTED
            }
            val run = records.load(receipt.identity.id)
            val undoId = ReminderActionEncoding.undoId(receipt.identity.id)
            if (records.database.actionRun().find(undoId.toString()) != null) {
                return@withTransaction UndoResult.UNDONE
            }
            if (run.safeErrorCode == "PERSONAL_DATA_REMOVED") return@withTransaction UndoResult.NOT_SUPPORTED
            if (!TaskReceiptBinding.matches(ReminderActionEncoding.receipt(run), receipt)) {
                return@withTransaction UndoResult.CONFLICT
            }
            cancelLocked(run)
        }
    }

    suspend fun cancel(actionId: UUID): UndoResult = withContext(Dispatchers.IO) {
        records.database.withTransaction { cancelLocked(records.load(actionId)) }
    }

    @Suppress("ReturnCount") // Replay and missing-record guards precede the cancellable state check.
    private suspend fun cancelLocked(run: SavedActionRun): UndoResult {
        ReminderActionEncoding.data(run)
        val id = UUID.fromString(run.metadata.id)
        val undoId = ReminderActionEncoding.undoId(id)
        if (records.database.actionRun().find(undoId.toString()) != null ||
            run.status == "CANCELLED") return UndoResult.UNDONE
        val reminder = run.entityId?.let { records.reminder(it) } ?: return UndoResult.CONFLICT
        val expected = run.receipt?.let { JSONObject(it).getLong("revision") } ?: 0L
        val unchanged = records.sameReminder(run, reminder) && reminder.metadata.revision == expected
        return if (!unchanged || reminder.schedulingState !in setOf("PENDING", "SCHEDULED")) UndoResult.CONFLICT else {
            platform.cancel(UUID.fromString(reminder.metadata.id))
            records.repository.foundation.reminder.save(reminder.copy(schedulingState = "CANCELLED"))
            val at = maxOf(run.startedAt, records.clock.millis())
            if (run.status == "PENDING") {
                val cancelled = run.copy(status = "CANCELLED", finishedAt = at)
                records.repository.foundation.actionRun.save(cancelled)
                records.audit(cancelled, "DENY", "The user cancelled this pending reminder before scheduling.")
            } else {
                val undo = SavedActionRun(run.requestId, UUID.randomUUID().toString(), undoId.toString(),
                    "CANCEL_REMINDER",
                    JSONObject().put("operation", "CANCEL_ACCEPTED_REMINDER_V1").put("actionId",
                        run.metadata.id).toString(),
                    "MEDIUM", "SUCCEEDED", at, "VERIFIED_PLATFORM", finishedAt = at,
                    receipt = JSONObject().put("id", undoId).toString(), entityType = "REMINDER",
                    entityId = reminder.metadata.id, metadata = RecordMetadata(id = undoId.toString(), createdAt = at))
                records.repository.foundation.actionRun.save(undo)
                records.audit(undo, "ALLOW",
                    "Reminder registration was cancelled and verified; past delivery cannot be undone.")
            }
            UndoResult.UNDONE
        }
    }
}
