package dev.metis.agent

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.composer_unavailable)).assertIsDisplayed()
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
        composeRule.onNodeWithText("Send request").performScrollTo().assertIsEnabled()
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
    fun submittedRequestAsksForDetailsAndClearsSubmittedTextFromRestoredDraft() {
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithText("What do you want to do?").performTextInput("Remind me tomorrow at 8")
        composeRule.onNodeWithText("What do you want to do?").performImeAction()
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithTag("request_result").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("request_result").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Specify a future date, time with AM/PM or HH:mm, and what to remember. " +
            "Ambiguous or invalid local times need clarification.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Send request").performScrollTo().assertIsNotEnabled()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Remind me tomorrow at 8").assertDoesNotExist()
        closeDraft()
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithTag("request_result").assertDoesNotExist()
    }

    @Test
    fun backDismissesDraftThenDetailThenReturnsToToday() {
        composeRule.onNodeWithTag("nav_YOU").performClick()
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithText("Search saved memory").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Search saved memory").performScrollTo()
            .performTextReplacement("Synthetic navigation search")
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
        val unobscuredBottom = composeRule.viewportBottom()
        composeRule.onNodeWithText("What do you want to do?").performTextInput("Keep this draft")
        composeRule.waitUntil(10_000L) { composeRule.viewportBottom() < unobscuredBottom }
        sendBack()
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithTag("draft_sheet").fetchSemanticsNodes().isEmpty() ||
                composeRule.viewportBottom() == unobscuredBottom
        }
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

    private fun pressBack() {
        val hadSheet = composeRule.onAllNodesWithTag("draft_sheet").fetchSemanticsNodes().isNotEmpty()
        sendBack()
        if (hadSheet) {
            val dismissed = try {
                composeRule.waitUntil(1_500L) {
                    composeRule.onAllNodesWithTag("draft_sheet").fetchSemanticsNodes().isEmpty()
                }
                true
            } catch (_: ComposeTimeoutException) {
                false
            }
            if (!dismissed) {
                // API 26 can consume the first Back while releasing stale IME focus.
                sendBack()
                composeRule.waitUntil(10_000L) {
                    composeRule.onAllNodesWithTag("draft_sheet").fetchSemanticsNodes().isEmpty()
                }
            }
        }
    }

    private fun sendBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

}
