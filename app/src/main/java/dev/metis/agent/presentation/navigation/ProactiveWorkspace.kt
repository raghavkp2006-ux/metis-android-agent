package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.metis.agent.R
import dev.metis.agent.platform.ProactiveRuntime

@Composable
internal fun ProactiveWorkspace() {
    val context = LocalContext.current.applicationContext
    var enabled by remember(context) { mutableStateOf(ProactiveRuntime.enabled(context)) }
    Column {
        Text(stringResource(R.string.proactive_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.proactive_description), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (enabled) R.string.proactive_enabled else R.string.proactive_disabled),
                Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { value ->
                enabled = value
                ProactiveRuntime.setEnabled(context, value)
            }, modifier = Modifier.testTag("proactive_toggle").semantics {
                contentDescription = context.getString(R.string.proactive_title)
            })
        }
    }
}
