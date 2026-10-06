package dev.metis.agent.presentation.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import dev.metis.agent.R

@Composable
fun PrimaryButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false,
) {
    val working = stringResource(R.string.state_working)
    Button(
        onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = AgentSpacing.touchTarget)
            .semantics { if (loading) stateDescription = working },
    ) { ButtonLabel(label, loading) }
}

@Composable
fun SecondaryButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false,
) {
    val working = stringResource(R.string.state_working)
    OutlinedButton(
        onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = AgentSpacing.touchTarget)
            .semantics { if (loading) stateDescription = working },
    ) { ButtonLabel(label, loading) }
}

@Composable
private fun ButtonLabel(label: String, loading: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(AgentSpacing.roomy))
        Text(label, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun AgentIconButton(
    icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.defaultMinSize(AgentSpacing.touchTarget, AgentSpacing.touchTarget),
    ) { Icon(icon, contentDescription = label) }
}
