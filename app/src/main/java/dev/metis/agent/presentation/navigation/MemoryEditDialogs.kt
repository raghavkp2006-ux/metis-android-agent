package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.metis.agent.R
import dev.metis.agent.domain.storage.MemoryExpiryChoice
import dev.metis.agent.domain.storage.MemoryType

@Composable
internal fun MemoryEditDialogs(state: MemoryEditorState, model: MemoryEditorViewModel) {
    state.draft?.let { draft -> EditMemoryDialog(draft, state.busy, model::change, model::save, model::cancel) }
    if (state.deleting != null || state.review != null) {
        val review = state.review
        val count = review?.candidates?.size ?: 1
        AlertDialog(
            onDismissRequest = model::cancel,
            title = { Text(stringResource(if (review == null) R.string.memory_delete else R.string.memory_expired)) },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.deleting?.let { Text(it.content) }
                    Text(pluralStringResource(R.plurals.memory_delete_count, count, count))
                    Text(stringResource(R.string.memory_delete_warning))
                    if (review?.hasMore == true) Text(stringResource(R.string.memory_expiry_more))
                }
            },
            confirmButton = {
                Button(onClick = if (review == null) model::delete else model::deleteExpired,
                    enabled = !state.busy && count > 0,
                ) { Text(stringResource(R.string.memory_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = model::cancel, enabled = !state.busy) {
                    Text(stringResource(R.string.memory_cancel))
                }
            },
        )
    }
}

@Composable
private fun EditMemoryDialog(
    draft: MemoryDraft, busy: Boolean, change: (MemoryDraft) -> Unit, save: () -> Unit, cancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(stringResource(if (draft.original == null) R.string.memory_add else R.string.memory_edit)) },
        text = {
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.memory_edit_privacy))
                OutlinedTextField(value = draft.content,
                    onValueChange = { change(draft.copy(content = it.take(MAX_MEMORY_LENGTH))) },
                    enabled = !busy, label = { Text(stringResource(R.string.memory_content)) },
                    modifier = Modifier.testTag("memory_content"), minLines = 2,
                )
                MemoryChoice(stringResource(R.string.memory_type), draft.type, MemoryType.entries, !busy) {
                    change(draft.copy(type = it))
                }
                MemoryChoice(stringResource(R.string.memory_retention), draft.expiry,
                    if (draft.original == null) MemoryExpiryChoice.entries.filter { it != MemoryExpiryChoice.KEEP }
                    else MemoryExpiryChoice.entries, !busy,
                ) { change(draft.copy(expiry = it)) }
                Text(stringResource(R.string.memory_working_expiry))
                MemoryImportance(draft, !busy, change)
            }
        },
        confirmButton = {
            Button(onClick = save, enabled = !busy && draft.content.isNotBlank()) {
                Text(stringResource(R.string.memory_save))
            }
        },
        dismissButton = {
            TextButton(onClick = cancel, enabled = !busy) { Text(stringResource(R.string.memory_cancel)) }
        },
    )
}

@Composable
private fun MemoryImportance(draft: MemoryDraft, enabled: Boolean, change: (MemoryDraft) -> Unit) {
    MemoryChoice(stringResource(R.string.memory_importance), draft.importance,
        listOf(0f, LOW_IMPORTANCE, MEDIUM_IMPORTANCE, HIGH_IMPORTANCE, 1f),
        enabled, { change(draft.copy(importance = it)) },
    )
}

private const val MAX_MEMORY_LENGTH = 4_000
private const val LOW_IMPORTANCE = 0.25f
private const val MEDIUM_IMPORTANCE = 0.5f
private const val HIGH_IMPORTANCE = 0.75f

@Composable
private fun <T> MemoryChoice(title: String, current: T, choices: List<T>, enabled: Boolean, select: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(R.string.memory_choice, title, memoryChoiceLabel(current)))
        }
        androidx.compose.material3.DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(memoryChoiceLabel(choice)) },
                    onClick = { expanded = false; select(choice) },
                )
            }
        }
    }
}

@Composable
private fun <T> memoryChoiceLabel(choice: T): String = when (choice) {
    MemoryExpiryChoice.KEEP -> stringResource(R.string.memory_expiry_keep)
    MemoryExpiryChoice.FOREVER -> stringResource(R.string.memory_expiry_forever)
    MemoryExpiryChoice.DAY -> stringResource(R.string.memory_expiry_day)
    MemoryExpiryChoice.WEEK -> stringResource(R.string.memory_expiry_week)
    else -> choice.toString().lowercase().replaceFirstChar { it.titlecase() }
}
