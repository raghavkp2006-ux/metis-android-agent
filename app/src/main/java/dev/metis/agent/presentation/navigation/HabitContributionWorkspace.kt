@file:Suppress("MagicNumber") // Cell dimensions and the five-level visual key are part of the heatmap layout.

package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.R
import dev.metis.agent.domain.discipline.HabitContributionDay
import dev.metis.agent.domain.discipline.HabitContributionReport
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.LoadingState
import dev.metis.agent.presentation.designsystem.MetisCard
import dev.metis.agent.presentation.designsystem.MetisSectionHeader
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.res.pluralStringResource

@Composable
internal fun HabitContributionSection() {
    val context = LocalContext.current.applicationContext
    val model: HabitContributionViewModel = viewModel(factory = viewModelFactory {
        initializer { HabitContributionViewModel(PersonalStorage.repository(context).discipline) }
    })
    HabitContributionSection(model)
}

@Composable
internal fun HabitContributionSection(model: HabitContributionViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    MetisSectionHeader(stringResource(R.string.contribution_title))
    when {
        state.failed -> ErrorState(stringResource(R.string.storage_error), model::reload)
        state.loading -> LoadingState(stringResource(R.string.contribution_loading))
        state.report != null -> HabitContributionContent(state, model::selectDate)
    }
}

@Composable
internal fun HabitContributionContent(
    state: HabitContributionUiState,
    onSelectDate: (java.time.LocalDate) -> Unit,
) {
    val report = state.report ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (report.totalExpectedCompletions == 0) {
            EmptyState(stringResource(R.string.contribution_empty_title),
                stringResource(R.string.contribution_empty_description))
        }
        StreakAndTotals(report)
        Text(stringResource(R.string.contribution_week_start),
            Modifier.testTag("contribution_week_start"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ContributionGrid(report, state.selectedDay, onSelectDate)
        ContributionLegend()
        SelectedDayDetails(state.selectedDay)
    }
}

@Composable
private fun StreakAndTotals(report: HabitContributionReport) {
    MetisCard {
        Text(stringResource(R.string.contribution_current_streak), style = MaterialTheme.typography.titleMedium)
        Text(pluralStringResource(R.plurals.contribution_streak_days, report.currentStreak, report.currentStreak))
        Text(stringResource(R.string.contribution_longest_streak), style = MaterialTheme.typography.titleMedium)
        Text(pluralStringResource(R.plurals.contribution_streak_days, report.longestStreak, report.longestStreak))
        Text(stringResource(R.string.contribution_streak_rule), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(pluralStringResource(R.plurals.contribution_totals, report.qualifyingDays,
        report.totalCompletedHabits, report.totalExpectedCompletions, report.qualifyingDays))
}

private data class HeatmapWeek(val days: List<HabitContributionDay>, val monthLabel: String?)

@Composable
private fun ContributionGrid(
    report: HabitContributionReport,
    selected: HabitContributionDay?,
    onSelectDate: (java.time.LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val monthFormatter = DateTimeFormatter.ofPattern("MMM", locale)
    val weeks = report.days.chunked(7).mapIndexed { weekIndex, week ->
        val firstOfMonth = week.firstOrNull { it.date.dayOfMonth == 1 }
        val labelDate = firstOfMonth?.date ?: if (weekIndex == 0) week.first().date else null
        HeatmapWeek(week, labelDate?.format(monthFormatter))
    }
    val previousWeeks = rememberScrollState()
    Column(
        Modifier.fillMaxWidth().horizontalScroll(previousWeeks)
            .semantics { isTraversalGroup = true },
    ) {
        Row {
            Box(Modifier.width(DAY_LABEL_WIDTH).heightIn(min = MONTH_LABEL_HEIGHT))
            weeks.forEach { week ->
                Box(Modifier.width(WEEK_COLUMN_WIDTH).heightIn(min = MONTH_LABEL_HEIGHT),
                    contentAlignment = Alignment.CenterStart) {
                    week.monthLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.width(DAY_LABEL_WIDTH)) {
                DAY_LABELS.forEach { label ->
                    Box(Modifier.heightIn(min = CELL_TARGET).width(DAY_LABEL_WIDTH)
                        .testTag("contribution_weekday_label"),
                        contentAlignment = Alignment.CenterStart) {
                        Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            weeks.forEachIndexed { weekIndex, week ->
                Column(Modifier.width(WEEK_COLUMN_WIDTH).testTag("contribution_week")) {
                    week.days.forEachIndexed { dayIndex, day ->
                        HeatmapCell(day, selected?.date == day.date, weekIndex * 7 + dayIndex, onSelectDate,
                            Modifier.width(WEEK_COLUMN_WIDTH).heightIn(min = CELL_TARGET))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: HabitContributionDay,
    selected: Boolean,
    order: Int,
    onSelectDate: (java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = cellDescription(day)
    Box(
        modifier.defaultMinSize(minWidth = CELL_TARGET, minHeight = CELL_TARGET)
            .clickable(role = Role.Button) { onSelectDate(day.date) }
            .semantics {
                contentDescription = description
                role = Role.Button
                traversalIndex = order.toFloat()
            }.testTag("contribution_cell"),
        contentAlignment = Alignment.Center,
    ) {
        HeatmapMark(day.intensity, selected)
    }
}

@Composable
private fun HeatmapMark(intensity: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fills = listOf(
        scheme.surface,
        scheme.tertiaryContainer,
        scheme.secondaryContainer,
        scheme.primaryContainer,
        scheme.primary,
    )
    val markColor = if (intensity == 4) scheme.onPrimary else scheme.onSurface
    Canvas(modifier.size(MARK_SIZE)) {
        val radius = 3.dp.toPx()
        val border = if (selected) scheme.primary else scheme.outlineVariant
        drawRoundRect(fills[intensity], cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        drawRoundRect(border, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            style = Stroke(if (selected) 2.dp.toPx() else 1.dp.toPx()))
        val dotRadius = 1.2.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        when (intensity) {
            1 -> drawCircle(markColor, dotRadius, center)
            2 -> listOf(-3.dp.toPx(), 3.dp.toPx()).forEach { x ->
                drawCircle(markColor, dotRadius, Offset(center.x + x, center.y))
            }
            3 -> listOf(-4.dp.toPx(), 0f, 4.dp.toPx()).forEach { x ->
                drawCircle(markColor, dotRadius, Offset(center.x + x, center.y))
            }
            4 -> listOf(-3.dp.toPx(), 3.dp.toPx()).forEach { x ->
                listOf(-3.dp.toPx(), 3.dp.toPx()).forEach { y ->
                    drawCircle(markColor, dotRadius, Offset(center.x + x, center.y + y))
                }
            }
        }
    }
}

@Composable
private fun ContributionLegend() {
    Text(stringResource(R.string.contribution_legend_title), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        LEGEND_LEVELS.forEachIndexed { level, label ->
            Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HeatmapMark(level, selected = false)
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SelectedDayDetails(day: HabitContributionDay?) {
    if (day == null) return
    val locale = Locale.getDefault()
    val fullDate = day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    MetisCard(Modifier.testTag("contribution_day_details")) {
        Text(fullDate, style = MaterialTheme.typography.titleMedium)
        when {
            day.isFuture -> Text(pluralStringResource(R.plurals.contribution_upcoming,
                day.expectedCount, day.expectedCount))
            day.expectedCount == 0 -> Text(stringResource(R.string.contribution_no_expected))
            else -> {
                val percent = (day.score!! * 100).toInt()
                Text(pluralStringResource(R.plurals.contribution_day_score, day.expectedCount,
                    day.completedCount, day.expectedCount, percent))
                Text(stringResource(R.string.contribution_done_label), style = MaterialTheme.typography.labelLarge)
                if (day.completedHabits.isEmpty()) {
                    Text(stringResource(R.string.contribution_none_done))
                } else {
                    day.completedHabits.forEach { Text(it.name) }
                }
            }
        }
    }
}

@Composable
private fun cellDescription(day: HabitContributionDay): String {
    val label = day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    return when {
        day.isFuture -> pluralStringResource(R.plurals.contribution_cell_upcoming, day.expectedCount,
            label, day.expectedCount)
        day.expectedCount == 0 -> stringResource(R.string.contribution_cell_no_expected, label)
        else -> pluralStringResource(R.plurals.contribution_cell_score, day.expectedCount,
            label, day.completedCount, day.expectedCount)
    }
}

private val DAY_LABELS = DayOfWeek.entries.map { day ->
    day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
}
private val LEGEND_LEVELS = listOf(
    R.string.contribution_level_0,
    R.string.contribution_level_1,
    R.string.contribution_level_2,
    R.string.contribution_level_3,
    R.string.contribution_level_4,
)
private val DAY_LABEL_WIDTH = 40.dp
private val WEEK_COLUMN_WIDTH = 56.dp
private val CELL_TARGET = 48.dp
private val MARK_SIZE = 18.dp
private val MONTH_LABEL_HEIGHT = 32.dp
