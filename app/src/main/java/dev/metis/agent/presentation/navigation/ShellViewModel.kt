package dev.metis.agent.presentation.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Transient navigation/draft state only. No parsing, submission, storage, or platform actions. */
class ShellViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val state = MutableStateFlow(
        ShellUiState(
            destination = ShellDestination.entries.firstOrNull { it.name == savedState.get<String>(DESTINATION) }
                ?: ShellDestination.TODAY,
            draft = savedState.get<String>(DRAFT).orEmpty().take(MAX_DRAFT_LENGTH),
            composerOpen = savedState[COMPOSER_OPEN] ?: false,
            privacyOpen = savedState[PRIVACY_OPEN] ?: false,
        ),
    )
    val uiState = state.asStateFlow()

    fun selectDestination(destination: ShellDestination) {
        update(state.value.copy(destination = destination, composerOpen = false, privacyOpen = false))
    }

    fun updateDraft(draft: String) {
        update(state.value.copy(draft = draft.take(MAX_DRAFT_LENGTH)))
    }

    fun openComposer() {
        update(state.value.copy(composerOpen = true))
    }

    fun closeComposer() {
        update(state.value.copy(composerOpen = false))
    }

    fun openPrivacy() {
        update(state.value.copy(privacyOpen = true))
    }

    fun goBack() {
        val current = state.value
        update(
            when {
                current.composerOpen -> current.copy(composerOpen = false)
                current.privacyOpen -> current.copy(privacyOpen = false)
                else -> current.copy(destination = ShellDestination.TODAY)
            },
        )
    }

    private fun update(next: ShellUiState) {
        savedState[DESTINATION] = next.destination.name
        savedState[DRAFT] = next.draft
        savedState[COMPOSER_OPEN] = next.composerOpen
        savedState[PRIVACY_OPEN] = next.privacyOpen
        state.value = next
    }

    companion object {
        // Keep the Android saved-instance-state bundle small; this is not a durable draft store.
        const val MAX_DRAFT_LENGTH = 4_000
        private const val DESTINATION = "shell.destination"
        private const val DRAFT = "shell.draft"
        private const val COMPOSER_OPEN = "shell.composerOpen"
        private const val PRIVACY_OPEN = "shell.privacyOpen"
    }
}
