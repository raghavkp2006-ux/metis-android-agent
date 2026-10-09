package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskActionScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun reviewingCancellingAcceptingAndUndoingUseOneVisibleTaskFlow() {
        val repository = PersonalStorage.repository(composeRule.activity)
        val title = "Synthetic task action ${UUID.randomUUID()}"
        val before = runBlocking { repository.foundation.actionRun.observe().first().map { it.metadata.id }.toSet() }
        try {
            composeRule.onNodeWithText("Write a request").performClick()
            composeRule.onNodeWithText("What do you want to do?").performTextInput("add a task to $title")
            composeRule.onNodeWithText("Send request").performScrollTo().performClick()
            waitForProposal()
            composeRule.onNodeWithTag("task_proposal").performScrollTo().assertIsDisplayed()
            assertTrue(runBlocking { repository.observeTasks().first().none { it.title == title } })
            composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
            assertTrue(runBlocking { repository.observeTasks().first().none { it.title == title } })
            composeRule.onNodeWithText("Write a request").performClick()
            composeRule.onNodeWithText("What do you want to do?").performTextInput("add a task to $title")
            composeRule.onNodeWithText("Send request").performScrollTo().performClick()
            waitForProposal()
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_accept))
                .performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Task saved and verified on this device.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Task saved and verified on this device.").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("undo_created_task").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Created task removed. Undo was verified locally.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Created task removed. Undo was verified locally.")
                .performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("undo_created_task").assertDoesNotExist()
            assertTrue(runBlocking { repository.observeTasks().first().none { it.title == title } })
        } finally {
            runBlocking {
                repository.observeTasks().first().filter { it.title == title }.forEach {
                    repository.deleteTask(it.metadata.id, it.metadata.revision)
                }
                repository.foundation.actionRun.observe().first().filter { it.metadata.id !in before }.forEach {
                    repository.foundation.actionRun.delete(it.metadata.id, it.metadata.revision)
                }
            }
        }
    }
    private fun waitForProposal() = composeRule.waitUntil(TIMEOUT) {
        composeRule.onAllNodesWithTag("task_proposal").fetchSemanticsNodes().isNotEmpty()
    }
    private companion object { const val TIMEOUT = 10_000L }
}
