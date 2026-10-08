package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun planAndTaskLinksReflectStoredPlanningRecordsAndDeletion(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val project = SavedProject("Synthetic screen project")
        val goal = SavedGoal("Synthetic screen goal", projectId = project.metadata.id)
        val task = SavedTask("Synthetic linked screen task", projectId = project.metadata.id, goalId = goal.metadata.id)
        try {
            repository.planning.saveProject(project)
            repository.planning.saveGoal(goal)
            repository.saveTask(task)
            waitFor("Project: ${project.title}")
            composeRule.onNodeWithText("Goal: ${goal.title}").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("nav_PLAN").performClick()
            waitFor("${project.title} · ACTIVE")
            composeRule.activityRule.scenario.recreate()
            waitFor("${goal.title} · ACTIVE")
            repository.planning.deleteProject(project.metadata.id, 0)
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("${project.title} · ACTIVE").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("nav_TODAY").performClick()
            waitFor("Goal: ${goal.title}")
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

    private companion object { const val TIMEOUT = 10_000L }
}
