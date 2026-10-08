package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.storage.MemoryEngine
import dev.metis.agent.domain.storage.MemoryExpiryChoice
import dev.metis.agent.domain.storage.MemoryExpiryReview
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemoryType
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class MemoryDraft(
    val original: SavedMemory? = null,
    val content: String = original?.content.orEmpty(),
    val type: MemoryType = original?.type ?: MemoryType.SEMANTIC,
    val importance: Float = original?.importance ?: DEFAULT_IMPORTANCE,
    val expiry: MemoryExpiryChoice = if (original == null) MemoryExpiryChoice.FOREVER else MemoryExpiryChoice.KEEP,
)

private const val DEFAULT_IMPORTANCE = 0.5f

internal data class MemoryEditorState(
    val draft: MemoryDraft? = null,
    val deleting: SavedMemory? = null,
    val review: MemoryExpiryReview? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
    val conflict: Boolean = false,
    val finished: Boolean = false,
)

/** Personal edit drafts stay in RAM; never write them to SavedStateHandle or preferences. */
internal class MemoryEditorViewModel(private val engine: MemoryEngine) : ViewModel() {
    private val state = MutableStateFlow(MemoryEditorState())
    val uiState = state.asStateFlow()

    fun edit(memory: SavedMemory? = null) {
        if (state.value.busy) return
        require(memory == null || memory.origin == MemoryOrigin.EXPLICIT)
        state.value = MemoryEditorState(draft = MemoryDraft(memory))
    }

    fun change(draft: MemoryDraft) {
        if (!state.value.busy && state.value.draft != null) state.value = state.value.copy(draft = draft)
    }

    fun cancel() { if (!state.value.busy) state.value = MemoryEditorState() }
    fun forget(memory: SavedMemory) {
        if (!state.value.busy) state.value = MemoryEditorState(deleting = memory)
    }

    fun save() {
        val draft = state.value.draft ?: return
        if (draft.content.isBlank()) return
        execute {
            engine.saveExplicit(draft.content, draft.type, draft.importance, draft.expiry, draft.original)
            MemoryEditorState(finished = true)
        }
    }

    fun delete() {
        val memory = state.value.deleting ?: return
        execute { engine.forget(memory); MemoryEditorState(finished = true) }
    }

    fun reviewExpiry() = execute { MemoryEditorState(review = engine.reviewExpired()) }
    fun deleteExpired() {
        val review = state.value.review ?: return
        execute { engine.deleteExpired(review); MemoryEditorState(finished = true) }
    }

    private fun execute(operation: suspend () -> MemoryEditorState) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, error = false, conflict = false, finished = false)
        viewModelScope.launch {
            try {
                state.value = operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RevisionConflictException) {
                state.value = MemoryEditorState(conflict = true)
            } catch (_: Exception) {
                state.value = MemoryEditorState(error = true)
            }
        }
    }
}
