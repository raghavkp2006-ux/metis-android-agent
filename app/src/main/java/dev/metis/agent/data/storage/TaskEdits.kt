package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.TaskStatus
import org.json.JSONObject

internal class TaskEdits(private val database: PersonalDatabase) {
    suspend fun eligible(task: SavedTask, data: JSONObject, at: Long): Boolean =
        task.status == TaskStatus.OPEN && task.recurrenceRule == null &&
            task.metadata.revision == data.getLong("revision") && task.title == data.getString("title") &&
            when (data.getString("kind")) {
                "DELETE" -> database.records().taskDeletionLinks(task.metadata.id) == 0
                "POSTPONE" -> data.getLong("afterDue") > at &&
                    (task.dueAt == null || data.getLong("afterDue") > task.dueAt)
                else -> true
            }

    fun changed(task: SavedTask, data: JSONObject): SavedTask = when (data.getString("kind")) {
        "RENAME" -> task.copy(title = data.getString("afterTitle"))
        "PRIORITY" -> task.copy(priority = data.getInt("afterPriority"))
        "POSTPONE" -> task.copy(dueAt = data.getLong("afterDue"), dueZoneId = data.getString("afterZone"))
        else -> error("Unsupported task edit.")
    }

    fun restored(task: SavedTask, data: JSONObject): SavedTask = when (data.getString("kind")) {
        "RENAME" -> task.copy(title = data.getString("title"))
        "PRIORITY" -> task.copy(priority = data.getInt("beforePriority"))
        "POSTPONE" -> task.copy(dueAt = if (data.isNull("beforeDue")) null else data.getLong("beforeDue"),
            dueZoneId = if (data.isNull("beforeZone")) null else data.getString("beforeZone"))
        else -> error("Unsupported task undo.")
    }
}
