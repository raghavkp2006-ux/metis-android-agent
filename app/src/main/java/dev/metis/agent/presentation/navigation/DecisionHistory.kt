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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.domain.storage.FoundationRepository
import dev.metis.agent.domain.storage.SavedRecommendation
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.SecondaryButton
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DecisionHistoryState(
    val entries: List<SavedRecommendation> = emptyList(), val loading: Boolean = true, val failed: Boolean = false,
)

internal class DecisionHistoryModel(private val store: FoundationRepository<SavedRecommendation>) : ViewModel() {
    private val state = MutableStateFlow(DecisionHistoryState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    init { reload() }

    fun reload() {
        observation?.cancel()
        state.value = DecisionHistoryState()
        observation = viewModelScope.launch {
            try {
                store.observe().collect { state.value = DecisionHistoryState(it, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                state.value = DecisionHistoryState(loading = false, failed = true)
            }
        }
    }
}

@Composable
internal fun DecisionHistory() {
    val context = LocalContext.current.applicationContext
    val model: DecisionHistoryModel = viewModel(factory = viewModelFactory {
        initializer { DecisionHistoryModel(PersonalStorage.repository(context).foundation.recommendation) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    Text("Saved task suggestions", style = MaterialTheme.typography.titleMedium)
    Text("Historical snapshots, not current status or accepted plans. Ask again for a fresh recommendation.")
    Text("Showing up to 10 recent suggestions.")
    when {
        state.loading -> Text("Loading saved suggestions…")
        state.failed -> ErrorState("Saved suggestions could not be read.", model::reload)
        state.entries.isEmpty() -> Text("No saved task suggestions yet.")
        else -> state.entries.take(HISTORY_ROWS).forEach { SavedDecision(it) }
    }
}

@Composable
private fun SavedDecision(record: SavedRecommendation) {
    var expanded by remember(record.metadata.id) { mutableStateOf(false) }
    Text("Generated ${Instant.ofEpochMilli(record.generatedAt)} · snapshot expires " +
        Instant.ofEpochMilli(record.expiresAt), modifier = Modifier.testTag("decision_history_${record.metadata.id}"))
    SecondaryButton(if (expanded) "Hide suggestion explanation" else "Show suggestion explanation",
        { expanded = !expanded }, Modifier.testTag("decision_explanation_${record.metadata.id}"))
    if (expanded) Text(record.reason)
}

private const val HISTORY_ROWS = 10
