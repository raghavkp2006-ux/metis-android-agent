package dev.metis.agent.presentation.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import dev.metis.agent.R
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.platform.AndroidVoiceInput
import dev.metis.agent.platform.VoiceInputMessage
import dev.metis.agent.platform.VoiceInputPhase
import dev.metis.agent.platform.VoiceRuntime
import dev.metis.agent.presentation.designsystem.AgentComposer
import dev.metis.agent.presentation.designsystem.AgentMessage
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.ComposerState
import dev.metis.agent.presentation.designsystem.SecondaryButton
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftSheet(
    draft: String, onDraft: (String) -> Unit, onClose: () -> Unit,
    request: RequestUiState, onSubmit: (InputSource) -> Unit,
    onAccept: (ActionProposal) -> Unit = {}, onUndo: (ActionReceipt) -> Unit = {},
) {
    val accepting = rememberUpdatedState(request.accepting)
    val context = LocalContext.current
    val currentDraft = rememberUpdatedState(draft)
    val currentOnDraft = rememberUpdatedState(onDraft)
    val voicePhase = remember { mutableStateOf(VoiceInputPhase.IDLE) }
    val voiceMessage = remember { mutableStateOf<String?>(null) }
    val onlineRetryAvailable = remember { mutableStateOf(false) }
    val source = remember { mutableStateOf(InputSource.TEXT) }
    val showVoicePurpose = remember { mutableStateOf(false) }
    val originalDraft = remember { mutableStateOf("") }
    val originalSource = remember { mutableStateOf(InputSource.TEXT) }
    val controller = remember(context) {
        AndroidVoiceInput(
            context,
            onTranscript = { transcript ->
                source.value = InputSource.VOICE
                currentOnDraft.value(transcript.take(ShellViewModel.MAX_DRAFT_LENGTH))
                onlineRetryAvailable.value = false
                voiceMessage.value = if (transcript.length > ShellViewModel.MAX_DRAFT_LENGTH) {
                    context.getString(R.string.voice_transcript_too_long)
                } else null
            },
            onPhaseChanged = { voicePhase.value = it },
            onMessage = { message ->
                onlineRetryAvailable.value = message == VoiceInputMessage.ONLINE_FALLBACK_AVAILABLE
                voiceMessage.value = context.getString(message.resourceId())
            },
        )
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            voiceMessage.value = null
            controller.start(VoiceRuntime.onlineFallbackAllowed(context))
        } else {
            voiceMessage.value = context.getString(R.string.voice_permission_denied)
        }
    }
    fun startAfterDisclosure() {
        showVoicePurpose.value = false
        originalDraft.value = currentDraft.value
        originalSource.value = source.value
        voiceMessage.value = null
        onlineRetryAvailable.value = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) {
                    controller.start(VoiceRuntime.onlineFallbackAllowed(context))
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !accepting.value }),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.fillMaxWidth().testTag("draft_sheet").navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(AgentSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AgentSpacing.medium),
        ) {
            Text(stringResource(R.string.draft_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.composer_unavailable))
            AgentComposer(
                value = draft,
                onValueChange = {
                    value -> source.value = InputSource.TEXT
                    voiceMessage.value = null
                    onlineRetryAvailable.value = false
                    onDraft(value)
                },
                onSubmit = { onSubmit(source.value) },
                state = ComposerState(
                    busy = request.busy,
                    listening = voicePhase.value != VoiceInputPhase.IDLE,
                    finishingVoice = voicePhase.value == VoiceInputPhase.FINISHING,
                    onlineRetryAvailable = onlineRetryAvailable.value,
                    error = voiceMessage.value,
                ),
                onMicrophoneClick = {
                    if (voicePhase.value == VoiceInputPhase.LISTENING) controller.stop()
                    else showVoicePurpose.value = true
                },
                onCancelVoice = {
                    controller.cancel()
                    currentOnDraft.value(originalDraft.value)
                    source.value = originalSource.value
                    voiceMessage.value = null
                    onlineRetryAvailable.value = false
                },
                onRetryOnline = {
                    onlineRetryAvailable.value = false
                    voiceMessage.value = null
                    controller.start(
                        onlineFallbackAllowed = VoiceRuntime.onlineFallbackAllowed(context),
                        forceOnline = true,
                    )
                },
            )
            request.result?.let {
                AgentMessage(it.message, Modifier.testTag("request_result"))
                TaskRequestActions(it, request.busy, onAccept, onUndo, onClose)
            }
            Text(stringResource(R.string.draft_length, draft.length, ShellViewModel.MAX_DRAFT_LENGTH))
            SecondaryButton(stringResource(R.string.action_close_draft), onClose, Modifier.fillMaxWidth(),
                enabled = !request.accepting)
        }
    }
    if (showVoicePurpose.value) {
        AlertDialog(
            onDismissRequest = { showVoicePurpose.value = false },
            title = { Text(stringResource(R.string.voice_purpose_title)) },
            text = { Text(stringResource(if (VoiceRuntime.onlineFallbackAllowed(context)) {
                R.string.voice_purpose_online
            } else R.string.voice_purpose_offline)) },
            confirmButton = {
                TextButton(onClick = ::startAfterDisclosure) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showVoicePurpose.value = false }) {
                    Text(stringResource(R.string.action_not_now))
                }
            },
        )
    }
}

private fun VoiceInputMessage.resourceId(): Int = when (this) {
    VoiceInputMessage.MICROPHONE_PERMISSION_REQUIRED -> R.string.voice_permission_denied
    VoiceInputMessage.OFFLINE_UNAVAILABLE -> R.string.voice_offline_unavailable
    VoiceInputMessage.NO_PROVIDER -> R.string.voice_no_provider
    VoiceInputMessage.NO_RESULT -> R.string.voice_no_result
    VoiceInputMessage.RECOGNITION_FAILED -> R.string.voice_recognition_failed
    VoiceInputMessage.ONLINE_FALLBACK_AVAILABLE -> R.string.voice_online_retry_available
}
