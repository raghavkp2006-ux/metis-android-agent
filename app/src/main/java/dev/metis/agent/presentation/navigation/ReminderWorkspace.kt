package dev.metis.agent.presentation.navigation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.domain.agent.AcceptedReminderStore
import dev.metis.agent.domain.agent.ReminderHistoryEntry
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.platform.AndroidReminderPlatform
import dev.metis.agent.platform.ReminderRuntime
import dev.metis.agent.presentation.designsystem.SecondaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ReminderWorkspaceState(
    val entries: List<ReminderHistoryEntry> = emptyList(), val busy: Boolean = false,
    val failed: Boolean = false, val message: String? = null, val loading: Boolean = true,
)

internal class ReminderWorkspaceModel(private val store: AcceptedReminderStore) : ViewModel() {
    private val state = MutableStateFlow(ReminderWorkspaceState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    init { reload() }

    fun reload() {
        observation?.cancel()
        state.value = state.value.copy(loading = true)
        observation = viewModelScope.launch {
            try {
                store.reconcile()
                store.history().collect { state.value = state.value.copy(entries = it, failed = false,
                    loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                state.value = state.value.copy(entries = emptyList(), failed = true, loading = false)
            }
        }
    }

    fun retry(entry: ReminderHistoryEntry) = perform { store.retry(entry.actionId).reason }
    fun cancel(entry: ReminderHistoryEntry) = perform {
        val result = entry.receipt?.let { store.undo(it) } ?: store.cancel(entry.actionId)
        if (result == UndoResult.UNDONE) "Reminder registration cancelled and verified."
        else "Cancellation unavailable: the reminder changed or delivery already occurred."
    }

    private fun perform(operation: suspend () -> String) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true)
        viewModelScope.launch {
            val message = try { withContext(NonCancellable) { operation() } }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                "Reminder outcome could not be verified. Reload before retrying."
            }
            state.value = state.value.copy(busy = false, message = message)
        }
    }
}

@Composable
internal fun ReminderWorkspace() {
    val context = LocalContext.current
    val platform = remember(context) { AndroidReminderPlatform(context) }
    val model: ReminderWorkspaceModel = viewModel(factory = viewModelFactory {
        initializer { ReminderWorkspaceModel(ReminderRuntime.store(context)) }
    })
    var available by remember { mutableStateOf(platform.notificationsAvailable()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        available = platform.notificationsAvailable()
        model.reload()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { available = platform.notificationsAvailable(); model.reload() }
    val state by model.uiState.collectAsStateWithLifecycle()
    Text("Accepted reminders")
    Text("Approximate background notifications may be late. A registered reminder does not prove delivery or reading.")
    Text(if (available) "Notifications enabled." else "Notifications unavailable. Enable them, then submit a fresh " +
        "request.")
    if (!available && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) SecondaryButton("Enable notifications", {
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    })
    SecondaryButton("Notification settings", {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,
            context.packageName))
    })
    when {
        state.loading -> Text("Checking reminder registrations…")
        state.failed -> {
            Text("Reminders could not be read or reconciled.")
            SecondaryButton("Reload reminders", model::reload)
        }
        else -> {
            if (state.entries.isEmpty()) Text("No accepted reminders yet.")
            state.entries.take(MAX_ROWS).forEach { ReminderRow(it, state.busy, model) }
        }
    }
    state.message?.let { Text(it) }
}
private const val MAX_ROWS = 20

@Composable
private fun ReminderRow(entry: ReminderHistoryEntry, busy: Boolean, model: ReminderWorkspaceModel) {
    Text(entry.title)
    Text(entry.trigger)
    Text(if (entry.status == "FIRED") "Notification posting verified; reading is unknown." else entry.status)
    if (entry.pending) SecondaryButton("Retry accepted reminder", { model.retry(entry) }, enabled = !busy)
    if (entry.pending || entry.receipt != null) SecondaryButton("Cancel accepted reminder",
        { model.cancel(entry) }, enabled = !busy)
    if (entry.status in setOf("FAILED", "DENIED")) Text("Submit a fresh reminder request to schedule again.")
}
