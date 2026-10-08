package dev.metis.agent.presentation.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import dev.metis.agent.R
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.TaskStatus
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.MemorySearchResult
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.LoadingState
import dev.metis.agent.presentation.designsystem.MemoryRow
import dev.metis.agent.presentation.designsystem.ScheduleBlock
import dev.metis.agent.presentation.designsystem.TaskRow
import dev.metis.agent.presentation.designsystem.SecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun SavedRecords(
    destination: ShellDestination, records: RecordsUiState, onReload: () -> Unit, onSearch: (String) -> Unit,
    onSearchMore: () -> Unit,
) {
    Text(stringResource(destination.description))
    when {
        records.loading -> LoadingState(stringResource(R.string.storage_loading))
        records.failed -> ErrorState(stringResource(R.string.storage_error), onReload)
        else -> when (destination) {
            ShellDestination.TODAY -> TaskRecords(records)
            ShellDestination.PLAN -> ScheduleRecords(records)
            ShellDestination.YOU -> MemoryRecords(records, onSearch, onSearchMore)
            else -> Unit
        }
    }
}

@Composable
private fun TaskRecords(records: RecordsUiState) {
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
private fun ScheduleRecords(records: RecordsUiState) {
    PlanningRecords(records)
    if (records.schedules.isEmpty()) EmptyState(
        stringResource(R.string.schedules_empty), stringResource(R.string.saved_records_read_only),
    )
    records.schedules.forEach { schedule ->
        ScheduleBlock(
            schedule.title,
            "${formatTime(schedule.startAt, schedule.zoneId)} – ${formatTime(schedule.endAt, schedule.zoneId)}",
            stringResource(R.string.schedule_metadata, schedule.status.name, schedule.reason),
        )
    }
}

@Composable
private fun MemoryRecords(records: RecordsUiState, onSearch: (String) -> Unit, onSearchMore: () -> Unit) {
    PreferenceRecords(records.preferences)
    val search = records.search
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = search.query, onValueChange = onSearch, singleLine = true,
        label = { Text(stringResource(R.string.memory_search_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(force = true) }),
    )
    when {
        search.query.isBlank() -> MemoryList(records.memories)
        search.loading -> LoadingState(stringResource(R.string.memory_search_loading))
        search.failed -> ErrorState(stringResource(R.string.storage_error), { onSearch(search.query) })
        else -> search.result?.let { SearchResults(it, onSearchMore) }
    }
}

@Composable
private fun SearchResults(result: MemorySearchResult, onSearchMore: () -> Unit) {
    if (result.candidatesTruncated) {
        Text(pluralStringResource(
            R.plurals.memory_search_candidates_limited, result.candidateLimit, result.candidateLimit,
        ))
        if (result.candidateLimit < MemorySearchQuery.MAX_CANDIDATES) {
            SecondaryButton(stringResource(R.string.memory_search_more), onSearchMore)
        }
    }
    if (result.matchesTruncated) Text(stringResource(R.string.memory_search_results_limited))
    if (result.memories.isEmpty()) EmptyState(
        stringResource(R.string.memory_search_empty), stringResource(R.string.memory_search_help),
    ) else MemoryList(result.memories)
}

@Composable
private fun MemoryList(memories: List<SavedMemory>) {
    if (memories.isEmpty()) EmptyState(
        stringResource(R.string.memories_empty), stringResource(R.string.saved_records_read_only),
    )
    memories.forEach { memory ->
        val origin = stringResource(
            if (memory.origin == MemoryOrigin.EXPLICIT) R.string.memory_explicit else R.string.memory_derived,
        )
        MemoryRow(memory.content, stringResource(R.string.memory_metadata, origin, memory.type.name))
        Text(
            formatTime(memory.metadata.updatedAt, ZoneId.systemDefault().id),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatTime(timestamp: Long, zone: String): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.of(zone))
    .format(Instant.ofEpochMilli(timestamp)) + " ($zone)"
