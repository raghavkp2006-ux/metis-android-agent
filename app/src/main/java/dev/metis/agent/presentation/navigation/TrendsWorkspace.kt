@file:Suppress("MagicNumber", "TooManyFunctions") // Display percentages and mood scale are fixed product rules.

package dev.metis.agent.presentation.navigation

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.AppIsolation
import dev.metis.agent.PersonalStorage
import dev.metis.agent.domain.discipline.TrendMean
import dev.metis.agent.domain.discipline.TrendPeriod
import dev.metis.agent.domain.discipline.TrendWindow
import dev.metis.agent.domain.discipline.TrendsReport
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.LoadingState
import dev.metis.agent.presentation.designsystem.MetisCard
import dev.metis.agent.presentation.designsystem.MetisSectionHeader
import java.time.DayOfWeek
import java.util.Locale

@Composable
internal fun TrendsWorkspace() {
    val context = LocalContext.current.applicationContext
    val model: TrendsViewModel = viewModel(factory = viewModelFactory {
        initializer { TrendsViewModel(PersonalStorage.repository(context).discipline) }
    })
    TrendsWorkspace(model, context)
}

@Composable
internal fun TrendsWorkspace(model: TrendsViewModel, context: Context) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val preferences = remember(context) {
        context.getSharedPreferences(AppIsolation.name("metis.welcome"), Context.MODE_PRIVATE)
    }
    var insightsVisible by rememberSaveable { mutableStateOf(preferences.getBoolean("trends_insights_visible", true)) }
    var window by rememberSaveable { mutableStateOf(TrendWindow.MONTH) }

    MetisSectionHeader("Trends")
    Text("Calculated on this device from your check-ins. Notes are never read. " +
        "These patterns describe logged days and do not explain why they happened.")
    when {
        state.loading -> LoadingState("Loading trends…")
        state.failed -> ErrorState("Trends could not be opened. Check local storage and try again.", model::reload)
        state.report == null -> EmptyState("No trends yet", "Log mood and habit check-ins to see trends.")
        else -> {
            val report = state.report ?: return
            if (report.periods.values.all { it.mood.sampleSize == 0 && it.completion.sampleSize == 0 }) {
                EmptyState("No trends yet", "Log mood and habit check-ins to see trends and insights.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
                    TrendWindow.entries.forEach { option ->
                        FilterChip(selected = window == option, onClick = { window = option },
                            label = { Text("${option.days} days") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                .testTag("trends_window_${option.name}"))
                    }
                }
                val period = report.periods.getValue(window)
                TrendCharts(period)
                WeekdayPatterns(report)
                Associations(report)
                EvolutionSection(report)
            }
            MetisCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gentle observations", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = insightsVisible, onCheckedChange = {
                        insightsVisible = it
                        preferences.edit { putBoolean("trends_insights_visible", it) }
                    }, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("trends_insights_toggle")
                        .semantics { contentDescription = "Show gentle observations" })
                }
                if (insightsVisible) {
                    val visible = report.insights.filterNot { it.id in state.dismissed }
                    if (visible.isEmpty()) Text("Log mood on at least four days and check in " +
                        "with habits to unlock observations.")
                    visible.forEach { insight ->
                        Column(Modifier.testTag("trends_insight_${insight.id}")) {
                            Text(insight.message)
                            Text("${insight.range.from} to ${insight.range.through} · " +
                                "${insight.sampleSize} observations",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { model.dismiss(insight.id) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendCharts(period: TrendPeriod) {
    MetisCard {
        Text("${period.range.from} to ${period.range.through}", style = MaterialTheme.typography.titleMedium)
        Text("Mood: ${period.mood.display("out of 5")} · ${period.mood.sampleSize} entries · direction " +
            period.direction.name.lowercase().replace('_', ' '),
            modifier = Modifier.testTag("trends_mood_summary"))
        Text("Habit completion: ${period.completion.percent()} · ${period.completion.sampleSize} scored days",
            modifier = Modifier.testTag("trends_completion_summary"))
        Text("Weekly mood", style = MaterialTheme.typography.titleSmall)
        period.weeks.forEach { week ->
            TrendBar("${week.from} to ${week.through}", week.mood, 5.0, "mood")
        }
        Text("Weekly completion", style = MaterialTheme.typography.titleSmall)
        period.weeks.forEach { week ->
            TrendBar("${week.from} to ${week.through}", week.completion, 1.0, "completion")
        }
    }
}

@Composable
private fun TrendBar(label: String, mean: TrendMean, max: Double, kind: String) {
    val detail = if (kind == "mood") mean.display("out of 5") else mean.percent()
    Column(Modifier.fillMaxWidth().semantics {
        contentDescription = "$label, $kind, $detail, ${mean.sampleSize} observations"
    }) {
        Text("$label · $detail · n=${mean.sampleSize}")
        // A missing mean has no bar; missing mood is never displayed as zero.
        mean.value?.let { value ->
            LinearProgressIndicator(progress = { (value / max).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WeekdayPatterns(report: TrendsReport) {
    MetisCard {
        Text("Weekday pattern · last 90 days", style = MaterialTheme.typography.titleMedium)
        Text("Each weekday needs four logged moods or scored habit days.")
        report.weekdays.forEach { weekday ->
            Text("${weekday.day.label()}: completion ${weekday.completion.percent()} " +
                "(n=${weekday.completion.sampleSize}); mood ${weekday.mood.display("out of 5")} " +
                "(n=${weekday.mood.sampleSize})", modifier = Modifier.testTag("trends_weekday_${weekday.day.name}"))
        }
    }
}

@Composable
private fun Associations(report: TrendsReport) {
    MetisCard {
        Text("Habits and mood · last 90 days", style = MaterialTheme.typography.titleMedium)
        Text("These are descriptive comparisons of logged days, not causes.")
        if (report.associations.isEmpty()) Text("Not enough data yet. Log mood on at least " +
            "four done and four not-done expected days for a habit.")
        report.associations.forEach { association -> Text(association.description) }
    }
}

@Composable
private fun EvolutionSection(report: TrendsReport) {
    val evolution = report.evolution
    MetisCard {
        Text("Last 30 days versus previous 30 days", style = MaterialTheme.typography.titleMedium)
        Text("Mood change: ${evolution.moodChange?.signed() ?: "not enough data"} " +
            "(n=${evolution.current.mood.sampleSize} vs ${evolution.previous.mood.sampleSize})")
        val completionChange = evolution.completionChange?.let {
            (it * 100).signed() + " percentage points"
        } ?: "not enough data"
        Text("Completion change: $completionChange " +
            "(n=${evolution.current.completion.sampleSize} vs ${evolution.previous.completion.sampleSize})")
    }
}

private fun TrendMean.display(suffix: String): String = value?.let { "${"%.1f".format(Locale.US, it)} $suffix" }
    ?: "not enough data"
private fun TrendMean.percent(): String = value?.let { "${"%.0f".format(Locale.US, it * 100)}%" }
    ?: "not enough data"
private fun Double.signed(): String = "%+.1f".format(Locale.US, this)
private fun DayOfWeek.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
