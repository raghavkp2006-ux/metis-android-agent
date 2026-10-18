package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
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

    @org.junit.Before fun enterRedesignedFrontend() {
        dev.metis.agent.testing.TestSafety.requireIsolated(composeRule.activity)
        runBlocking { PersonalStorage.repository(composeRule.activity).database.clearAllTables() }
        composeRule.enterFrontend(); composeRule.reloadWorkspaceRecords(); composeRule.navigateWorkspace("TODAY");
        composeRule.showAllTasks() }


    @Test
    fun debugInspectionRefreshesStoredCountsAcrossRecreation(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        assertEquals(0, repository.database.records().recordCount())
        val task = SavedTask("Synthetic inspection screen task")
        try {
            composeRule.navigateWorkspace("SETTINGS")
            composeRule.onNodeWithText("Refresh database inspection (debug)").performScrollTo().assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodesWithText("Stored schema version: 6").fetchSemanticsNodes().size)
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
            composeRule.navigateWorkspace("MEMORY")
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("No saved memories").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.navigateWorkspace("SETTINGS")
            composeRule.onNodeWithText("Load synthetic records (debug)")
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Synthetic records saved. Inspect Today, Plan, and You; " +
                    "relaunch to verify persistence.").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.reloadWorkspaceRecords()
            composeRule.navigateWorkspace("MEMORY")
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Synthetic explicit fact: this is a development fixture.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(3, repository.database.records().recordCount())
            composeRule.navigateWorkspace("SETTINGS")
            composeRule.onNodeWithText("Load synthetic records (debug)")
                .performSemanticsAction(SemanticsActions.OnClick)
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
        var stage = "before seed"
        try {
            repository.saveTask(task)
            repository.saveSchedule(schedule)
            repository.saveMemory(memory)
            composeRule.reloadWorkspaceRecords()
            composeRule.showAllTasks()
            stage = "seeded before Today assertion"
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText(task.title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(task.title).performScrollTo().assertIsDisplayed()
            composeRule.navigateWorkspace("PLAN")
            composeRule.onNodeWithText(schedule.title).performScrollTo().assertIsDisplayed()
            composeRule.activityRule.scenario.recreate()
            stage = "after recreation on Plan"
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText(schedule.title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(schedule.title).performScrollTo().assertIsDisplayed()
            composeRule.navigateWorkspace("MEMORY")
            composeRule.onNodeWithText(memory.content).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Explicit fact · SEMANTIC").performScrollTo().assertIsDisplayed()
            composeRule.enterFrontend()
            composeRule.onNodeWithTag("send_request").assertIsNotEnabled()
        } catch (error: Throwable) {
            android.util.Log.e("NAV_DIAGNOSTIC", "stage=$stage task=" +
                (repository.database.records().task(task.metadata.id) != null) +
                " records=" + repository.database.records().recordCount())
            android.util.Log.e("NAV_DIAGNOSTIC", "ui=" + composeRule.onRoot().printToString())
            throw error
        } finally {
            repository.deleteMemory(memory.metadata.id, 0)
            repository.deleteSchedule(schedule.metadata.id, 0)
            repository.deleteTask(task.metadata.id, 0)
        }
    }

    private companion object { const val TIMEOUT = 10_000L }
}
