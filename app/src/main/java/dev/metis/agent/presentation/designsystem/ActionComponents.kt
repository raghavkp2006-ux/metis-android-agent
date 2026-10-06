package dev.metis.agent.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.metis.agent.R

/** Display data only. Acceptance authority belongs to the future domain/policy layer. */
data class ProposalDisplay(
    val title: String,
    val details: List<String>,
    val risk: String,
    val reason: String,
    val evidence: List<String> = emptyList(),
    val canAccept: Boolean = true,
    val busy: Boolean = false,
)

enum class ReceiptOutcome { COMPLETED, HANDOFF, PENDING, FAILED, CANCELLED }

data class ReceiptDisplay(
    val title: String,
    val outcome: ReceiptOutcome,
    val details: String,
    val timestamp: String,
)

@Composable
fun AgentMessage(message: String, modifier: Modifier = Modifier, timestamp: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AgentSpacing.tiny)) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        timestamp?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun ActionProposal(
    proposal: ProposalDisplay, onAccept: () -> Unit, onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AgentSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AgentSpacing.medium),
        ) {
            ProposalDetails(proposal)
            ProposalButtons(proposal, onAccept, onCancel)
        }
    }
}

@Composable
private fun ProposalDetails(proposal: ProposalDisplay) {
    Text(proposal.title, style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.proposal_status), color = MaterialTheme.colorScheme.onSurfaceVariant)
    proposal.details.forEach { Text(it) }
    Text(stringResource(R.string.proposal_risk, proposal.risk))
    Text(stringResource(R.string.proposal_reason, proposal.reason))
    if (proposal.evidence.isNotEmpty()) {
        Text(stringResource(R.string.proposal_evidence), style = MaterialTheme.typography.labelLarge)
        proposal.evidence.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    if (!proposal.canAccept) Text(stringResource(R.string.proposal_unavailable), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ProposalButtons(proposal: ProposalDisplay, onAccept: () -> Unit, onCancel: () -> Unit) {
    PrimaryButton(
        stringResource(R.string.action_accept), onAccept, Modifier.fillMaxWidth(),
        enabled = proposal.canAccept, loading = proposal.busy,
    )
    SecondaryButton(
        stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth(), enabled = !proposal.busy,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(
    proposal: ProposalDisplay, onAccept: () -> Unit, onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = { if (!proposal.busy) onCancel() }, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AgentSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AgentSpacing.medium),
        ) {
            ProposalDetails(proposal)
            ProposalButtons(proposal, onAccept, onCancel)
        }
    }
}

@Composable
fun ActionReceipt(
    receipt: ReceiptDisplay, modifier: Modifier = Modifier, onUndo: (() -> Unit)? = null,
) {
    val outcomeLabel = when (receipt.outcome) {
        ReceiptOutcome.COMPLETED -> R.string.receipt_completed
        ReceiptOutcome.HANDOFF -> R.string.receipt_handoff
        ReceiptOutcome.PENDING -> R.string.receipt_pending
        ReceiptOutcome.FAILED -> R.string.receipt_failed
        ReceiptOutcome.CANCELLED -> R.string.receipt_cancelled
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AgentSpacing.small)) {
        Text(receipt.title, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(outcomeLabel),
            color = if (receipt.outcome == ReceiptOutcome.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(receipt.details)
        Text(receipt.timestamp, style = MaterialTheme.typography.bodySmall)
        onUndo?.let { SecondaryButton(stringResource(R.string.action_undo), it) }
    }
}
