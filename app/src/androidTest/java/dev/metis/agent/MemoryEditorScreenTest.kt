package dev.metis.agent

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.SavedMemory
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MemoryEditorScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun userCanAddEditAndConfirmDeletionAcrossRecreation(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val content = "Synthetic editor fact ${UUID.randomUUID()}"
        var id: String? = null
        try {
            openYou()
            composeRule.onNodeWithTag("memory_add").performScrollTo().performClick()
            composeRule.onNodeWithText("Save memory").assertIsNotEnabled()
            composeRule.onNodeWithTag("memory_content").performTextReplacement(content)
            composeRule.onNodeWithText("Save memory").performClick()
            composeRule.waitUntil(TIMEOUT) { !hasTag("memory_content") && hasText(content) }
            val saved = repository.observeMemories().first().single { it.content == content }
            id = saved.metadata.id
            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(TIMEOUT) { hasText(content) }
            composeRule.onNodeWithTag("memory_edit_$id").performScrollTo().performClick()
            composeRule.onNodeWithTag("memory_content").performTextReplacement("$content updated")
            composeRule.onNodeWithText("Save memory").performClick()
            composeRule.waitUntil(TIMEOUT) { !hasTag("memory_content") && hasText("$content updated") }
            val changed = repository.observeMemories().first().single { it.metadata.id == id }
            assertEquals(1L, changed.metadata.revision)
            composeRule.onNodeWithTag("memory_delete_$id").performScrollTo().performClick()
            composeRule.onNodeWithText("Cancel").performClick()
            assertEquals(changed, repository.observeMemories().first().single { it.metadata.id == id })
            composeRule.onNodeWithTag("memory_delete_$id").performScrollTo().performClick()
            composeRule.onNodeWithText("Confirm deletion").performClick()
            composeRule.waitUntil(TIMEOUT) { !hasText("$content updated") }
            assertTrue(repository.observeMemories().first().none { it.metadata.id == id })
        } finally {
            closeDialog()
            // Only this test's synthetic record; account for a failure before its id was captured.
            repository.observeMemories().first().filter { it.metadata.id == id || it.content.startsWith(content) }
                .forEach { repository.deleteMemory(it.metadata.id, it.metadata.revision) }
        }
    }

    @Test
    fun derivedFilterAndExpiryReviewNeverEditProvenanceOrDeleteWithoutConfirmation(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val expired = SavedMemory("Synthetic expired UI fact", expiresAt = 1)
        val derived = SavedMemory("Synthetic derived UI fact", origin = MemoryOrigin.DERIVED)
        try {
            repository.saveMemory(expired)
            repository.saveMemory(derived)
            openYou()
            composeRule.waitUntil(TIMEOUT) { hasText(derived.content) }
            composeRule.onNodeWithText("Show: All memories").performScrollTo().performClick()
            composeRule.onNodeWithText("Derived items").performClick()
            composeRule.waitUntil(TIMEOUT) { !hasText(expired.content) && hasText(derived.content) }
            assertTrue(!hasTag("memory_edit_${derived.metadata.id}"))
            composeRule.onNodeWithText("Review expired memories").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) { hasText("Confirm deletion") }
            assertEquals(2, repository.observeMemories().first().size)
            composeRule.onNodeWithText("Cancel").performClick()
            assertEquals(2, repository.observeMemories().first().size)
            composeRule.onNodeWithText("Review expired memories").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) { hasText("Confirm deletion") }
            composeRule.onNodeWithText("Confirm deletion").performClick()
            composeRule.waitUntil(TIMEOUT) { !hasText("Confirm deletion") }
            assertEquals(listOf(derived), repository.observeMemories().first())
        } finally {
            closeDialog()
            listOf(expired.metadata.id, derived.metadata.id).forEach { id ->
                repository.database.records().memory(id)?.let { repository.deleteMemory(id, it.metadata.revision) }
            }
        }
    }

    @Test
    fun unsavedEditsAreDiscardedOnRecreationAndConcurrentUpdatesAreNotOverwritten(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val original = SavedMemory("Synthetic stale edit fact")
        try {
            repository.saveMemory(original)
            openYou()
            composeRule.waitUntil(TIMEOUT) { hasText(original.content) }
            composeRule.onNodeWithTag("memory_edit_${original.metadata.id}").performScrollTo().performClick()
            composeRule.onNodeWithTag("memory_content").performTextReplacement("Synthetic unsaved private draft")
            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(TIMEOUT) { !hasTag("memory_content") && hasText(original.content) }
            assertEquals(listOf(original), repository.observeMemories().first())
            composeRule.onNodeWithTag("memory_edit_${original.metadata.id}").performScrollTo().performClick()
            composeRule.onNodeWithTag("memory_content").performTextReplacement("Synthetic stale replacement")
            repository.saveMemory(original.copy(content = "Synthetic external update"))
            composeRule.onNodeWithText("Save memory").performClick()
            composeRule.waitUntil(TIMEOUT) { hasText("This memory changed. Reload it before trying again.") }
            assertEquals("Synthetic external update", repository.observeMemories().first().single().content)
        } finally {
            closeDialog()
            repository.database.records().memory(original.metadata.id)?.let {
                repository.deleteMemory(original.metadata.id, it.metadata.revision)
            }
        }
    }

    private fun openYou() { composeRule.onNodeWithTag("nav_YOU").performClick() }
    private fun closeDialog() { if (hasText("Cancel")) composeRule.onNodeWithText("Cancel").performClick() }
    private fun hasText(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private companion object { const val TIMEOUT = 10_000L }
}
