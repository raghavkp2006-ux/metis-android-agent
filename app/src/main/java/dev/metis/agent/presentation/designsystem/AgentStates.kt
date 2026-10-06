package dev.metis.agent.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.metis.agent.R

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(AgentSpacing.medium),
    ) {
        CircularProgressIndicator()
        Text(label)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(AgentSpacing.small),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        SecondaryButton(stringResource(R.string.action_retry), onRetry)
    }
}

@Composable
fun PermissionExplainer(
    capability: String, reason: String, scope: String, denied: Boolean,
    onEnable: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
        Text(capability, style = MaterialTheme.typography.titleLarge)
        Text(reason)
        Text(scope, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (denied) Text(stringResource(R.string.permission_denied), color = MaterialTheme.colorScheme.error)
        PrimaryButton(stringResource(R.string.action_enable), onEnable, Modifier.fillMaxWidth())
        SecondaryButton(stringResource(R.string.action_not_now), onNotNow, Modifier.fillMaxWidth())
    }
}
