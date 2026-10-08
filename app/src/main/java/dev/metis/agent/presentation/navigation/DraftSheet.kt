package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.metis.agent.R
import dev.metis.agent.presentation.designsystem.AgentComposer
import dev.metis.agent.presentation.designsystem.AgentMessage
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.ComposerState
import dev.metis.agent.presentation.designsystem.SecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftSheet(
    draft: String, onDraft: (String) -> Unit, onClose: () -> Unit,
    request: RequestUiState, onSubmit: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                value = draft, onValueChange = onDraft, onSubmit = onSubmit,
                state = ComposerState(busy = request.busy),
            )
            request.result?.let { AgentMessage(it.message, Modifier.testTag("request_result")) }
            Text(stringResource(R.string.draft_length, draft.length, ShellViewModel.MAX_DRAFT_LENGTH))
            SecondaryButton(stringResource(R.string.action_close_draft), onClose, Modifier.fillMaxWidth())
        }
    }
}
