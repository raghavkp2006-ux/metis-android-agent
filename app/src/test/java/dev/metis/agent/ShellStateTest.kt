package dev.metis.agent

import androidx.lifecycle.SavedStateHandle
import dev.metis.agent.presentation.navigation.ShellDestination
import dev.metis.agent.presentation.navigation.ShellViewModel
import dev.metis.agent.presentation.navigation.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellStateTest {
    @Test
    fun backClosesInputAndDetailBeforeReturningToChatWithoutDiscardingDraft() {
        val model = ShellViewModel(SavedStateHandle())
        model.selectDestination(ShellDestination.YOU)
        model.openPrivacy()
        model.updateDraft("Call example tomorrow")
        model.openComposer()
        model.goBack()
        assertFalse(model.uiState.value.composerOpen)
        assertTrue(model.uiState.value.privacyOpen)
        assertEquals(ShellDestination.YOU, model.uiState.value.destination)
        model.goBack()
        assertFalse(model.uiState.value.privacyOpen)
        assertEquals(ShellDestination.YOU, model.uiState.value.destination)
        model.goBack()
        assertEquals(ShellDestination.AGENT, model.uiState.value.destination)
        assertEquals(Workspace.CHAT, model.uiState.value.workspace)
        assertFalse(model.uiState.value.handlesBack)
        assertEquals("Call example tomorrow", model.uiState.value.draft)
    }

    @Test
    fun savedNavigationAndDraftRestoreTogetherWithDetailSource() {
        val handle = SavedStateHandle()
        val model = ShellViewModel(handle)
        model.selectDestination(ShellDestination.YOU)
        model.openPrivacy()
        model.openComposer()
        model.updateDraft("Prepare for example exam")
        val restored = ShellViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        assertEquals(model.uiState.value, restored.uiState.value)
        restored.selectDestination(ShellDestination.PLAN)
        assertFalse(restored.uiState.value.privacyOpen)
        assertFalse(restored.uiState.value.composerOpen)
        assertEquals("Prepare for example exam", restored.uiState.value.draft)
    }

    @Test
    fun invalidRestoredRouteFallsBackAndDraftIsBoundedBeforeSaving() {
        val model = ShellViewModel(SavedStateHandle(mapOf("shell.destination" to "REMOVED_ROUTE")))
        assertEquals(ShellDestination.AGENT, model.uiState.value.destination)
        assertEquals(Workspace.CHAT, model.uiState.value.workspace)
        model.updateDraft("x".repeat(ShellViewModel.MAX_DRAFT_LENGTH + 1))
        assertEquals(ShellViewModel.MAX_DRAFT_LENGTH, model.uiState.value.draft.length)
        val legacy = ShellViewModel(SavedStateHandle(mapOf("shell.destination" to "PLAN")))
        assertEquals(ShellDestination.PLAN, legacy.uiState.value.destination)
        assertEquals(Workspace.PLAN, legacy.uiState.value.workspace)
    }
}
