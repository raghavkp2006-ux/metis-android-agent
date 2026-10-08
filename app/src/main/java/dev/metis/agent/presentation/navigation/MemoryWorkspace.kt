package dev.metis.agent.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.R
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemoryScore
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.MemorySearchResult
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.LoadingState
import dev.metis.agent.presentation.designsystem.MemoryRow
import dev.metis.agent.presentation.designsystem.SecondaryButton

@Composable
internal fun MemoryWorkspace(
    records: RecordsUiState, onSearch: (String) -> Unit, onSearchMore: () -> Unit,
    onFilter: (MemoryOrigin?) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val model: MemoryEditorViewModel = viewModel(factory = viewModelFactory {
        initializer { MemoryEditorViewModel(PersonalStorage.repository(context).memoryEngine) }
    })
    val editor by model.uiState.collectAsStateWithLifecycle()
    // Never restore personal edit content through Android saved-instance state.
    DisposableEffect(model) { onDispose { model.cancel() } }
    PreferenceRecords(records.preferences)
    SecondaryButton(stringResource(R.string.memory_add), { model.edit() }, Modifier.testTag("memory_add"))
    SecondaryButton(stringResource(R.string.memory_expired), model::reviewExpiry)
    if (editor.busy) Text(stringResource(R.string.memory_operation_busy))
    if (editor.finished) Text(stringResource(R.string.memory_operation_done))
    if (editor.conflict) Text(stringResource(R.string.memory_conflict))
    if (editor.error) Text(stringResource(R.string.storage_error))
    MemorySearchBox(records.search.query, onSearch)
    MemoryOriginFilter(records.search.origin, onFilter)
    when {
        records.search.query.isBlank() -> MemoryArchive(
            records.memories.filter { records.search.origin == null || it.origin == records.search.origin },
            emptyList(), model,
        )
        records.search.loading -> LoadingState(stringResource(R.string.memory_search_loading))
        records.search.failed -> ErrorState(stringResource(R.string.storage_error), { onSearch(records.search.query) })
        else -> records.search.result?.let { MemoryResults(it, onSearchMore, model) }
    }
    MemoryEditDialogs(editor, model)
}

@Composable
private fun MemorySearchBox(query: String, onSearch: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query, onValueChange = onSearch, singleLine = true,
        label = { Text(stringResource(R.string.memory_search_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(force = true) }),
    )
}

@Composable
private fun MemoryResults(result: MemorySearchResult, onSearchMore: () -> Unit, model: MemoryEditorViewModel) {
    if (result.candidatesTruncated) {
        Text(pluralStringResource(R.plurals.memory_search_candidates_limited,
            result.candidateLimit, result.candidateLimit))
        if (result.candidateLimit < MemorySearchQuery.MAX_CANDIDATES) {
            SecondaryButton(stringResource(R.string.memory_search_more), onSearchMore)
        }
    }
    if (result.matchesTruncated) Text(stringResource(R.string.memory_search_results_limited))
    if (result.memories.isEmpty()) EmptyState(
        stringResource(R.string.memory_search_empty), stringResource(R.string.memory_search_help),
    ) else MemoryArchive(result.memories, result.scores, model)
}

@Composable
private fun MemoryArchive(memories: List<SavedMemory>, scores: List<MemoryScore>, model: MemoryEditorViewModel) {
    if (memories.isEmpty()) EmptyState(
        stringResource(R.string.memories_empty), stringResource(R.string.memory_add_help),
    )
    memories.forEach { memory ->
        val origin = stringResource(
            if (memory.origin == MemoryOrigin.EXPLICIT) R.string.memory_explicit else R.string.memory_derived,
        )
        MemoryRow(memory.content, stringResource(R.string.memory_metadata, origin, memory.type.name))
        MemoryDates(memory)
        scores.firstOrNull { it.id == memory.metadata.id }?.let { score ->
            Text(stringResource(R.string.memory_rank, (score.total * PERCENT_SCALE).toInt(),
                (score.relevance * PERCENT_SCALE).toInt(), (score.importance * PERCENT_SCALE).toInt(),
                (score.recency * PERCENT_SCALE).toInt(), (score.relationship * PERCENT_SCALE).toInt(),
                (score.confidence * PERCENT_SCALE).toInt()))
        }
        if (memory.origin == MemoryOrigin.EXPLICIT) {
            SecondaryButton(stringResource(R.string.memory_edit), { model.edit(memory) },
                Modifier.testTag("memory_edit_${memory.metadata.id}"))
        } else Text(stringResource(R.string.memory_derived_read_only))
        SecondaryButton(stringResource(R.string.memory_delete), { model.forget(memory) },
            Modifier.testTag("memory_delete_${memory.metadata.id}"))
    }
}

private const val PERCENT_SCALE = 100

@Composable
private fun MemoryDates(memory: SavedMemory) {
    Text(java.time.Instant.ofEpochMilli(memory.metadata.updatedAt).toString())
    memory.expiresAt?.let { expiry ->
        Text(stringResource(R.string.memory_expiry_at, java.time.Instant.ofEpochMilli(expiry).toString()))
        if (expiry <= System.currentTimeMillis()) Text(stringResource(R.string.memory_is_expired))
    }
}
