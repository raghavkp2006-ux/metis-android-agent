package dev.metis.agent.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import dev.metis.agent.R

data class ComposerState(
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val listening: Boolean = false,
    val error: String? = null,
)

@Composable
fun AgentComposer(
    value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit,
    modifier: Modifier = Modifier, state: ComposerState = ComposerState(),
    onMicrophoneClick: (() -> Unit)? = null,
) {
    val canSubmit = state.enabled && !state.busy && !state.listening && value.isNotBlank()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.enabled && !state.busy && !state.listening,
            label = { Text(stringResource(R.string.composer_label)) },
            isError = state.error != null, shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSubmit) onSubmit() }),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            label = stringResource(R.string.action_submit), onClick = onSubmit,
            enabled = canSubmit, loading = state.busy, modifier = Modifier.fillMaxWidth(),
        )
        onMicrophoneClick?.let { microphoneClick ->
            SecondaryButton(
                label = stringResource(if (state.listening) R.string.action_stop_listening else R.string.action_voice),
                onClick = microphoneClick, enabled = state.enabled && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.listening) Text(stringResource(R.string.state_listening))
    }
}
