package dev.metis.agent

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.compose.ui.text.AnnotatedString
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.ContactLookupPanel
import dev.metis.agent.presentation.navigation.ContactLookupUiState
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Uses synthetic fields only; no device Contacts Provider is read. */
class ContactSemanticsPrivacyTest {
    @get:Rule val compose = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test fun mergedAndUnmergedTreesHideContactFields() {
        val name = "Synthetic Raghav"
        val number = "+1 555 010 4242"
        val state = ContactLookupUiState.Ambiguous("Which $name?", UUID.randomUUID(),
            listOf(ContactCandidate("synthetic-id", name, number)))
        compose.setContent {
            AgentTheme {
                ContactLookupPanel(state, {}, { _, _ -> }, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Contact name")
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("Synthetic Query")) }
        listOf(false, true).forEach { unmerged ->
            val tree = compose.onRoot(useUnmergedTree = unmerged).printToString()
            assertFalse(tree.contains(name))
            assertFalse(tree.contains(number))
            assertFalse(tree.contains("••42"))
            assertFalse(tree.contains("Synthetic Query"))
        }
    }

    @Test fun resolvedContactAlsoStaysOutOfTreeDumps() {
        val name = "Synthetic Ada"
        val state = ContactLookupUiState.Resolved(
            ContactCandidate("synthetic-id", name, "+1 555 010 8383"))
        compose.setContent { AgentTheme { ContactLookupPanel(state, {}, { _, _ -> }, {}, {}, {}) } }
        listOf(false, true).forEach { unmerged ->
            val tree = compose.onRoot(useUnmergedTree = unmerged).printToString()
            assertFalse(tree.contains(name))
            assertFalse(tree.contains("••83"))
        }
    }
}
