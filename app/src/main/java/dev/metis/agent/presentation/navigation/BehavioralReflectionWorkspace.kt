package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.metis.agent.PersonalStorage
import dev.metis.agent.domain.insights.BehavioralReflection
import dev.metis.agent.domain.insights.BehavioralReflectionEngine
import dev.metis.agent.domain.insights.ReflectionPeriod
import dev.metis.agent.domain.storage.SavedDerivedInsight
import dev.metis.agent.platform.BehavioralAnalysisRuntime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class BehavioralReflectionState(
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val reflection: BehavioralReflection? = null,
    val savedInsights: List<SavedDerivedInsight> = emptyList(),
)

internal class BehavioralReflectionViewModel(
    private val isConsented: () -> Boolean,
    private val saveConsent: (Boolean) -> Unit,
    private val storage: dev.metis.agent.data.storage.LocalPersonalRepository,
) : ViewModel() {
    private val state = MutableStateFlow(BehavioralReflectionState(
        enabled = isConsented(),
    ))
    val uiState = state.asStateFlow()

    init { reloadSavedInsights() }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            state.value = state.value.copy(busy = true, failed = false, reflection = null, savedInsights = emptyList())
            try {
                if (enabled) {
                    // Remove reports from a previous consent period before allowing new analysis.
                    clearDerivedRecords()
                    saveConsent(true)
                } else {
                    // Revoke authority first; a partial deletion can never leave analysis enabled.
                    saveConsent(false)
                    clearDerivedRecords()
                }
                state.value = BehavioralReflectionState(enabled = enabled)
                reloadSavedInsights()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value.copy(enabled = isConsented(),
                    busy = false, failed = true, savedInsights = emptyList())
            }
        }
    }

    fun review(period: ReflectionPeriod) {
        if (!state.value.enabled) return
        viewModelScope.launch {
            state.value = state.value.copy(busy = true, failed = false)
            try {
                val events = storage.foundation.event.observe().first()
                val now = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                val reflection = BehavioralReflectionEngine.reflect(events, period, now, zone,
                    consented = isConsented())
                    ?: error("Behavioral analysis consent is required.")
                saveReflection(reflection)
                state.value = state.value.copy(busy = false, reflection = reflection)
                reloadSavedInsights()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value.copy(busy = false, failed = true)
            }
        }
    }

    private suspend fun clearDerivedRecords() {
        storage.foundation.derivedInsight.observe().first()
            .filter { it.methodVersion == BehavioralReflectionEngine.METHOD_VERSION }.forEach {
            storage.foundation.derivedInsight.delete(it.metadata.id, it.metadata.revision)
        }
        storage.foundation.habit.observe().first().forEach {
            storage.foundation.habit.delete(it.metadata.id, it.metadata.revision)
        }
    }

    fun deleteSavedInsights() {
        if (!isConsented()) return
        viewModelScope.launch {
            state.value = state.value.copy(busy = true, failed = false)
            try {
                storage.foundation.derivedInsight.observe().first()
                    .filter { it.methodVersion == BehavioralReflectionEngine.METHOD_VERSION }
                    .forEach { storage.foundation.derivedInsight.delete(it.metadata.id, it.metadata.revision) }
                state.value = state.value.copy(busy = false, reflection = null, savedInsights = emptyList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value.copy(busy = false, failed = true)
            }
        }
    }

    private fun reloadSavedInsights() {
        viewModelScope.launch {
            if (!isConsented()) {
                state.value = state.value.copy(savedInsights = emptyList())
                return@launch
            }
            try {
                val saved = storage.foundation.derivedInsight.observe().first()
                    .filter { it.methodVersion == BehavioralReflectionEngine.METHOD_VERSION }
                state.value = state.value.copy(savedInsights = saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value.copy(failed = true)
            }
        }
    }

    private suspend fun saveReflection(reflection: BehavioralReflection) {
        val kind = reflection.period.name
        val existing = storage.foundation.derivedInsight.observe().first().filter {
            it.kind == kind && it.methodVersion == reflection.methodVersion
        }
        val sampleCount = reflection.repeatedPatterns.sumOf { it.sampleCount }
        if (reflection.repeatedPatterns.isEmpty()) {
            existing.forEach { storage.foundation.derivedInsight.delete(it.metadata.id, it.metadata.revision) }
            return
        }
        val stats = JSONObject()
            .put("reflection_start_at", reflection.startAt)
            .put("reflection_end_at", reflection.endAt)
            .put("completed_tasks", reflection.completedTasks)
            .put("postponed_tasks", reflection.postponedTasks)
            .put("completed_focus_sessions", reflection.completedFocusSessions)
            .put("patterns", org.json.JSONArray().also { array ->
                reflection.repeatedPatterns.forEach { pattern ->
                    array.put(JSONObject().put("event_type", pattern.eventType)
                        .put("weekday", pattern.weekday).put("sample_count", pattern.sampleCount)
                        .put("distinct_days", pattern.distinctDays))
                }
            }).toString()
        val report = SavedDerivedInsight(
            kind = kind,
            computedAt = System.currentTimeMillis(),
            observationStart = reflection.evidenceStartAt,
            observationEnd = reflection.evidenceEndAt,
            sampleCount = sampleCount,
            methodVersion = reflection.methodVersion,
            confidence = reflection.repeatedPatterns.maxOf { it.distinctDays }.toFloat() /
                BehavioralReflectionEngine.MAX_WEEKLY_SAMPLES_IN_WINDOW,
            statisticsJson = stats,
            sourceWatermark = reflection.sourceWatermark,
        )
        storage.foundation.derivedInsight.save(report)
        existing.forEach { storage.foundation.derivedInsight.delete(it.metadata.id, it.metadata.revision) }
    }
}

@Composable
internal fun BehavioralReflectionWorkspace() {
    val context = LocalContext.current.applicationContext
    val model: BehavioralReflectionViewModel = viewModel(factory = viewModelFactory {
        initializer {
            BehavioralReflectionViewModel(
                isConsented = { BehavioralAnalysisRuntime.enabled(context) },
                saveConsent = { BehavioralAnalysisRuntime.setEnabled(context, it) },
                storage = PersonalStorage.repository(context),
            )
        }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    var enabled by remember(context, state.enabled) { mutableStateOf(state.enabled) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column {
        Text("Daily and weekly reflection", style = MaterialTheme.typography.titleMedium)
        Text("With your consent, METIS summarizes task and focus outcomes stored on this device. " +
            "It does not analyze titles, notes, messages, or audio. Reviews run only when you ask.")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (enabled) "Behavior analysis on" else "Behavior analysis off",
                Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { value ->
                enabled = value
                model.setEnabled(value)
            }, enabled = !state.busy, modifier = Modifier.testTag("behavioral_analysis_toggle").semantics {
                contentDescription = "Behavioral analysis consent"
            })
        }
        if (state.enabled && !state.busy) {
            dev.metis.agent.presentation.designsystem.SecondaryButton("Review today", {
                model.review(ReflectionPeriod.DAILY)
            })
            dev.metis.agent.presentation.designsystem.SecondaryButton("Review this week", {
                model.review(ReflectionPeriod.WEEKLY)
            })
        }
        if (state.busy) Text("Preparing a local reflection…")
        if (state.failed) Text("The reflection could not be completed. Try again when local storage is available.")
        if (state.enabled && state.savedInsights.isNotEmpty()) {
            Text("Saved reflections", style = MaterialTheme.typography.titleSmall)
            state.savedInsights.forEach { insight ->
                Text("${insight.kind.lowercase().replaceFirstChar { it.uppercase() }} · " +
                    "${insight.sampleCount} observations · ${insight.methodVersion}")
            }
            dev.metis.agent.presentation.designsystem.SecondaryButton("Delete saved reflections", {
                confirmDelete = true
            }, enabled = !state.busy)
        }
        state.reflection?.let { reflection ->
            Text(if (reflection.period == ReflectionPeriod.DAILY) "Today" else "This week",
                style = MaterialTheme.typography.titleSmall)
            Text("${reflection.completedTasks} tasks completed · ${reflection.postponedTasks} postponed · " +
                "${reflection.completedFocusSessions} focus sessions completed")
            if (reflection.repeatedPatterns.isEmpty()) {
                Text("There is not enough repeated evidence for a pattern yet.")
            } else {
                reflection.repeatedPatterns.forEach { pattern ->
                    Text("${pattern.eventType.lowercase().replace('_', ' ')} happened on " +
                        "${weekdayName(pattern.weekday)} ${pattern.distinctDays} different weeks " +
                        "(${pattern.sampleCount} observations).")
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete saved reflections?") },
            text = { Text("This removes derived reflection summaries. Your factual event history stays saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; model.deleteSavedInsights() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun weekdayName(day: Int) = java.time.DayOfWeek.of(day).name.lowercase()
    .replaceFirstChar { it.uppercase() }
