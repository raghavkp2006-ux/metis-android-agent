package dev.metis.agent.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.R
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.platform.ReminderRuntime
import androidx.compose.runtime.LaunchedEffect
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.designsystem.PrimaryButton

private const val RAIL_WIDTH_THRESHOLD = 600
private const val RAIL_HEIGHT_THRESHOLD = 480
private const val RAIL_FONT_THRESHOLD = 1.5f

@Composable
fun AgentApp(viewModel: ShellViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val requestModel: RequestViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val repository = PersonalStorage.repository(context)
            RequestViewModel(ConfirmedReminderAgent(
                ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions),
                    ReminderRuntime.store(context)))
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
    AgentTheme {
        BackHandler(enabled = state.handlesBack && !state.composerOpen, onBack = viewModel::goBack)
        NavigationShell(
            state,
            { focusManager.clearFocus(force = true); viewModel.selectDestination(it) },
            { focusManager.clearFocus(force = true); viewModel.openComposer() },
            { focusManager.clearFocus(force = true); viewModel.openPrivacy() }, viewModel::goBack,
            records, recordsViewModel::reload,
            recordsViewModel::searchMemories,
            recordsViewModel::expandSearch,
            recordsViewModel::filterMemory,
        )
        if (state.composerOpen) DraftSheet(
            state.draft, { requestModel.dismiss(); viewModel.updateDraft(it) },
            {
                if (!requestModel.uiState.value.accepting) { requestModel.dismiss(); viewModel.closeComposer() }
            }, requestState,
            {
                focusManager.clearFocus(force = true)
                val current = viewModel.uiState.value
                if (current.draft.isNotBlank() && !requestModel.uiState.value.busy) {
                    requestModel.submit(current.draft, current.destination)
                    viewModel.updateDraft("")
                }
            }, requestModel::accept, requestModel::undo,
        )
    }
}

@Composable
internal fun NavigationShell(
    state: ShellUiState, onSelect: (ShellDestination) -> Unit, onComposer: () -> Unit,
    onPrivacy: () -> Unit, onBack: () -> Unit,
    records: RecordsUiState = RecordsUiState(), onReload: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onSearchMore: () -> Unit = {},
    onMemoryFilter: (dev.metis.agent.domain.storage.MemoryOrigin?) -> Unit = {},
) {
    val savedPages = rememberSaveableStateHolder()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= RAIL_WIDTH_THRESHOLD.dp || maxHeight < RAIL_HEIGHT_THRESHOLD.dp ||
            LocalDensity.current.fontScale >= RAIL_FONT_THRESHOLD
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = { if (!useRail) ShellNavigation(state.destination, false, onSelect) },
        ) { innerPadding ->
            Row(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                if (useRail) ShellNavigation(state.destination, true, onSelect)
                Column(Modifier.weight(1f)) {
                    savedPages.SaveableStateProvider("${state.destination.name}/${state.privacyOpen}") {
                        ShellPage(
                            state, onPrivacy, onBack, Modifier.weight(1f), records, onReload, onSearch, onSearchMore,
                            onMemoryFilter,
                        )
                    }
                    Surface {
                        PrimaryButton(
                            stringResource(R.string.action_write_request), onComposer,
                            Modifier.fillMaxWidth().padding(AgentSpacing.screen),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun NavigationShellPreview() {
    AgentTheme { NavigationShell(ShellUiState(), {}, {}, {}, {}) }
}
