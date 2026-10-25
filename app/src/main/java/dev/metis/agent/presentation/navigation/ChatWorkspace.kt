@file:Suppress("MagicNumber") // Chat layout dimensions.

package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.presentation.designsystem.MetisCard
import dev.metis.agent.presentation.designsystem.MetisOrb
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.launch

@Suppress("CyclomaticComplexMethod") // Rendering supported input/result states.
@Composable
internal fun ChatWorkspace(
    request: RequestUiState, onDraft: (String) -> Unit,
    onAccept: (ActionProposal) -> Unit, onUndo: (ActionReceipt) -> Unit, onReject: () -> Unit,
    modifier: Modifier = Modifier, listState: LazyListState = rememberLazyListState(),
    onActionPersonChoice: (Int) -> Unit = {}, onActionContactPermission: (UUID) -> Unit = {},
    onActionContactSkip: (UUID) -> Unit = {},
    onRejectProposal: ((ActionProposal) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var newMessage by remember { mutableStateOf(false) }
    val atBottom by remember { derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index.let {
            it == null || it >= listState.layoutInfo.totalItemsCount - 2
        }
    } }
    LaunchedEffect(atBottom) { if (atBottom) newMessage = false }
    LaunchedEffect(request.turns.size, request.result, request.busy, request.sessionId) {
        if (request.turns.isEmpty()) {
            newMessage = false
        } else if (atBottom) {
            listState.scrollToItem(request.turns.size)
            newMessage = false
        } else newMessage = true
    }
    Box(modifier) {
        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize().testTag("chat_messages")) {
            if (request.turns.isEmpty()) item(key = "greeting") { ChatGreeting(onDraft) }
            items(request.turns, key = { it.id }) { turn ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.widthIn(max = 600.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                if (turn.origin.workspace != Workspace.CHAT) Text("From ${turn.origin.workspace.title}",
                                    style = MaterialTheme.typography.bodySmall)
                                SelectionContainer { Text(turn.text,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer) }
                            }
                        }
                    }
                    val results = listOfNotNull(turn.result) + turn.parts + turn.outcomes
                    val firstQuestion = results.firstOrNull { it.followUp != null }?.followUp?.id
                    results.forEach { result ->
                        val eligible = request.activeRequestId == turn.id &&
                            (request.result === result || request.parts.any { it === result }) && !turn.dismissed
                        val cancelCard = {
                            val proposal = result.proposals.singleOrNull()
                            if (proposal != null && onRejectProposal != null) onRejectProposal(proposal) else onReject()
                        }
                        ChatAgentMessage(result, eligible, request.busy, onAccept, onUndo, cancelCard,
                            onActionPersonChoice, onActionContactPermission, onActionContactSkip,
                            choiceEligible = result.followUp?.id == firstQuestion)
                    }
                    if (turn.dismissed) Text("Request dismissed · nothing was executed.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (request.busy && request.activeRequestId == turn.id) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        MetisOrb(diameter = 40.dp, active = true)
                        Text(if (request.accepting) "Verifying your action…" else "METIS is working…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "conversation_bottom") { Spacer(Modifier.height(1.dp)) }
        }
        if (newMessage && !atBottom) FilledTonalButton(onClick = {
            scope.launch { listState.scrollToItem(request.turns.size) }
            newMessage = false
        }, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)) { Text("New message ↓") }
    }
}

@Composable
private fun ChatGreeting(onDraft: (String) -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning."
        hour < 17 -> "Good afternoon."
        else -> "Good evening."
    }
    Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MetisOrb(diameter = 96.dp)
        Text("M E T I S", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(greeting, style = MaterialTheme.typography.headlineMedium)
        Text("What can I help with?", style = MaterialTheme.typography.titleLarge)
        Text("A little clarity. A more intentional day.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        listOf("Plan my day", "What should I do?").forEach { prompt ->
            OutlinedButton(onClick = { onDraft(prompt) }, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(min = 56.dp)) {
                Text(prompt, Modifier.weight(1f))
                Text("↗")
            }
        }
        Text("This conversation is temporary. Your saved data stays on your device.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@Suppress("CyclomaticComplexMethod") // Each result type has its own explicit controls.
private fun ChatAgentMessage(
    result: AgentResult, eligible: Boolean, busy: Boolean,
    onAccept: (ActionProposal) -> Unit, onUndo: (ActionReceipt) -> Unit, onReject: () -> Unit,
    onActionPersonChoice: (Int) -> Unit, onActionContactPermission: (UUID) -> Unit,
    onActionContactSkip: (UUID) -> Unit,
    choiceEligible: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("METIS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SelectionContainer(Modifier.testTag("request_result")) {
            Text(result.message,
                color = if (result.status == AgentResultStatus.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface)
        }
        if (!eligible && (result.proposals.isNotEmpty() || result.completedActions.isNotEmpty())) {
            Text("Inactive · only the current result can be acted on.", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        TaskRequestActions(result, busy, onAccept, onUndo, onReject, eligible)
        val followUp = result.followUp
        val canChoose = eligible && choiceEligible
        if (canChoose && followUp != null && followUp.missingFields.singleOrNull() in
            setOf("selection", "reminder_contact_choice", "reference_person", "reference_item")) {
            followUp.suggestedChoices.forEachIndexed { index, name ->
                val optionKind = if (followUp.missingFields == setOf("reference_item")) "Item" else "Person"
                OutlinedButton(onClick = { onActionPersonChoice(index) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                        contentDescription = "$optionKind option ${index + 1}"
                    }) { Text(name, Modifier.clearAndSetSemantics {}) }
            }
        }
        if (eligible && !choiceEligible && followUp?.suggestedChoices?.isNotEmpty() == true)
            Text("Answer the previous question first.", style = MaterialTheme.typography.bodySmall)
        if (eligible && followUp != null && followUp.missingFields.singleOrNull() in
            setOf("contact_permission", "reminder_contact_permission")) {
            OutlinedButton(onClick = { onActionContactPermission(followUp.id) }, enabled = !busy) {
                Text("Allow contacts")
            }
            OutlinedButton(onClick = { onActionContactSkip(followUp.id) }, enabled = !busy) {
                Text("Continue without contacts")
            }
        }
        if (eligible && result.status == AgentResultStatus.FOLLOW_UP &&
            result.followUp?.missingFields == setOf("time")) {
            OutlinedButton(onClick = onReject, enabled = !busy) { Text("Cancel reminder") }
        }
        result.completedActions.forEach { receipt ->
            MetisCard {
                Text("Action receipt", style = MaterialTheme.typography.labelLarge)
                Text(receipt.outcome.status.name.lowercase().replace('_', ' '))
                Text("Verification: ${receipt.outcome.verification.name.lowercase().replace('_', ' ')}",
                    style = MaterialTheme.typography.bodyMedium)
                receipt.outcome.finishedAt?.let { finished ->
                    Text(java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                        java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
                        .withZone(java.time.ZoneId.systemDefault()).format(finished),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
