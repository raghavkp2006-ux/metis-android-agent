package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.presentation.navigation.ShellDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyDestinationHasAComposerAndAnAccurateUnavailableState() {
        ShellDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.name}").performClick().assertIsSelected()
            composeRule.onNodeWithTag("screen_title").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(composeRule.activity.getString(destination.description))
                .performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Write a request").assertIsDisplayed().performClick()
            composeRule.onNodeWithText("Requests are not available yet. No request will be sent.").assertIsDisplayed()
            composeRule.onNodeWithText("Send request").performScrollTo().assertIsNotEnabled()
            closeDraft()
            composeRule.onNodeWithTag("nav_${destination.name}").assertIsSelected()
        }
    }

    @Test
    fun draftSurvivesDestinationSwitchAndRecreationWithoutSending() {
        composeRule.onNodeWithTag("nav_PLAN").performClick()
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithText("What do you want to do?").performTextInput("Example request")
        composeRule.onNodeWithText("What do you want to do?").performImeAction()
        composeRule.onNodeWithText("Send request").performScrollTo().assertIsNotEnabled()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Example request").assertIsDisplayed()
        closeDraft()
        composeRule.onNodeWithTag("nav_PLAN").assertIsSelected()
        composeRule.onNodeWithTag("nav_AGENT").performClick()
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithText("Example request").assertIsDisplayed()
        composeRule.onNodeWithText("Use voice").assertDoesNotExist()
    }

    @Test
    fun backDismissesDraftThenDetailThenReturnsToToday() {
        composeRule.onNodeWithTag("nav_YOU").performClick()
        composeRule.onNodeWithText("Privacy and access").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen_title").performScrollTo().assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Write a request").performClick()
        pressBack()
        composeRule.onNodeWithTag("draft_sheet").assertDoesNotExist()
        composeRule.onNodeWithText("Privacy and access").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_YOU").assertIsSelected()
        pressBack()
        composeRule.onNodeWithText("Privacy and access").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("nav_YOU").assertIsSelected()
        pressBack()
        composeRule.onNodeWithTag("nav_TODAY").assertIsSelected()
    }

    @Test
    fun keyboardBackDoesNotNavigateAwayFromDraftSource() {
        composeRule.onNodeWithTag("nav_TIMELINE").performClick()
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithText("What do you want to do?").performTextInput("Keep this draft")
        pressBack()
        // The system may consume the first Back to hide the IME. The next Back dismisses the sheet.
        if (composeRule.onAllNodesWithTag("draft_sheet").fetchSemanticsNodes().isNotEmpty()) pressBack()
        composeRule.onNodeWithTag("draft_sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_TIMELINE").assertIsSelected()
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithText("Keep this draft").assertIsDisplayed()
    }

    private fun closeDraft() {
        composeRule.onNodeWithText("Close draft").performScrollTo().performClick()
        composeRule.onNodeWithTag("draft_sheet").assertDoesNotExist()
    }
}
