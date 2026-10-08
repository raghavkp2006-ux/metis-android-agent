package dev.metis.agent.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.data.storage.DebugStorageInspector
import dev.metis.agent.data.storage.StorageInspectionSnapshot
import dev.metis.agent.presentation.designsystem.SecondaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class InspectionUiState(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val snapshot: StorageInspectionSnapshot? = null,
)

internal class StorageInspectionViewModel(private val inspector: DebugStorageInspector) : ViewModel() {
    private val state = MutableStateFlow(InspectionUiState())
    val inspection = state.asStateFlow()

    fun inspect() {
        if (state.value.loading) return
        state.value = InspectionUiState(loading = true)
        viewModelScope.launch {
            try {
                state.value = InspectionUiState(snapshot = inspector.inspect())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = InspectionUiState(failed = true)
            }
        }
    }
}

@Composable
internal fun StorageInspection(state: InspectionUiState, inspect: () -> Unit) {
    Text("Debug database inspection. No personal content is decrypted, logged or exported.")
    SecondaryButton("Refresh database inspection (debug)", inspect, enabled = !state.loading)
    if (state.loading) Text("Inspecting database…")
    if (state.failed) Text("Database inspection unavailable. No records were changed.")
    state.snapshot?.let { snapshot ->
        Text("Stored schema version: ${snapshot.schemaVersion}")
        snapshot.rowCounts.forEach { (table, count) -> Text("$table: $count") }
        Text("Foreign-key enforcement: ${if (snapshot.foreignKeysEnabled) "enabled" else "disabled"}")
        Text("Foreign-key check: ${if (snapshot.foreignKeysValid) "passed" else "failed"}")
        Text("SQLite quick check: ${if (snapshot.quickCheckPassed) "passed" else "failed"}")
        Text("Snapshot of stored rows; refresh after changes. SQLite checks only; encryption keys and application rules " +
            "are not verified.")
    }
}
