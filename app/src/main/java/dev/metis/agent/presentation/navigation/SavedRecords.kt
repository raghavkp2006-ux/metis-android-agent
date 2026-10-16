package dev.metis.agent.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import dev.metis.agent.R
import dev.metis.agent.domain.storage.TaskStatus
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.LoadingState
import dev.metis.agent.presentation.designsystem.ScheduleBlock
import dev.metis.agent.presentation.designsystem.TaskRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun SavedRecords(
    destination: ShellDestination, records: RecordsUiState, onReload: () -> Unit, onSearch: (String) -> Unit,
    onSearchMore: () -> Unit,
    onMemoryFilter: (dev.metis.agent.domain.storage.MemoryOrigin?) -> Unit,
    onRequest: (String) -> Unit = {},
) {
    Text(stringResource(destination.description))
    when {
        records.loading -> LoadingState(stringResource(R.string.storage_loading))
        records.failed -> ErrorState(stringResource(R.string.storage_error), onReload)
        else -> {
            FoundationRecords(destination)
            when (destination) {
                ShellDestination.TODAY -> TaskRecords(records)
                ShellDestination.PLAN -> ScheduleRecords(records, onRequest)
                ShellDestination.YOU -> MemoryWorkspace(records, onSearch, onSearchMore, onMemoryFilter)
                else -> Unit
            }
        }
    }
}

@Composable
internal fun TaskRecords(records: RecordsUiState) {
    val prerequisiteCounts = records.dependencies.groupingBy { it.taskId }.eachCount()
    if (records.tasks.isEmpty()) EmptyState(
        stringResource(R.string.tasks_empty), stringResource(R.string.saved_records_read_only),
    )
    records.tasks.forEach { task ->
        val due = task.dueAt?.let { formatTime(it, requireNotNull(task.dueZoneId)) }
            ?: stringResource(R.string.task_no_deadline)
        TaskRow(
            task.title, stringResource(R.string.task_metadata, task.status.name, due),
            task.status == TaskStatus.COMPLETED, onCompletedChange = null,
        )
        val count = prerequisiteCounts[task.metadata.id] ?: 0
        TaskPlanningLinks(task, records)
        if (count > 0) Text(pluralStringResource(R.plurals.task_prerequisites, count, count))
    }
}

@Composable
internal fun ScheduleRecords(records: RecordsUiState, onRequest: (String) -> Unit) {
    CalendarWorkspace(records.schedules)
    Text("Plan whole tasks around saved commitments. Other calendars are not included.")
    dev.metis.agent.presentation.designsystem.SecondaryButton("Plan my day", { onRequest("Plan my day") })
    dev.metis.agent.presentation.designsystem.SecondaryButton("Plan my evening", { onRequest("Plan my evening") })
    if (records.schedules.isEmpty()) EmptyState(
        stringResource(R.string.schedules_empty), stringResource(R.string.saved_records_read_only),
    )
    records.schedules.forEach { schedule ->
        ScheduleBlock(
            schedule.title,
            "${formatTime(schedule.startAt, schedule.zoneId)} – ${formatTime(schedule.endAt, schedule.zoneId)}",
            stringResource(R.string.schedule_metadata, schedule.status.name, schedule.reason),
        )
        if (schedule.status == dev.metis.agent.domain.storage.ScheduleStatus.ACCEPTED &&
            schedule.startAt > System.currentTimeMillis()) PlanReminderRequest(schedule, onRequest)
    }
}

@Composable
private fun PlanReminderRequest(schedule: dev.metis.agent.domain.storage.SavedSchedule, onRequest: (String) -> Unit) {
    val roundedStart = (schedule.startAt + REMINDER_MINUTE_MILLIS - 1) / REMINDER_MINUTE_MILLIS * REMINDER_MINUTE_MILLIS
    val local = Instant.ofEpochMilli(roundedStart).atZone(ZoneId.systemDefault())
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ROOT))
    Text("Optional independent reminder, rounded up to a minute and possibly late. " +
        "Editing or deleting a block will not change an accepted reminder.")
    dev.metis.agent.presentation.designsystem.SecondaryButton("Review reminder for this block", {
        onRequest("Remind me ${local.toLocalDate()} at $time to Start planned task")
    }, androidx.compose.ui.Modifier.testTag("plan_reminder_${schedule.metadata.id}"))
}

private const val REMINDER_MINUTE_MILLIS = 60_000L

private fun formatTime(timestamp: Long, zone: String): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.of(zone))
    .format(Instant.ofEpochMilli(timestamp)) + " ($zone)"
