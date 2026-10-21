package dev.metis.agent.presentation.navigation

import androidx.core.content.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.decision.DecisionAgent
import dev.metis.agent.platform.ReminderRuntime
import androidx.compose.runtime.LaunchedEffect
import dev.metis.agent.presentation.designsystem.AgentTheme


@Composable
fun AgentApp(viewModel: ShellViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val requestModel: RequestViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val repository = PersonalStorage.repository(context)
            val reminders = ConfirmedReminderAgent(
                ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions, localCalendar = true,
                    readCandidate = ReadCandidate.V16),
                    ReminderRuntime.store(context))
            RequestViewModel(dev.metis.agent.domain.planner.PlannerAgent(DecisionAgent(reminders,
                repository.decisions), repository.planner), reminderClarification = reminders)
        }
    })
    val requestState by requestModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(context) {
        try { ReminderRuntime.store(context).reconcile() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Storage/permission failure remains visible through reminder history; never reset storage.
        }
    }
    val recordsViewModel: RecordsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val repository = PersonalStorage.repository(context)
            RecordsViewModel(repository, repository, repository, repository.dependencies, repository.preferences,
                repository.planning)
        }
    })
    val records by recordsViewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    AgentTheme {
        val preferences = remember(context) {
            context.getSharedPreferences(dev.metis.agent.AppIsolation.name("metis.welcome"),
                android.content.Context.MODE_PRIVATE)
        }
        var welcomed by remember { mutableStateOf(preferences.getBoolean("welcomeCompleted", false)) }
        if (!welcomed) {
            WelcomeScreen {
                preferences.edit { putBoolean("welcomeCompleted", true) }
                welcomed = true
            }
        } else {
            MetisAppShell(
                state = state,
                request = requestState,
                onSelect = {
                    focusManager.clearFocus(); keyboard?.hide()
                    if (viewModel.uiState.value.workspace != it) requestModel.cancelReminderClarification()
                    viewModel.selectWorkspace(it)
                },
                onDraft = viewModel::updateDraft,
                onSubmit = { source ->
                    val current = viewModel.uiState.value
                    if (current.draft.isNotBlank() && !requestModel.uiState.value.busy) {
                        val origin = ConversationOrigin(current.workspace, current.selectedEntity)
                        focusManager.clearFocus()
                        keyboard?.hide()
                        requestModel.submit(current.draft, origin, source)
                        viewModel.updateDraft("")
                        viewModel.selectWorkspace(Workspace.CHAT)
                    }
                },
                onNewChat = {
                    if (!requestModel.uiState.value.accepting) {
                        requestModel.dismiss()
                        viewModel.updateDraft("")
                        viewModel.selectWorkspace(Workspace.CHAT)
                    }
                },
                onAccept = requestModel::accept,
                onUndo = requestModel::undo,
                onReject = requestModel::reject,
                onBack = { requestModel.cancelReminderClarification(); viewModel.goBack() },
                onPrivacy = { requestModel.cancelReminderClarification(); viewModel.openPrivacy() },
                records = records,
                onReload = recordsViewModel::reload,
                onSearch = recordsViewModel::searchMemories,
                onSearchMore = recordsViewModel::expandSearch,
                onMemoryFilter = recordsViewModel::filterMemory,
                onEntity = { requestModel.cancelReminderClarification(); viewModel.selectEntity(it) },
            )
        }
    }
}
