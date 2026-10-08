package dev.metis.agent

import androidx.room.withTransaction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.RecordMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class MemorySearchScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scopeWarningAllowsExplicitExpansionToOlderRecords(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val memories = (0..20).map { number ->
            SavedMemory(
                if (number == 0) "Synthetic older uniqueterm" else "Synthetic recent record $number",
                metadata = RecordMetadata(createdAt = 0, updatedAt = number.toLong()),
            )
        }
        try {
            repository.database.withTransaction { memories.forEach { repository.saveMemory(it) } }
            composeRule.onNodeWithTag("nav_YOU").performClick()
            composeRule.waitUntil(TIMEOUT) { hasText("Search saved memory") }
            composeRule.onNodeWithText("Search saved memory").performScrollTo().performTextReplacement("uniqueterm")
            composeRule.waitUntil(TIMEOUT) { hasText("No matching memory in the searched records") }
            composeRule.onNodeWithText("Search up to 200 recent records (may take longer)")
                .performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) { hasText(memories.first().content) }
        } finally {
            try {
                hideKeyboard()
            } finally {
                repository.database.withTransaction {
                    memories.forEach { repository.deleteMemory(it.metadata.id, it.metadata.revision) }
                }
            }
        }
    }

    @Test
    fun localSearchSurvivesRecreationAndRefreshesAfterSavedDataChanges(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val chemistry = SavedMemory("Synthetic chemistry revision")
        val mathematics = SavedMemory("Synthetic mathematics revision")
        try {
            repository.saveMemory(chemistry)
            repository.saveMemory(mathematics)
            composeRule.onNodeWithTag("nav_YOU").performClick()
            composeRule.waitUntil(TIMEOUT) { hasText(chemistry.content) && hasText(mathematics.content) }
            composeRule.onNodeWithText("Search saved memory").performScrollTo().performTextReplacement("chemistry")
            composeRule.waitUntil(TIMEOUT) { hasText(chemistry.content) && !hasText(mathematics.content) }
            composeRule.onNodeWithText(chemistry.content).performScrollTo().assertIsDisplayed()
            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(TIMEOUT) { hasText(chemistry.content) && !hasText(mathematics.content) }
            composeRule.onNodeWithText("Search saved memory").performScrollTo().performTextReplacement("mathematics")
            composeRule.waitUntil(TIMEOUT) { hasText(mathematics.content) && !hasText(chemistry.content) }
            repository.saveMemory(mathematics.copy(content = "Synthetic geometry revision"))
            composeRule.waitUntil(TIMEOUT) { hasText("No matching memory in the searched records") }
            composeRule.onNodeWithText("Search saved memory").performScrollTo().performTextReplacement("geometry")
            composeRule.waitUntil(TIMEOUT) { hasText("Synthetic geometry revision") }
            repository.deleteMemory(mathematics.metadata.id, 1)
            composeRule.waitUntil(TIMEOUT) { hasText("No matching memory in the searched records") }
            composeRule.onNodeWithText("Search saved memory").performScrollTo().performTextReplacement("")
            composeRule.waitUntil(TIMEOUT) { hasText(chemistry.content) }
        } finally {
            try {
                hideKeyboard()
            } finally {
                listOf(chemistry.metadata.id, mathematics.metadata.id).forEach { id ->
                    repository.database.records().memory(id)?.let { repository.deleteMemory(id, it.metadata.revision) }
                }
            }
        }
    }

    private fun hasText(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun hideKeyboard() {
        composeRule.onNodeWithText("Search saved memory").performImeAction()
        composeRule.onNodeWithText("Search saved memory").assertIsNotFocused()
    }
    private companion object { const val TIMEOUT = 10_000L }
}
