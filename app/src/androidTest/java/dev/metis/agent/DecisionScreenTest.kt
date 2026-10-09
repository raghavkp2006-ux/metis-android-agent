package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.SavedTask
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecisionScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    @Before fun reset() = runBlocking { PersonalStorage.repository(composeRule.activity).database.clearAllTables() }
    @After fun cleanup() = runBlocking { PersonalStorage.repository(composeRule.activity).database.clearAllTables() }

    @Test fun composerRecommendsFittingTaskAndPersistsInspectableExplanationWithoutAcceptanceOrMutation() {
        val repository = PersonalStorage.repository(composeRule.activity)
        val due = Instant.now().minusSeconds(60).toEpochMilli()
        val title = "Synthetic fitting decision task"
        val before = runBlocking {
            repository.saveTask(SavedTask(title, dueAt = due, dueZoneId = "UTC", priority = 2, estimatedSeconds = 600))
            repository.saveTask(SavedTask("Synthetic oversized task", dueAt = due, dueZoneId = "UTC", priority = 3,
                estimatedSeconds = 3_600))
            repository.saveTask(SavedTask("Synthetic unknown duration task", priority = 3))
            repository.observeTasks().first()
        }
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithText("What do you want to do?").performTextInput("What should I do with 30 minutes?")
        composeRule.onNodeWithText("Send request").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText("Suggested task: $title", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Suggested task: $title", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_accept)).assertDoesNotExist()
        val recommendation = runBlocking { repository.foundation.recommendation.observe().first().single() }
        assertEquals(before, runBlocking { repository.observeTasks().first() })
        assertTrue(runBlocking { repository.foundation.actionRun.observe().first().isEmpty() })
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_close_draft))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("nav_YOU").performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("decision_history_${recommendation.metadata.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("decision_explanation_${recommendation.metadata.id}")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Suggested task: $title", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("nav_TIMELINE").performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText("RECOMMENDATION SHOWN").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("RECOMMENDATION SHOWN").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyDecisionRequestReportsNoEligibleTaskWithoutSavingAFabricatedSuggestion() {
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithText("What do you want to do?").performTextInput("What should I do?")
        composeRule.onNodeWithText("Send request").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText("No eligible saved task was found.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(runBlocking { PersonalStorage.repository(composeRule.activity)
            .foundation.recommendation.observe().first().isEmpty() })
    }

    private companion object { const val TIMEOUT = 10_000L }
}
