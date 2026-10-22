package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.presentation.navigation.Workspace
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationShellTest {
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()
    @Before fun welcome() { composeRule.enterFrontend() }

    @Test fun chatIsDefaultAndEveryWorkspaceKeepsGlobalComposer() {
        composeRule.onNodeWithTag("screen_title").assertTextContains("METIS")
        composeRule.onNodeWithTag("send_request").assertIsNotEnabled()
        Workspace.entries.forEach { workspace ->
            composeRule.navigateWorkspace(workspace.name)
            composeRule.onNodeWithTag("screen_title").assertTextContains(workspace.title)
            composeRule.onNodeWithTag("composer_input").assertIsDisplayed()
        }
    }

    @Test fun draftSurvivesWorkspaceNavigationAndRotation() {
        composeRule.onNodeWithTag("composer_input").performTextInput("Synthetic unsent draft")
        composeRule.navigateWorkspace("MEMORY")
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("composer_input").assertTextContains("Synthetic unsent draft")
        composeRule.navigateWorkspace("CHAT")
        composeRule.onNodeWithTag("composer_input").assertTextContains("Synthetic unsent draft")
    }

    @Test fun shortcutPrefillsWithoutSubmittingAndNewChatClearsDraft() {
        composeRule.onNodeWithText("Plan my day").performScrollTo().performClick()
        composeRule.onNodeWithTag("composer_input").assertTextContains("Plan my day")
        composeRule.onNodeWithTag("request_result").assertDoesNotExist()
        composeRule.onNodeWithTag("open_navigation").performClick()
        composeRule.onNodeWithTag("new_chat").performClick()
        composeRule.onNodeWithText("New chat", useUnmergedTree = true)
        composeRule.onNodeWithText("Starts a fresh temporary conversation.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_new_chat").performClick()
        composeRule.onNodeWithTag("send_request").assertIsNotEnabled()
    }

    @Test fun submittedConversationSurvivesRotationAndWorkspaceNavigation() {
        composeRule.onNodeWithTag("composer_input").performTextInput("What should I do?")
        composeRule.onNodeWithTag("send_request").performClick()
        composeRule.awaitCondition("navigation submitted request result") {
            composeRule.onAllNodesWithTag("request_result").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.navigateWorkspace("TODAY")
        composeRule.activityRule.scenario.recreate()
        composeRule.navigateWorkspace("CHAT")
        composeRule.onNodeWithTag("request_result").performScrollTo().assertIsDisplayed()
    }
}
