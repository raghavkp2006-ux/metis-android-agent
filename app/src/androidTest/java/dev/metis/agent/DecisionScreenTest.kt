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
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend() }

    @Before fun reset() = runBlocking { PersonalStorage.repository(composeRule.activity).database.clearAllTables() }
    @After fun cleanup() {
        val repository = PersonalStorage.repository(composeRule.activity)
        composeRule.activityRule.scenario.close()
        runBlocking { repository.database.clearAllTables() }
    }

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
        composeRule.enterFrontend()
        composeRule.onNodeWithTag("composer_input").performTextInput("What should I do with 30 minutes?")
        composeRule.onNodeWithTag("send_request").performClick()
        composeRule.awaitRequestText("Suggested task: $title", substring = true)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_accept)).assertDoesNotExist()
        val recommendation = runBlocking { repository.foundation.recommendation.observe().first().single() }
        assertEquals(before, runBlocking { repository.observeTasks().first() })
        assertTrue(runBlocking { repository.foundation.actionRun.observe().first().isEmpty() })
        composeRule.enterFrontend()
        composeRule.navigateWorkspace("ACTIVITY", "Decisions")
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("decision_history_${recommendation.metadata.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("decision_explanation_${recommendation.metadata.id}")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Suggested task: $title", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.navigateWorkspace("ACTIVITY", "Timeline")
        composeRule.scrollToDisplayed("RECOMMENDATION SHOWN")
        composeRule.onNodeWithText("RECOMMENDATION SHOWN").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyDecisionRequestReportsNoEligibleTaskWithoutSavingAFabricatedSuggestion() {
        composeRule.enterFrontend()
        composeRule.onNodeWithTag("composer_input").performTextInput("What should I do?")
        composeRule.onNodeWithTag("send_request").performClick()
        composeRule.awaitRequestText("No eligible saved task was found.", substring = true)
        assertTrue(runBlocking { PersonalStorage.repository(composeRule.activity)
            .foundation.recommendation.observe().first().isEmpty() })
    }

    private companion object { const val TIMEOUT = 10_000L }
}
