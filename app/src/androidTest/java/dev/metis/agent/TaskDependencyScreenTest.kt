package dev.metis.agent

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDependencyScreenTest {
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend(); composeRule.navigateWorkspace("TODAY");
        composeRule.showAllTasks() }


    @Test
    fun todayReflectsSavedPrerequisitesAcrossRecreationAndDeletion(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val task = SavedTask("Synthetic dependent task")
        val prerequisite = SavedTask("Synthetic prerequisite task")
        var stage = "before seed"
        try {
            repository.saveTask(task)
            repository.saveTask(prerequisite)
            repository.dependencies.saveDependency(SavedTaskDependency(task.metadata.id, prerequisite.metadata.id))
            stage = "seeded before Today assertion"
            waitForCount(true)
            composeRule.onNodeWithText("1 saved prerequisite task").performScrollTo().assertIsDisplayed()
            composeRule.activityRule.scenario.recreate()
            stage = "after recreation on Today"
            waitForCount(true)
            composeRule.onNodeWithText("1 saved prerequisite task").performScrollTo().assertIsDisplayed()
            repository.deleteTask(prerequisite.metadata.id, 0)
            waitForCount(false)
            composeRule.onNodeWithText(task.title).performScrollTo().assertIsDisplayed()
        } catch (error: Throwable) {
            android.util.Log.e("NAV_DIAGNOSTIC", "stage=$stage dependent=" +
                (repository.database.records().task(task.metadata.id) != null) +
                " prerequisite=" + (repository.database.records().task(prerequisite.metadata.id) != null))
            android.util.Log.e("NAV_DIAGNOSTIC", "ui=" + composeRule.onRoot().printToString())
            throw error
        } finally {
            repository.deleteTask(task.metadata.id, 0)
            // The prerequisite may already have been deleted by the test.
            if (repository.database.records().task(prerequisite.metadata.id) != null) {
                repository.deleteTask(prerequisite.metadata.id, 0)
            }
        }
    }

    private fun waitForCount(present: Boolean) = composeRule.waitUntil(10_000L) {
        composeRule.onAllNodesWithText("1 saved prerequisite task").fetchSemanticsNodes().isNotEmpty() == present
    }
}
