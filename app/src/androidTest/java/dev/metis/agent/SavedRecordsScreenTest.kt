package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedRecordsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun debugInspectionRefreshesStoredCountsAcrossRecreation(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        assertEquals(0, repository.database.records().recordCount())
        val task = SavedTask("Synthetic inspection screen task")
        try {
            composeRule.onNodeWithTag("nav_YOU").performClick()
            composeRule.onNodeWithText("Refresh database inspection (debug)").performScrollTo().assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodesWithText("Stored schema version: 4").fetchSemanticsNodes().size)
            repository.saveTask(task)
            refreshInspection("tasks: 1")
            composeRule.onNodeWithText("Foreign-key check: passed").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("SQLite quick check: passed").performScrollTo().assertIsDisplayed()
            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("tasks: 1").fetchSemanticsNodes().isNotEmpty()
            }
            repository.deleteTask(task.metadata.id, 0)
            refreshInspection("tasks: 0")
        } finally {
            repository.observeTasks().first().firstOrNull { it.metadata.id == task.metadata.id }?.let {
                repository.deleteTask(it.metadata.id, it.metadata.revision)
            }
        }
    }

    private fun refreshInspection(expected: String) {
        composeRule.onNodeWithText("Refresh database inspection (debug)").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun debugSeedIsExplicitAndRefusesToOverwriteExistingData(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        assertEquals(0, repository.database.records().recordCount())
        try {
            composeRule.onNodeWithTag("nav_YOU").performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("No saved memories").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Load synthetic records (debug)").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Synthetic explicit fact: this is a development fixture.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(3, repository.database.records().recordCount())
            composeRule.onNodeWithText("Load synthetic records (debug)").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText(
                    "Seed unavailable. The database must be empty and its key accessible. No data was reset.",
                ).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(3, repository.database.records().recordCount())
        } finally {
            repository.observeMemories().first().forEach { repository.deleteMemory(it.metadata.id, it.metadata.revision) }
            repository.observeSchedules().first().forEach {
                repository.deleteSchedule(it.metadata.id, it.metadata.revision)
            }
            repository.observeTasks().first().forEach { repository.deleteTask(it.metadata.id, it.metadata.revision) }
        }
    }

    @Test
    fun screensObserveSavedRowsAcrossActivityRecreationWithoutEnablingRequests(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val task = SavedTask("Synthetic screen task")
        val schedule = SavedSchedule("Synthetic screen schedule", 10, 20, "UTC", "Synthetic reason", taskId = task.metadata.id)
        val memory = SavedMemory("Synthetic screen memory")
        try {
            repository.saveTask(task)
            repository.saveSchedule(schedule)
            repository.saveMemory(memory)
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText(task.title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(task.title).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("nav_PLAN").performClick()
            composeRule.onNodeWithText(schedule.title).performScrollTo().assertIsDisplayed()
            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText(schedule.title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(schedule.title).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("nav_YOU").performClick()
            composeRule.onNodeWithText(memory.content).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Explicit fact · SEMANTIC").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Write a request").performClick()
            composeRule.onNodeWithText("Send request").performScrollTo().assertIsNotEnabled()
        } finally {
            repository.deleteMemory(memory.metadata.id, 0)
            repository.deleteSchedule(schedule.metadata.id, 0)
            repository.deleteTask(task.metadata.id, 0)
        }
    }

    private companion object { const val TIMEOUT = 10_000L }
}
