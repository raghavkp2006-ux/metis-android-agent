package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.metis.agent.R
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.SecondaryButton

@Composable
internal fun ShellPage(
    state: ShellUiState, onPrivacy: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier,
    records: RecordsUiState = RecordsUiState(), onReload: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AgentSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(AgentSpacing.large),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(if (state.privacyOpen) R.string.privacy_title else state.destination.label),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("screen_title").semantics { heading() },
        )
        if (state.privacyOpen) {
            Text(stringResource(R.string.privacy_status))
            Text(stringResource(R.string.privacy_detail))
            Text(stringResource(R.string.draft_privacy))
            SecondaryButton(stringResource(R.string.action_back), onBack)
        } else {
            if (state.destination in listOf(ShellDestination.TODAY, ShellDestination.PLAN, ShellDestination.YOU)) {
                SavedRecords(state.destination, records, onReload)
            } else {
                EmptyState(stringResource(R.string.shell_empty_title), stringResource(state.destination.description))
            }
            if (state.destination == ShellDestination.TODAY) Text(stringResource(R.string.scaffold_status))
            if (state.destination == ShellDestination.YOU) {
                SecondaryButton(stringResource(R.string.privacy_title), onPrivacy)
                StorageDeveloperTools(records)
            }
            Text(stringResource(R.string.privacy_status), style = MaterialTheme.typography.bodySmall)
        }
    }
}
