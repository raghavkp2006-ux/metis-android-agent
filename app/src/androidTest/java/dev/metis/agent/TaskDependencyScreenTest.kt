package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDependencyScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun todayReflectsSavedPrerequisitesAcrossRecreationAndDeletion(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val task = SavedTask("Synthetic dependent task")
        val prerequisite = SavedTask("Synthetic prerequisite task")
        try {
            repository.saveTask(task)
            repository.saveTask(prerequisite)
            repository.dependencies.saveDependency(SavedTaskDependency(task.metadata.id, prerequisite.metadata.id))
            waitForCount(true)
            composeRule.onNodeWithText("1 saved prerequisite task").performScrollTo().assertIsDisplayed()
            composeRule.activityRule.scenario.recreate()
            waitForCount(true)
            composeRule.onNodeWithText("1 saved prerequisite task").performScrollTo().assertIsDisplayed()
            repository.deleteTask(prerequisite.metadata.id, 0)
            waitForCount(false)
            composeRule.onNodeWithText(task.title).performScrollTo().assertIsDisplayed()
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
