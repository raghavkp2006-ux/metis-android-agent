package dev.metis.agent.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import dev.metis.agent.R

@Composable
fun TaskRow(
    title: String, metadata: String, completed: Boolean, onCompletedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = AgentSpacing.touchTarget)
            .toggleable(
                value = completed, enabled = onCompletedChange != null, role = Role.Checkbox,
                onValueChange = { onCompletedChange?.invoke(it) },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small),
    ) {
        Checkbox(checked = completed, onCheckedChange = null, enabled = onCompletedChange != null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgentSpacing.tiny)) {
            Text(title)
            Text(
                metadata, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ScheduleBlock(
    title: String, timeRange: String, status: String,
    modifier: Modifier = Modifier, conflict: String? = null,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AgentSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AgentSpacing.small),
        ) {
            Text(timeRange, style = MaterialTheme.typography.labelLarge)
            Text(title)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            conflict?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun MemoryRow(
    content: String, provenance: String, modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
        Text(content)
        Text(
            provenance, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        onEdit?.let { SecondaryButton(stringResource(R.string.action_edit), it) }
        onDelete?.let { SecondaryButton(stringResource(R.string.action_delete), it) }
    }
}

@Composable
fun TimelineRow(title: String, timestamp: String, outcome: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AgentSpacing.tiny)) {
        Text(title)
        Text(timestamp, style = MaterialTheme.typography.bodySmall)
        Text(outcome, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
