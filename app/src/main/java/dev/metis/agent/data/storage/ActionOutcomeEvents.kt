package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedEvent
import java.util.UUID
import org.json.JSONObject

/** Called inside the action write transaction. Pending reservations are never successful events. */
internal class ActionOutcomeEvents(private val database: PersonalDatabase, private val codec: RecordCodec) {
    suspend fun append(run: SavedActionRun) {
        if (run.safeErrorCode == "PERSONAL_DATA_REMOVED" || run.status !in OUTCOMES) return
        val data = JSONObject(run.payload)
        val operation = data.optString("operation")
        val success = success(operation, data)
        if (success != null) appendOutcome(run, data, operation, success)
    }

    private suspend fun appendOutcome(run: SavedActionRun, data: JSONObject, operation: String,
        success: Pair<String, String>) {
        val delivery = operation in setOf("REMINDER_DELIVERY_V1", "REMINDER_UNAVAILABLE_V1")
        val verified = run.status == "SUCCEEDED"
        val type = if (verified) success.first else if (delivery) "REMINDER_DELIVERY_FAILED" else "ACTION_FAILED"
        val id = eventId(run.metadata.id, run.status)
        if (database.event().find(id) != null) return
        val at = run.finishedAt ?: run.startedAt
        val payload = JSONObject().put("version", 1).put("summary", summary(run, success.second, delivery))
            .put("status", run.status).put("verification", run.verification).put("operation", operation)
        data.optString("title").takeIf { it.isNotBlank() }?.let { payload.put("title", it) }
        run.safeErrorCode?.let { payload.put("safeErrorCode", it) }
        // Entity content is owned through actionId. This history must not itself prevent task undo/deletion.
        val source = when (operation) {
            "REMINDER_DELIVERY_V1" -> "WORKER"
            "REMINDER_UNAVAILABLE_V1" -> "ANDROID"
            else -> "AGENT"
        }
        val event = SavedEvent(type, source, at, NORMAL_IMPORTANCE, 1,
            payloadMetadata = payload.toString(), requestId = run.requestId, actionId = run.metadata.id,
            metadata = RecordMetadata(id = id, createdAt = at))
        database.event().insert(EventCodec(codec).encode(event, StoredMetadata(id, at, at, 0)))
    }

    private fun summary(run: SavedActionRun, success: String, delivery: Boolean): String = when {
        run.status == "SUCCEEDED" -> success
        run.status == "CANCELLED" -> "Accepted action cancelled before completion; no success is claimed."
        delivery -> "Notification delivery denied or uncertain; posting is unverified."
        else -> "Action failed or its outcome is uncertain; no success is claimed."
    }

    private fun success(operation: String, data: JSONObject): Pair<String, String>? = when (operation) {
        PlanEncoding.OPERATION -> "PLAN_ACCEPTED" to "Plan blocks accepted and saved locally; no reminders scheduled."
        TaskActionEncoding.OPERATION -> "TASK_CREATED" to "Task creation verified on this device."
        TaskCompletionEncoding.OPERATION -> "TASK_COMPLETED" to "Task completion verified on this device."
        TaskEditEncoding.OPERATION -> if (data.optString("kind") == "POSTPONE") {
            "TASK_POSTPONED" to "Task postponement verified on this device."
        } else "TASK_UPDATED" to "Task update verified on this device."
        TaskDeletion.OPERATION -> "TASK_DELETED" to "Task deletion verified; personal task content removed."
        "UNDO_${TaskActionEncoding.OPERATION}" -> "TASK_DELETED" to "Task creation undone and absence verified."
        "UNDO_${TaskCompletionEncoding.OPERATION}", "UNDO_${TaskEditEncoding.OPERATION}" ->
            "TASK_UPDATED" to "Task action undone and restoration verified."
        ReminderActionEncoding.OPERATION -> "REMINDER_CREATED" to
            "Approximate reminder registration verified. Delivery may be late; posting is not yet proven."
        "CANCEL_ACCEPTED_REMINDER_V1" -> "REMINDER_CANCELLED" to "Reminder cancellation verified."
        "REMINDER_DELIVERY_V1" -> "REMINDER_TRIGGERED" to
            "An active local notification was verified. This does not prove it was read or that a call was made."
        "REMINDER_UNAVAILABLE_V1" -> "REMINDER_DELIVERY_FAILED" to "Reminder delivery is unavailable."
        else -> null
    }

    companion object {
        private const val NORMAL_IMPORTANCE = 0.5f
        private val OUTCOMES = setOf("SUCCEEDED", "FAILED", "CANCELLED", "UNKNOWN")
        fun eventId(actionId: String, status: String): String =
            UUID.nameUUIDFromBytes("action-outcome-v1:$actionId:$status".toByteArray(Charsets.UTF_8)).toString()
    }
}
