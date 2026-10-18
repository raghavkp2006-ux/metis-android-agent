@file:Suppress("MagicNumber", "TooManyFunctions") // Mood scale 1..5 and the seven-day window are domain constants.

package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.R
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.LoadingState
import dev.metis.agent.presentation.designsystem.MetisCard
import dev.metis.agent.presentation.designsystem.MetisSectionHeader
import dev.metis.agent.presentation.designsystem.PrimaryButton
import dev.metis.agent.presentation.designsystem.SecondaryButton
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HabitWorkspace() {
    val context = LocalContext.current.applicationContext
    val model: HabitCheckinViewModel = viewModel(factory = viewModelFactory {
        initializer { HabitCheckinViewModel(PersonalStorage.repository(context).discipline) }
    })
    HabitWorkspace(model)
}

@Composable
internal fun HabitWorkspace(model: HabitCheckinViewModel, modifier: Modifier = Modifier) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AgentSpacing.large)) {
        when {
            state.failed -> ErrorState(stringResource(R.string.storage_error), model::reload)
            state.loading -> LoadingState("Loading your habits…")
            else -> {
                CheckinDaySection(state, model)
                HabitManagementSection(state, model)
            }
        }
    }
}

@Composable
private fun CheckinDaySection(state: HabitCheckinUiState, model: HabitCheckinViewModel) {
    MetisSectionHeader("Today")
    Text(state.selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Check in for today or backfill one of the previous seven local days.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
        state.backupDays.forEach { day ->
            FilterChip(
                selected = day == state.selectedDate, onClick = { model.selectDay(day) },
                label = { Text(dayLabel(day, state.today)) },
                modifier = Modifier.testTag("checkin_day_$day").defaultMinSize(minHeight = AgentSpacing.touchTarget)
                    .semantics { contentDescription = "Check in $day" },
            )
        }
    }
    BackfillField(model)
    state.message?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("checkin_message").semantics { liveRegion = LiveRegionMode.Polite })
    }
    ExpectedHabits(state, model)
    MetisSectionHeader("How was the day?")
    Text("Optional mood from 1 to 5.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
        for (value in 1..5) {
            FilterChip(
                selected = state.mood == value,
                onClick = { model.setMood(if (state.mood == value) null else value) },
                label = { Text(value.toString()) },
                modifier = Modifier.testTag("checkin_mood_$value").defaultMinSize(minHeight = AgentSpacing.touchTarget)
                    .semantics { contentDescription = "Mood $value" },
            )
        }
    }
    OutlinedTextField(state.note, model::setNote, label = { Text("Note (optional)") }, minLines = 2,
        modifier = Modifier.fillMaxWidth().testTag("checkin_note"))
    SecondaryButton("Save check-in", { model.saveCheckin() }, Modifier.testTag("checkin_save"),
        enabled = !state.busy, loading = state.busy)
}

@Composable
private fun BackfillField(model: HabitCheckinViewModel) {
    var manual by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small),
        verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(manual, { manual = it }, label = { Text("Backfill a day (yyyy-MM-dd)") }, singleLine = true,
            modifier = Modifier.weight(1f).testTag("checkin_manual_date"))
        SecondaryButton("Open day", { model.selectDayText(manual) }, Modifier.testTag("checkin_open_day"))
    }
}

@Composable
private fun ExpectedHabits(state: HabitCheckinUiState, model: HabitCheckinViewModel) {
    val expected = state.expectedHabits()
    MetisSectionHeader(if (state.selectedDate == state.today) "Today's habits" else "Habits for this day")
    if (expected.isEmpty()) EmptyState("No habits expected here",
        "Create a habit below or choose another day. Nothing is inferred.")
    expected.forEach { habit ->
        HabitTickRow(habit, state.completions[habit.metadata.id] == true, !state.busy) { completed ->
            model.toggleTick(habit.metadata.id, completed)
        }
    }
}

@Composable
private fun HabitTickRow(habit: HabitDefinition, completed: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = AgentSpacing.touchTarget)
            .toggleable(value = completed, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .testTag("checkin_tick_${habit.metadata.id}")
            .semantics { contentDescription = "${habit.name}, ${cadenceLabel(habit.cadence)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small),
    ) {
        Checkbox(checked = completed, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgentSpacing.tiny)) {
            Text(habit.name)
            Text(habit.description?.takeIf { it.isNotBlank() } ?: cadenceLabel(habit.cadence),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HabitManagementSection(state: HabitCheckinUiState, model: HabitCheckinViewModel) {
    MetisSectionHeader("Manage habits")
    Text("A habit's start date and cadence are fixed after creation and cannot be changed.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    CreateHabitForm(state, model)
    if (state.saved) Text("Habit changes saved.", modifier = Modifier.testTag("habit_saved"))
    if (state.habits.isEmpty()) EmptyState("No habits yet", "Create your first habit above.")
    state.habits.sortedBy { it.startLocalDate }.forEach { habit -> HabitCard(habit, state.busy, model) }
}

@Composable
private fun CreateHabitForm(state: HabitCheckinUiState, model: HabitCheckinViewModel) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf(state.today.toString()) }
    var daily by remember { mutableStateOf(true) }
    var weekdays by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    val cadence = if (daily) HabitCadence.DAILY else (weekdays.toCadence() ?: HabitCadence.DAILY)
    MetisCard {
        Text("New habit", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it }, label = { Text("Habit name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("habit_name"))
        OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth().testTag("habit_description"))
        OutlinedTextField(startText, { startText = it }, label = { Text("Start date (yyyy-MM-dd)") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("habit_start"))
        Text("Repeat", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
            FilterChip(daily, { daily = true }, label = { Text("Daily") },
                modifier = Modifier.testTag("habit_cadence_daily").defaultMinSize(minHeight = AgentSpacing.touchTarget))
            DayOfWeek.entries.forEach { day ->
                FilterChip(!daily && day in weekdays, {
                    daily = false
                    weekdays = if (day in weekdays) weekdays - day else weekdays + day
                }, label = { Text(dayShort(day)) },
                    modifier = Modifier.testTag("habit_weekday_${day.name}")
                        .defaultMinSize(minHeight = AgentSpacing.touchTarget)
                        .semantics { contentDescription = "Repeat on ${day.name.lowercase()}" })
            }
        }
        Text("Starts on the chosen date. Start date and cadence cannot be changed after creation.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("Save habit", {
            model.createHabit(name, description, cadence, startText)
            name = ""
            description = ""
        }, Modifier.testTag("habit_save"), enabled = name.isNotBlank() && (daily || weekdays.isNotEmpty()) &&
            !state.busy)
    }
}

@Composable
private fun HabitCard(habit: HabitDefinition, busy: Boolean, model: HabitCheckinViewModel) {
    var confirming by remember { mutableStateOf(false) }
    MetisCard {
        Text(habit.name, style = MaterialTheme.typography.titleLarge)
        habit.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
        Text("Starts ${habit.startLocalDate} · ${cadenceLabel(habit.cadence)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (habit.active) "Active" else "Archived since ${habit.archivedAt}",
            modifier = Modifier.testTag("habit_state_${habit.metadata.id}"))
        if (habit.active) {
            SecondaryButton("Archive", { model.archiveHabit(habit.metadata.id) },
                Modifier.testTag("habit_archive_${habit.metadata.id}"), enabled = !busy)
        } else {
            SecondaryButton("Restore as a new habit", { model.restoreHabit(habit.metadata.id) },
                Modifier.testTag("habit_restore_${habit.metadata.id}"), enabled = !busy)
        }
        SecondaryButton("Delete habit", { confirming = true },
            Modifier.testTag("habit_delete_${habit.metadata.id}"), enabled = !busy)
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Delete this habit?") },
        text = { Text("Deletes ${habit.name} and its recorded ticks. Other habits and days are kept.") },
        confirmButton = { TextButton(modifier = Modifier.testTag("habit_confirm_delete"), onClick = {
            confirming = false
            model.deleteHabit(habit.metadata.id)
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
    )
}

private fun Set<DayOfWeek>.toCadence(): HabitCadence? =
    if (isEmpty()) null else HabitCadence.weekdays(this)

private fun cadenceLabel(cadence: HabitCadence): String = if (cadence.weekdayMask == 0) "Daily"
else DayOfWeek.entries.filter { cadence.matches(it) }.joinToString(", ") { dayShort(it) }

private fun dayShort(day: DayOfWeek) = day.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

private fun dayLabel(day: LocalDate, today: LocalDate): String = when {
    day == today -> "Today"
    day == today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}
