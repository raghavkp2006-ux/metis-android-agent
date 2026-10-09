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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.metis.agent.R
import dev.metis.agent.platform.VoiceRuntime

@Composable
internal fun VoiceSettingsWorkspace() {
    val context = LocalContext.current.applicationContext
    var onlineAllowed by remember(context) { mutableStateOf(VoiceRuntime.onlineFallbackAllowed(context)) }
    Column {
        Text(stringResource(R.string.voice_settings_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.voice_settings_description), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (onlineAllowed) R.string.voice_online_enabled else R.string.voice_online_disabled),
                Modifier.weight(1f))
            Switch(checked = onlineAllowed, onCheckedChange = { allowed ->
                onlineAllowed = allowed
                VoiceRuntime.setOnlineFallbackAllowed(context, allowed)
            }, modifier = Modifier.testTag("voice_online_toggle").semantics {
                contentDescription = context.getString(R.string.voice_online_fallback)
            })
        }
    }
}
