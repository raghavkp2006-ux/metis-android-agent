package dev.metis.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.presentation.designsystem.ActionProposal
import dev.metis.agent.presentation.designsystem.ActionReceipt
import dev.metis.agent.presentation.designsystem.AgentComposer
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.designsystem.ComposerState
import dev.metis.agent.presentation.designsystem.ConfirmationSheet
import dev.metis.agent.presentation.designsystem.PermissionExplainer
import dev.metis.agent.presentation.designsystem.ProposalDisplay
import dev.metis.agent.presentation.designsystem.ReceiptDisplay
import dev.metis.agent.presentation.designsystem.ReceiptOutcome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignSystemInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test
    fun composerRequiresTextAndDisablesRepeatedSubmissionWhileBusy() {
        val draft = mutableStateOf("")
        val busy = mutableStateOf(false)
        var submissions = 0
        composeRule.setContent {
            AgentTheme {
                Surface {
                    AgentComposer(
                        draft.value, { draft.value = it }, { submissions++; busy.value = true },
                        state = ComposerState(busy = busy.value),
                    )
                }
            }
        }
        composeRule.onNodeWithText("Send request").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("What do you want to do?").performTextInput("   ")
        composeRule.onNodeWithText("What do you want to do?").performImeAction()
        composeRule.runOnIdle { assertEquals(0, submissions) }
        composeRule.onNodeWithText("Send request").assertIsNotEnabled()
        composeRule.onNodeWithText("What do you want to do?").performTextInput("Test request")
        composeRule.onNodeWithText("Send request").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Send request").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, submissions) }
        composeRule.onNodeWithText("Use voice").assertDoesNotExist()
    }

    @Test
    fun expiredProposalCannotBeAcceptedButCanBeCancelled() {
        var cancellations = 0
        var acceptances = 0
        composeRule.setContent {
            AgentTheme {
                ActionProposal(
                    ProposalDisplay("Test reminder", listOf("Tomorrow at 08:00 AM"), "Local action", "Test", canAccept = false),
                    { acceptances++ }, { cancellations++ },
                )
            }
        }
        composeRule.onNodeWithText("Tomorrow at 08:00 AM").assertIsDisplayed()
        composeRule.onNodeWithText("Accept proposal").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(0, acceptances); assertEquals(1, cancellations) }
    }

    @Test
    fun handoffReceiptDoesNotClaimVerifiedCompletionOrOfferUnsupportedUndo() {
        composeRule.setContent {
            AgentTheme {
                ActionReceipt(ReceiptDisplay("Test handoff", ReceiptOutcome.HANDOFF, "External composer", "08:00"))
            }
        }
        composeRule.onNodeWithText("Opened another app — completion unverified").assertIsDisplayed()
        composeRule.onNodeWithText("Completed and verified").assertDoesNotExist()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun confirmationShowsFullDetailsAndRequiresExplicitAcceptance() {
        var acceptances = 0
        composeRule.setContent {
            AgentTheme {
                ConfirmationSheet(
                    ProposalDisplay(
                        "Test confirmation", listOf("Target: Example", "08:00 AM · Asia/Kolkata"),
                        "Local action", "Explicit test request", listOf("Synthetic evidence"), canAccept = true,
                    ), { acceptances++ }, {},
                )
            }
        }
        composeRule.onNodeWithText("Target: Example").assertIsDisplayed()
        composeRule.onNodeWithText("08:00 AM · Asia/Kolkata").assertIsDisplayed()
        composeRule.onNodeWithText("Synthetic evidence").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, acceptances) }
        composeRule.onNodeWithText("Accept proposal").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, acceptances) }
    }

    @Test
    fun permissionDenialStillOffersNotNowWithoutRequestingAccess() {
        var accessRequests = 0
        var skipped = 0
        composeRule.setContent {
            AgentTheme(darkTheme = true) {
                Surface {
                    Column {
                        PermissionExplainer("Test access", "Test reason", "Test scope", true, { accessRequests++ }, { skipped++ })
                    }
                }
            }
        }
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.runOnIdle { assertEquals(0, accessRequests); assertEquals(1, skipped) }
    }
}
