package dev.metis.agent.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.R
import dev.metis.agent.data.storage.FoundationRepositories
import dev.metis.agent.domain.storage.SavedFocusSession
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedReminder
import dev.metis.agent.domain.storage.SavedRoutine
import dev.metis.agent.domain.storage.SavedUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal data class FoundationUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val profiles: List<SavedUserProfile> = emptyList(),
    val people: List<SavedPerson> = emptyList(),
    val reminders: List<SavedReminder> = emptyList(),
    val routines: List<SavedRoutine> = emptyList(),
    val focus: List<SavedFocusSession> = emptyList(),
)

internal class FoundationRecordsViewModel(private val stores: FoundationRepositories) : ViewModel() {
    private val state = MutableStateFlow(FoundationUiState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        state.value = FoundationUiState()
        observation = viewModelScope.launch {
            try {
                combine(stores.userProfile.observe(), stores.person.observe(), stores.reminder.observe(),
                    stores.routine.observe(), stores.focusSession.observe(),
                ) { profiles, people, reminders, routines, focus ->
                    FoundationUiState(false, profiles = profiles, people = people, reminders = reminders,
                        routines = routines, focus = focus)
                }.collect { state.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = FoundationUiState(loading = false, failed = true)
            }
        }
    }
}

@Composable
internal fun FoundationRecords(destination: ShellDestination) {
    val context = LocalContext.current.applicationContext
    val model: FoundationRecordsViewModel = viewModel(factory = viewModelFactory {
        initializer { FoundationRecordsViewModel(PersonalStorage.repository(context).foundation) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    when {
        state.loading -> Text(stringResource(R.string.storage_loading))
        state.failed -> dev.metis.agent.presentation.designsystem.ErrorState(
            stringResource(R.string.storage_error), model::reload,
        )
        else -> FoundationContent(destination, state)
    }
}

@Composable
private fun FoundationContent(destination: ShellDestination, records: FoundationUiState) {
    when (destination) {
        ShellDestination.YOU -> SavedPeople(records)
        ShellDestination.PLAN -> SavedTimeRecords(records)
        ShellDestination.TODAY -> {
            if (records.focus.isNotEmpty()) Text(stringResource(R.string.saved_focus_inactive))
            records.focus.forEach { Text(it.outcome) }
        }
        else -> Unit
    }
}

@Composable
private fun SavedPeople(records: FoundationUiState) {
    records.profiles.forEach { Text(it.displayName); Text("${it.zoneId} · ${it.locale}") }
    if (records.people.isNotEmpty()) Text(stringResource(R.string.saved_people))
    records.people.forEach { Text(it.displayName) }
}

@Composable
private fun SavedTimeRecords(records: FoundationUiState) {
    if (records.reminders.isNotEmpty()) Text(stringResource(R.string.saved_reminders_inactive))
    records.reminders.forEach { Text(it.title); Text("${it.localDateTime} · ${it.zoneId}") }
    if (records.routines.isNotEmpty()) Text(stringResource(R.string.saved_routines_inactive))
    records.routines.forEach { Text(it.name) }
}
