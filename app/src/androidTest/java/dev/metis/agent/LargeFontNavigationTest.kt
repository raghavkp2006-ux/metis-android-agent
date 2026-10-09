package dev.metis.agent

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.NavigationShell
import dev.metis.agent.presentation.navigation.ShellDestination
import dev.metis.agent.presentation.navigation.ShellUiState
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFontNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DesignSystemTestActivity>()

    @After fun cleanup() = runBlocking { PersonalStorage.repository(composeRule.activity).database.clearAllTables() }

    @Test fun darkDoubleSizeSuggestionHistoryKeepsExplanationReachable() {
        val repository = PersonalStorage.repository(composeRule.activity)
        val record = runBlocking {
            repository.saveTask(dev.metis.agent.domain.storage.SavedTask("Synthetic accessible suggestion",
                estimatedSeconds = 600))
            repository.decisions.recommend(UUID.randomUUID(), 1_800)
            repository.foundation.recommendation.observe().first().first()
        }
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AgentTheme(darkTheme = true) {
                    NavigationShell(ShellUiState(destination = ShellDestination.YOU), {}, {}, {}, {})
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("decision_explanation_${record.metadata.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("decision_explanation_${record.metadata.id}").performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Suggested task:", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("decision_explanation_${record.metadata.id}").performScrollTo().performClick()
        composeRule.onNodeWithText("Suggested task:", substring = true).assertDoesNotExist()
    }

    @Test fun darkDoubleSizeTimelineKeepsOutcomeAndAuditControlsReachable() {
        val repository = PersonalStorage.repository(composeRule.activity)
        val event = runBlocking {
            val agent = ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions)
            val proposal = agent.process(AgentRequest(UUID.randomUUID(), "add a task to Synthetic large text history",
                InputSource.TEXT, Instant.now())).proposals.single()
            agent.accept(proposal)
            repository.foundation.event.observe().first().single { it.actionId == proposal.action.identity.id.toString() }
        }
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AgentTheme(darkTheme = true) {
                    NavigationShell(ShellUiState(destination = ShellDestination.TIMELINE), {}, {}, {}, {})
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("timeline_event_${event.metadata.id}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Synthetic large text history").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Task creation verified on this device.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_details_${event.metadata.id}").performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Task mutation was verified and committed atomically with this receipt.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_details_${event.metadata.id}").performScrollTo().performClick()
        composeRule.onNodeWithText("Task mutation was verified and committed atomically with this receipt.")
            .assertDoesNotExist()
    }

    @Test
    fun darkLargeTextKeepsAllDestinationsAndComposerReachable() {
        val state = mutableStateOf(ShellUiState())
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AgentTheme(darkTheme = true) {
                    NavigationShell(state.value, { state.value = state.value.copy(destination = it) }, {}, {}, {})
                }
            }
        }
        ShellDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.name}").performScrollTo()
                .assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
            composeRule.onNodeWithTag("screen_title").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Write a request").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
    }
}
