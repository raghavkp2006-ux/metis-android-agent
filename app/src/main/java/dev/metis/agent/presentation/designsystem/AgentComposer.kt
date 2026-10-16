package dev.metis.agent.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import dev.metis.agent.R

@Suppress("CyclomaticComplexMethod") // Rendering supported input/result states.
@Composable
fun AgentComposer(
    value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit,
    modifier: Modifier = Modifier, state: ComposerState = ComposerState(),
    onMicrophoneClick: (() -> Unit)? = null,
    onCancelVoice: (() -> Unit)? = null,
    onRetryOnline: (() -> Unit)? = null,
) {
    val canSubmit = state.enabled && state.submissionAvailable &&
        !state.busy && !state.listening && value.isNotBlank()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag("composer_input"),
            enabled = state.enabled && !state.busy && !state.listening,
            placeholder = { Text(stringResource(R.string.composer_label)) },
            maxLines = 5,
            isError = state.error != null, shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSubmit) onSubmit() }),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
            Text(if (state.listening) stringResource(R.string.state_listening) else "Temporary chat · local agent",
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onMicrophoneClick != null) IconButton(onClick = onMicrophoneClick,
                enabled = state.enabled && !state.busy && !state.finishingVoice,
                modifier = Modifier.size(48.dp).testTag("voice_button")) {
                Icon(if (state.listening) MetisIcons.Stop else MetisIcons.Microphone,
                    stringResource(if (state.listening) R.string.action_stop_listening else R.string.action_voice))
            }
            FilledIconButton(onClick = onSubmit, enabled = canSubmit,
                shape = CircleShape, modifier = Modifier.size(48.dp).testTag("send_request")) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(MetisIcons.Send, stringResource(R.string.action_submit))
            }
        }
        VoiceControls(state, onCancelVoice, onRetryOnline)
    }
}

@Composable
private fun VoiceControls(
    state: ComposerState,
    onCancelVoice: (() -> Unit)?,
    onRetryOnline: (() -> Unit)?,
) {
    if (state.listening) {
        Text(stringResource(if (state.finishingVoice) R.string.state_finishing_voice else R.string.state_listening))
        onCancelVoice?.let {
            SecondaryButton(stringResource(R.string.action_cancel_voice), it, Modifier.fillMaxWidth())
        }
    }
    if (state.onlineRetryAvailable) {
        onRetryOnline?.let {
            SecondaryButton(
                label = stringResource(R.string.action_retry_online),
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
