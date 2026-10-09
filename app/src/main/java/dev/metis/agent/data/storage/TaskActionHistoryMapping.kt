package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.storage.SavedActionRun
import java.util.UUID
import org.json.JSONObject

internal object TaskActionHistoryMapping {
    fun entries(records: List<SavedActionRun>): List<TaskActionHistoryEntry> {
        val ids = records.map { it.metadata.id }.toSet()
        return records.filter { it.actionType == "TASK" && (
            it.safeErrorCode == "PERSONAL_DATA_REMOVED" || JSONObject(it.payload).optString("operation") in
                setOf(TaskActionEncoding.OPERATION, TaskCompletionEncoding.OPERATION,
                    TaskEditEncoding.OPERATION, TaskDeletion.OPERATION)) }.map { run ->
            val id = UUID.fromString(run.metadata.id)
            if (JSONObject(run.payload).optString("operation") == TaskDeletion.OPERATION) {
                TaskActionHistoryEntry(id, "Task deletion verified · no undo", ActionStatus.SUCCEEDED,
                    operation = "Task deletion")
            } else if (run.safeErrorCode == "PERSONAL_DATA_REMOVED") {
                TaskActionHistoryEntry(id, "Personal task data removed", ActionStatus.UNKNOWN)
            } else {
                val undone = TaskActionEncoding.undoId(id).toString() in ids
                TaskActionHistoryEntry(id, TaskMutationEncoding.data(run).getString("title"),
                    ActionStatus.valueOf(run.status),
                    if (run.status == "SUCCEEDED" && !undone) TaskActionEncoding.receipt(run) else null,
                    completion = TaskMutationEncoding.isCompletion(run), undone = undone,
                    operation = if (JSONObject(run.payload).optString("operation") == TaskEditEncoding.OPERATION)
                        "Task ${JSONObject(run.payload).getString("kind").lowercase(java.util.Locale.ROOT)}"
                    else if (TaskMutationEncoding.isCompletion(run)) "Task completion" else "Task creation")
            }
        }
    }
}
