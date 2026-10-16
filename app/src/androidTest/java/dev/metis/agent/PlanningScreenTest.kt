package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedTask
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend(); composeRule.navigateWorkspace("TODAY"); composeRule.showAllTasks() }


    @Test
    fun planAndTaskLinksReflectStoredPlanningRecordsAndDeletion(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val project = SavedProject("Synthetic screen project")
        val goal = SavedGoal("Synthetic screen goal", projectId = project.metadata.id)
        val task = SavedTask("Synthetic linked screen task", projectId = project.metadata.id, goalId = goal.metadata.id)
        var stage = "before seed"
        try {
            repository.planning.saveProject(project)
            repository.planning.saveGoal(goal)
            repository.saveTask(task)
            composeRule.reloadWorkspaceRecords()
            composeRule.showAllTasks()
            stage = "seeded before Today assertion"
            waitFor("Project: ${project.title}")
            composeRule.onNodeWithText("Goal: ${goal.title}").performScrollTo().assertIsDisplayed()
            composeRule.navigateWorkspace("PROJECTS_GOALS")
            waitFor("${project.title} · ACTIVE")
            composeRule.activityRule.scenario.recreate()
            stage = "after recreation on Plan"
            waitFor("${goal.title} · ACTIVE")
            repository.planning.deleteProject(project.metadata.id, 0)
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("${project.title} · ACTIVE").fetchSemanticsNodes().isEmpty()
            }
            composeRule.navigateWorkspace("TODAY"); composeRule.showAllTasks()
            stage = "Today after project deletion"
            waitFor("Goal: ${goal.title}")
        } catch (error: Throwable) {
            android.util.Log.e("NAV_DIAGNOSTIC", "stage=$stage project=" +
                (repository.database.planning().project(project.metadata.id) != null) +
                " task=" + (repository.database.records().task(task.metadata.id) != null))
            android.util.Log.e("NAV_DIAGNOSTIC", "ui=" + composeRule.onRoot().printToString())
            throw error
        } finally {
            repository.deleteTask(task.metadata.id, 0)
            repository.planning.deleteGoal(goal.metadata.id, 0)
            if (repository.database.planning().project(project.metadata.id) != null) {
                repository.planning.deleteProject(project.metadata.id, 0)
            }
        }
    }

    private fun waitFor(text: String) {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private companion object { const val TIMEOUT = 30_000L }
}
