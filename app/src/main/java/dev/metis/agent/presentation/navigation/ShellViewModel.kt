package dev.metis.agent.presentation.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Transient navigation/unsent draft state only. Request processing belongs to RequestViewModel. */
class ShellViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val restoredDestination = ShellDestination.entries.firstOrNull {
        it.name == savedState.get<String>(DESTINATION)
    } ?: ShellDestination.AGENT
    private val restoredWorkspace = Workspace.entries.firstOrNull {
        it.name == savedState.get<String>(WORKSPACE)
    } ?: restoredDestination.workspace()
    private val state = MutableStateFlow(
        ShellUiState(
            destination = restoredWorkspace.destination(),
            draft = savedState.get<String>(DRAFT).orEmpty().take(MAX_DRAFT_LENGTH),
            composerOpen = savedState[COMPOSER_OPEN] ?: false,
            privacyOpen = savedState[PRIVACY_OPEN] ?: false,
            workspace = restoredWorkspace,
        ),
    )
    val uiState = state.asStateFlow()

    fun selectDestination(destination: ShellDestination) {
        selectWorkspace(destination.workspace())
    }

    fun selectWorkspace(workspace: Workspace) {
        update(state.value.copy(workspace = workspace, destination = workspace.destination(), composerOpen = false,
            privacyOpen = false, selectedEntity = null))
    }

    fun selectEntity(reference: dev.metis.agent.domain.agent.EntityReference?) {
        update(state.value.copy(selectedEntity = reference))
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
                current.selectedEntity != null -> current.copy(selectedEntity = null)
                else -> current.copy(destination = ShellDestination.AGENT, workspace = Workspace.CHAT)
            },
        )
    }

    private fun update(next: ShellUiState) {
        savedState[DESTINATION] = next.destination.name
        savedState[WORKSPACE] = next.workspace.name
        savedState[DRAFT] = next.draft
        savedState[COMPOSER_OPEN] = next.composerOpen
        savedState[PRIVACY_OPEN] = next.privacyOpen
        state.value = next
    }

    companion object {
        // Keep the Android saved-instance-state bundle small; this is not a durable draft store.
        const val MAX_DRAFT_LENGTH = 4_000
        private const val DESTINATION = "shell.destination"
        private const val WORKSPACE = "shell.workspace"
        private const val DRAFT = "shell.draft"
        private const val COMPOSER_OPEN = "shell.composerOpen"
        private const val PRIVACY_OPEN = "shell.privacyOpen"
    }
}

private fun ShellDestination.workspace(): Workspace = when (this) {
    ShellDestination.TODAY -> Workspace.TODAY
    ShellDestination.PLAN -> Workspace.PLAN
    ShellDestination.AGENT -> Workspace.CHAT
    ShellDestination.TIMELINE -> Workspace.ACTIVITY
    ShellDestination.YOU -> Workspace.SETTINGS
}

private fun Workspace.destination(): ShellDestination = when (this) {
    Workspace.CHAT -> ShellDestination.AGENT
    Workspace.TODAY -> ShellDestination.TODAY
    Workspace.HABITS -> ShellDestination.TODAY
    Workspace.PLAN, Workspace.PROJECTS_GOALS -> ShellDestination.PLAN
    Workspace.ACTIVITY -> ShellDestination.TIMELINE
    Workspace.MEMORY, Workspace.PEOPLE, Workspace.INSIGHTS, Workspace.SETTINGS -> ShellDestination.YOU
}
