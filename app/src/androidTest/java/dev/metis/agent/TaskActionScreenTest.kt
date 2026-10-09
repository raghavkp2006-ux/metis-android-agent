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
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.TaskStatus
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
    fun taskCompletionShowsExactTitleAndChangesStatusOnlyAfterAcceptance() {
        val repository = PersonalStorage.repository(composeRule.activity)
        val title = "Synthetic completion ${UUID.randomUUID()}!"
        val task = SavedTask(title)
        val before = runBlocking { repository.foundation.actionRun.observe().first().map { it.metadata.id }.toSet() }
        runBlocking { repository.saveTask(task) }
        try {
            composeRule.onNodeWithText("Write a request").performClick()
            composeRule.onNodeWithText("What do you want to do?").performTextInput("complete task: $title")
            composeRule.onNodeWithText("Send request").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithTag("task_completion_proposal").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Open → Completed").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Mark this task completed: $title", substring = true)
                .performScrollTo().assertIsDisplayed()
            assertTrue(runBlocking { repository.observeTasks().first()
                .single { it.metadata.id == task.metadata.id }.status == TaskStatus.OPEN })
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_accept))
                .performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Task completion verified on this device.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(runBlocking { repository.observeTasks().first()
                .single { it.metadata.id == task.metadata.id }.status == TaskStatus.COMPLETED })
            composeRule.onNodeWithTag("undo_created_task").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Task action undone and verified locally.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(runBlocking { repository.observeTasks().first()
                .single { it.metadata.id == task.metadata.id }.status == TaskStatus.OPEN })
        } finally {
            runBlocking {
                repository.observeTasks().first().singleOrNull { it.metadata.id == task.metadata.id }?.let {
                    repository.deleteTask(it.metadata.id, it.metadata.revision)
                }
                repository.foundation.actionRun.observe().first().filter { it.metadata.id !in before }.forEach {
                    repository.foundation.actionRun.delete(it.metadata.id, it.metadata.revision)
                }
            }
        }
    }

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
                composeRule.onAllNodesWithText("Task action undone and verified locally.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Task action undone and verified locally.")
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
