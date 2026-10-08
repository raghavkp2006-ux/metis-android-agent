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
        )
        if (state.composerOpen) DraftSheet(state.draft, viewModel::updateDraft, viewModel::closeComposer)
    }
}

@Composable
internal fun NavigationShell(
    state: ShellUiState, onSelect: (ShellDestination) -> Unit, onComposer: () -> Unit,
    onPrivacy: () -> Unit, onBack: () -> Unit,
    records: RecordsUiState = RecordsUiState(), onReload: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onSearchMore: () -> Unit = {},
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
