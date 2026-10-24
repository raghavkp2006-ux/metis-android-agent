package dev.metis.agent

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.FollowUpQuestion
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.ChatWorkspace
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.ConversationTurn
import dev.metis.agent.presentation.navigation.RequestUiState
import dev.metis.agent.presentation.navigation.Workspace
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActionContactPermissionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test fun rationaleIsVisibleBeforePermissionCallbackAndSkipDoesNotPrompt() {
        val requestId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val rationale = "Allow Contacts access to check this name? You can continue without access."
        val result = AgentResult(requestId, AgentResultStatus.FOLLOW_UP, rationale,
            followUp = FollowUpQuestion(questionId, requestId, rationale, setOf("contact_permission")))
        val state = RequestUiState(result = result, activeRequestId = requestId,
            turns = listOf(ConversationTurn(requestId, "I owe Rahul the slides",
                ConversationOrigin(Workspace.CHAT), result)))
        var requested = 0
        var skipped = 0
        compose.setContent { AgentTheme {
            ChatWorkspace(state, {}, {}, {}, {}, onActionContactPermission = { requested++ },
                onActionContactSkip = { skipped++ })
        } }
        compose.onNodeWithText(rationale).assertExists()
        compose.onNodeWithText("Allow contacts").assertExists()
        compose.runOnIdle { assertEquals(0, requested) }
        compose.onNodeWithText("Continue without contacts").performClick()
        compose.runOnIdle { assertEquals(1, skipped); assertEquals(0, requested) }
    }
}
