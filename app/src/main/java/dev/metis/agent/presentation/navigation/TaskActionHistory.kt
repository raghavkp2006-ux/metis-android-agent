package dev.metis.agent.presentation.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.presentation.designsystem.SecondaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class TaskHistoryState(
    val entries: List<TaskActionHistoryEntry> = emptyList(), val failed: Boolean = false,
    val busy: Boolean = false, val message: String? = null,
    val loading: Boolean = true,
)

internal class TaskActionHistoryModel(private val store: AcceptedTaskStore) : ViewModel() {
    private val state = MutableStateFlow(TaskHistoryState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    init { reload() }

    fun reload() {
        observation?.cancel()
        state.value = state.value.copy(loading = true)
        observation = viewModelScope.launch {
            try {
                store.observeHistory().collect {
                    state.value = state.value.copy(entries = it, failed = false, loading = false)
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                state.value = state.value.copy(entries = emptyList(), failed = true, loading = false)
            }
        }
    }

    fun retry(entry: TaskActionHistoryEntry) = perform {
        store.retry(entry.actionId).reason
    }

    fun cancel(entry: TaskActionHistoryEntry) = perform {
        store.cancelPending(entry.actionId)
        "Pending task cancelled."
    }

    fun undo(entry: TaskActionHistoryEntry) = perform {
        val result = store.undo(requireNotNull(entry.receipt))
        if (result == UndoResult.UNDONE) "Task action undone and verified locally." else
            "Undo unavailable: task changes, dependencies or current policy prevent undo."
    }

    private fun perform(operation: suspend () -> String) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val message = try { withContext(NonCancellable) { operation() } }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                    "The action could not be verified. Reload accepted task actions before retrying."
                }
            state.value = state.value.copy(busy = false, message = message)
        }
    }
}

@Composable
internal fun TaskActionHistory() {
    val context = LocalContext.current.applicationContext
    val model: TaskActionHistoryModel = viewModel(factory = viewModelFactory {
        initializer { TaskActionHistoryModel(PersonalStorage.repository(context).taskActions) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    Text("Accepted task actions", style = MaterialTheme.typography.titleMedium)
    Text("Task actions require acceptance. Undo rechecks task changes and dependencies.")
    if (state.loading) {
        Text("Loading accepted task actions…")
    } else if (state.failed) {
        Text("Accepted task actions could not be read.")
        SecondaryButton("Reload accepted task actions", model::reload)
    } else {
        if (state.entries.isEmpty()) Text("No accepted task actions yet.")
        state.entries.take(MAX_HISTORY_ROWS).forEach { entry ->
            Text(entry.title)
            Text(if (entry.completion) "Task completion" else "Task creation")
            Text(entry.status.name)
            if (entry.undone) Text("Undone and verified locally.")
            if (entry.status == ActionStatus.PENDING) {
                Text("Accepted but not completed. Retry rechecks expiry and current policy.")
                SecondaryButton("Retry accepted task", { model.retry(entry) }, enabled = !state.busy)
                SecondaryButton("Cancel pending task", { model.cancel(entry) }, enabled = !state.busy)
            }
            if (entry.receipt != null) {
                Text("Verified local action · ${entry.receipt.outcome.finishedAt}")
                SecondaryButton("Undo task action", { model.undo(entry) }, enabled = !state.busy)
            }
        }
        if (state.entries.size > MAX_HISTORY_ROWS) Text("Showing the latest 20 accepted task actions.")
    }
    state.message?.let { Text(it) }
}
private const val MAX_HISTORY_ROWS = 20
