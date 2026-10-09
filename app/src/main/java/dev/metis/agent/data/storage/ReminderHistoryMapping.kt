package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ReminderHistoryEntry
import java.util.UUID
import kotlinx.coroutines.flow.combine
import org.json.JSONObject

internal object ReminderHistoryMapping {
    fun observe(repository: LocalPersonalRepository) = combine(repository.foundation.actionRun.observe(),
        repository.foundation.reminder.observe()) { runs, reminders ->
        val byId = reminders.associateBy { it.metadata.id }
        runs.filter { it.actionType == "CREATE_REMINDER" && it.safeErrorCode != "PERSONAL_DATA_REMOVED" &&
            JSONObject(it.payload).optString("operation") == ReminderActionEncoding.OPERATION }.map { run ->
            val data = ReminderActionEncoding.data(run)
            val reminder = byId[run.entityId]
            val scheduled = run.status == "SUCCEEDED" && reminder?.schedulingState == "SCHEDULED"
            ReminderHistoryEntry(UUID.fromString(run.metadata.id), UUID.fromString(requireNotNull(run.entityId)),
                data.getString("title"), "${data.getString("local")} (${data.getString("zone")}) · approximate",
                reminder?.schedulingState ?: "PERSONAL_DATA_REMOVED", run.status == "PENDING",
                if (scheduled) ReminderActionEncoding.receipt(run) else null)
        }
    }
}
