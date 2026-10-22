package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.metis.agent.presentation.designsystem.MetisCard
import dev.metis.agent.domain.contacts.MAX_CONTACT_QUERY_LENGTH
import java.util.UUID

/** Contact data is displayed only in this transient view, never in the chat result or saved state. */
@Composable
@Suppress("CyclomaticComplexMethod") // Each sealed lookup state has its own explicit UI outcome.
internal fun ContactLookupPanel(state: ContactLookupUiState, onFind: (String) -> Unit,
    onChoice: (UUID, Int) -> Unit, onPermission: (UUID) -> Unit,
    onCancel: () -> Unit, onSettings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    MetisCard {
        Text("Find contact", style = MaterialTheme.typography.titleLarge)
        Text("Search your device contacts only when you choose. Nothing is called, messaged or saved.")
        OutlinedTextField(value = query, onValueChange = { query = it.take(MAX_CONTACT_QUERY_LENGTH) },
            label = { Text("Name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                .testTag("contact_query").clearAndSetSemantics {
                contentDescription = "Contact name"
                editableText = AnnotatedString(if (query.isEmpty()) "" else "•••")
                requestFocus { focusRequester.requestFocus(); true }
                setText { value -> query = value.text.take(MAX_CONTACT_QUERY_LENGTH); true }
            })
        Row {
            TextButton(onClick = { onFind(query) }, enabled = query.isNotBlank(),
                modifier = Modifier.testTag("find_contact")) { Text("Find contact") }
            if (state != ContactLookupUiState.Idle) TextButton(onClick = onCancel) { Text("Clear") }
        }
        when (state) {
            ContactLookupUiState.Idle -> Unit
            ContactLookupUiState.Searching -> Text("Searching contacts…")
            ContactLookupUiState.NotFound -> Text("No matching contact was available. Refine the name and try again.")
            ContactLookupUiState.Refine -> Text("More than five contacts match. Add more of the name and search again.")
            is ContactLookupUiState.PermissionNeeded -> {
                if (state.permanentlyDenied) PermanentPermissionMessage(state.explainPermanentDenial,
                    onSettings)
                else {
                    Text("Allow Contacts access to search names on this device. The lookup is read only.")
                    TextButton(onClick = { onPermission(state.questionId) }) { Text("Allow contacts") }
                }
            }
            is ContactLookupUiState.Denied -> {
                if (state.permanentlyDenied) PermanentPermissionMessage(state.explainPermanentDenial,
                    onSettings)
                else {
                    Text("Contacts access was denied. You can search again when you are ready.")
                    TextButton(onClick = { onFind(query) }, enabled = query.isNotBlank()) {
                        Text("Try again")
                    }
                }
            }
            is ContactLookupUiState.Ambiguous -> {
                Text(state.question, modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "Choose a contact"
                })
                Column {
                    state.candidates.forEachIndexed { index, candidate ->
                        val label = "Contact option ${index + 1}"
                        TextButton(onClick = { onChoice(state.questionId, index) },
                            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                                contentDescription = label
                            }) {
                            Text(candidate.displayName, modifier = Modifier.clearAndSetSemantics {})
                            candidate.maskedIdentifier?.let {
                                Text(" · $it", Modifier.padding(start = 8.dp).clearAndSetSemantics {})
                            }
                        }
                    }
                }
            }
            is ContactLookupUiState.Resolved -> {
                Text("Contact found")
                Text(state.contact.displayName +
                    (state.contact.maskedIdentifier?.let { " · $it" } ?: ""),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Contact found" })
            }
        }
    }
}

@Composable
private fun PermanentPermissionMessage(explain: Boolean, onSettings: () -> Unit) {
    Text(if (explain) "Contacts access was denied permanently. Android will not show the permission " +
        "dialog again. You can enable access in app settings." else
        "Contacts access is off. Enable it in app settings to search again.")
    TextButton(onClick = onSettings) { Text("Open app settings") }
}
