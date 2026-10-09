package dev.metis.agent.presentation.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.R
import dev.metis.agent.data.storage.LocalEventTimeline
import dev.metis.agent.domain.storage.EventTimelineEntry
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.SecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class TimelineState(
    val entries: List<EventTimelineEntry> = emptyList(), val loading: Boolean = true, val failed: Boolean = false,
)

internal class EventTimelineModel(private val timeline: LocalEventTimeline) : ViewModel() {
    private val state = MutableStateFlow(TimelineState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    init { reload() }

    fun reload() {
        observation?.cancel()
        state.value = TimelineState()
        observation = viewModelScope.launch {
            try {
                timeline.observe().collect { state.value = TimelineState(it, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                state.value = TimelineState(loading = false, failed = true)
            }
        }
    }
}

@Composable
internal fun EventTimeline() {
    val context = LocalContext.current.applicationContext
    val model: EventTimelineModel = viewModel(factory = viewModelFactory {
        initializer { EventTimelineModel(LocalEventTimeline(PersonalStorage.repository(context).foundation)) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    var limit by remember { mutableStateOf(TIMELINE_PAGE) }
    when {
        state.loading -> Text("Loading local history…")
        state.failed -> ErrorState("Local history could not be read.", model::reload)
        state.entries.isEmpty() -> Text(stringResource(R.string.history_empty))
        else -> {
            state.entries.take(limit).forEach { TimelineOutcome(it) }
            if (state.entries.size > limit) SecondaryButton("Show more history", { limit += TIMELINE_PAGE })
        }
    }
}

@Composable
private fun TimelineOutcome(entry: EventTimelineEntry) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    Text(entry.type.replace('_', ' '), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("timeline_event_${entry.id}"))
    entry.title?.let { Text(it) }
    Text(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss VV").withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(entry.timestamp)))
    Text(entry.summary)
    entry.status?.let { Text("Outcome: $it · ${entry.verification}") }
    SecondaryButton(if (expanded) "Hide event details" else "Show event details", { expanded = !expanded },
        Modifier.testTag("timeline_details_${entry.id}"))
    if (expanded) {
        Text("Source: ${entry.source}")
        entry.requestId?.let { Text("Request: $it") }
        entry.actionId?.let { Text("Action: $it") }
        if (entry.audit.isEmpty()) Text("No correlated policy audit is available.")
        entry.audit.forEach {
            Text("${it.decision} · ${it.policyVersion} · autonomy ${it.autonomyLevel}")
            Text(it.reason)
        }
    }
}

private const val TIMELINE_PAGE = 50
