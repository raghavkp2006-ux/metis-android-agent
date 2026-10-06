package dev.metis.agent.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.metis.agent.R
import dev.metis.agent.presentation.designsystem.AgentComposer
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.designsystem.ComposerState
import dev.metis.agent.presentation.designsystem.EmptyState

@Composable
fun FoundationScreen() {
    AgentTheme {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(AgentSpacing.screen)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(AgentSpacing.large),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.welcome_description))
                Text(stringResource(R.string.scaffold_status))
                EmptyState(
                    stringResource(R.string.foundation_empty_title),
                    stringResource(R.string.foundation_empty_description),
                )
                AgentComposer("", onValueChange = {}, onSubmit = {}, state = ComposerState(enabled = false))
                Text(stringResource(R.string.composer_unavailable), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.privacy_status), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun FoundationScreenPreview() {
    FoundationScreen()
}
