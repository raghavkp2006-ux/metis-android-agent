@file:Suppress("MagicNumber") // Seven-day calendar grid arithmetic.

package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.CalendarConflict
import dev.metis.agent.domain.storage.CalendarOverlap
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.presentation.designsystem.ScheduleBlock
import dev.metis.agent.presentation.designsystem.SecondaryButton
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun CalendarWorkspace(schedules: List<SavedSchedule>) {
    val context = LocalContext.current.applicationContext
    val model: CalendarEditorViewModel = viewModel(factory = viewModelFactory {
        initializer { CalendarEditorViewModel(PersonalStorage.repository(context)) }
    })
    CalendarWorkspace(schedules, model)
}

@Composable
internal fun CalendarWorkspace(schedules: List<SavedSchedule>, model: CalendarEditorViewModel) {
    val editor by model.uiState.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var selectedDay by remember { mutableStateOf(LocalDate.now(zone)) }
    var month by remember { mutableStateOf(YearMonth.from(selectedDay)) }
    DisposableEffect(model) { onDispose { model.cancel() } }

    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = {
            month = month.minusMonths(1)
            selectedDay = month.atDay(minOf(selectedDay.dayOfMonth, month.lengthOfMonth()))
        }) { Text("Previous") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f))
        TextButton(onClick = {
            month = month.plusMonths(1)
            selectedDay = month.atDay(minOf(selectedDay.dayOfMonth, month.lengthOfMonth()))
        }) { Text("Next") }
    }
    val firstColumn = month.atDay(1).dayOfWeek.value - 1
    val cellCount = ((firstColumn + month.lengthOfMonth() + 6) / 7) * 7
    Row(Modifier.fillMaxWidth()) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        }
    }
    repeat(cellCount / 7) { week ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { weekday ->
                val number = week * 7 + weekday - firstColumn + 1
                if (number in 1..month.lengthOfMonth()) {
                    val day = month.atDay(number)
                    val count = schedules.count { it.status in VISIBLE_STATUSES &&
                        CalendarOverlap.overlapsDay(it, day, zone) }
                    TextButton(onClick = { selectedDay = day }, Modifier.weight(1f)
                        .testTag("calendar_day_$day").semantics {
                            selected = day == selectedDay
                            contentDescription = "$day, $count saved events"
                        }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(
                            containerColor = if (day == selectedDay) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent,
                        )) {
                        Text("$number${if (count > 0) "•" else ""}")
                    }
                } else Text("", Modifier.weight(1f))
            }
        }
    }
    Text("$selectedDay · saved on this device")
    SecondaryButton("Add event", { model.edit(selectedDay) }, Modifier.testTag("calendar_add"))
    if (editor.finished) Text("Calendar updated.")
    editor.error?.let { Text(it) }
    if (editor.busy) Text("Saving calendar change…")
    val dayEvents = schedules.filter { it.status in VISIBLE_STATUSES &&
        CalendarOverlap.overlapsDay(it, selectedDay, zone) }
        .sortedBy { it.startAt }
    if (dayEvents.isEmpty()) Text("No saved local events for this day. Other calendars are not included.")
    dayEvents.forEach { event ->
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        val start = formatter.format(Instant.ofEpochMilli(event.startAt).atZone(zone))
        val end = formatter.format(Instant.ofEpochMilli(event.endAt).atZone(zone))
        ScheduleBlock(event.title, "$start – $end ($zone)", event.reason)
        if (CalendarOverlap.conflicts(event, schedules).isNotEmpty()) {
            Text("Overlaps another saved event", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("calendar_conflict_${event.metadata.id}").semantics {
                    contentDescription = "${event.title} overlaps another saved event"
                })
        }
        if (event.isCalendarEditable()) {
            SecondaryButton("Edit event", { model.edit(selectedDay, event) },
                Modifier.testTag("calendar_edit_${event.metadata.id}"))
            SecondaryButton("Delete event", { model.confirmDelete(event) },
                Modifier.testTag("calendar_delete_${event.metadata.id}"))
        }
    }
    CalendarEditDialogs(editor, model)
}

private val VISIBLE_STATUSES = setOf(ScheduleStatus.ACCEPTED, ScheduleStatus.COMPLETED)

@Composable
private fun CalendarEditDialogs(editor: CalendarEditorState, model: CalendarEditorViewModel) {
    if (editor.conflicts.isNotEmpty()) {
        CalendarConflictDialog(editor.conflicts, model, editor.busy)
    } else editor.draft?.let { draft ->
        AlertDialog(
            onDismissRequest = model::cancel,
            title = { Text(if (draft.original == null) "Add local event" else "Edit local event") },
            text = {
                androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text("Stored only in METIS. Times use ${draft.original?.zoneId ?: ZoneId.systemDefault().id}.")
                    OutlinedTextField(draft.title, { model.change(draft.copy(title = it)) },
                        label = { Text("Title") }, singleLine = true, modifier = Modifier.testTag("calendar_title"))
                    OutlinedTextField(draft.date, { model.change(draft.copy(date = it)) },
                        label = { Text("Start date YYYY-MM-DD") }, singleLine = true,
                        modifier = Modifier.testTag("calendar_start_date"))
                    OutlinedTextField(draft.start, { model.change(draft.copy(start = it)) },
                        label = { Text("Start time HH:mm") }, singleLine = true,
                        modifier = Modifier.testTag("calendar_start_time"))
                    OutlinedTextField(draft.endDate, { model.change(draft.copy(endDate = it)) },
                        label = { Text("End date YYYY-MM-DD") }, singleLine = true,
                        modifier = Modifier.testTag("calendar_end_date"))
                    OutlinedTextField(draft.end, { model.change(draft.copy(end = it)) },
                        label = { Text("End time HH:mm") }, singleLine = true,
                        modifier = Modifier.testTag("calendar_end_time"))
                    OutlinedTextField(draft.details, { model.change(draft.copy(details = it)) },
                        label = { Text("Details (optional)") })
                    editor.error?.let { Text(it) }
                }
            },
            confirmButton = { TextButton(onClick = model::save, enabled = !editor.busy) {
                Text("Save event")
            } },
            dismissButton = { TextButton(onClick = model::cancel) { Text("Cancel") } },
        )
    }
    editor.deleting?.let { event ->
        AlertDialog(
            onDismissRequest = model::cancel,
            title = { Text("Delete local event?") },
            text = { Text(event.title) },
            confirmButton = { TextButton(onClick = model::delete, enabled = !editor.busy) {
                Text("Delete")
            } },
            dismissButton = { TextButton(onClick = model::cancel) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CalendarConflictDialog(conflicts: List<CalendarConflict>, model: CalendarEditorViewModel,
    busy: Boolean) {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    AlertDialog(
        onDismissRequest = model::changeTime,
        modifier = Modifier.testTag("calendar_conflict_dialog").semantics {
            contentDescription = "Time conflict. ${conflicts.size} overlapping saved events."
        },
        title = { Text("Time conflict") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState())) {
                Text("This time overlaps saved events on this device:")
                conflicts.forEach { conflict ->
                    val zone = ZoneId.of(conflict.zoneId)
                    val start = formatter.format(Instant.ofEpochMilli(conflict.startAt).atZone(zone))
                    val end = formatter.format(Instant.ofEpochMilli(conflict.endAt).atZone(zone))
                    Text("${conflict.title}: $start – $end (${zone.id})",
                        modifier = Modifier.testTag("calendar_conflict_item_${conflict.id}"))
                }
            }
        },
        confirmButton = { TextButton(onClick = model::changeTime, enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("calendar_conflict_change")) {
            Text("Change time")
        } },
        dismissButton = {
            TextButton(onClick = model::saveAnyway, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("calendar_conflict_save")) {
                Text("Save anyway")
            }
            TextButton(onClick = model::cancel, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("calendar_conflict_cancel")) {
                Text("Cancel")
            }
        },
    )
}
