package dev.metis.agent.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.UndoCapability
import dev.metis.agent.presentation.designsystem.ProposalDisplay
import dev.metis.agent.presentation.designsystem.SecondaryButton

@Composable
internal fun TaskRequestActions(
    result: AgentResult, busy: Boolean, onAccept: (ActionProposal) -> Unit, onUndo: (ActionReceipt) -> Unit,
    onCancel: () -> Unit,
) {
    result.proposals.singleOrNull()?.let { proposal ->
        val create = (proposal.action as? TaskAction)?.mutation as? TaskMutation.Create
        if (create != null) dev.metis.agent.presentation.designsystem.ActionProposal(
            ProposalDisplay("Create task", listOf(create.title, "Saved locally. No deadline or reminder.",
                "Expires: ${proposal.expiresAt}"), "R1 · local write", proposal.reason, canAccept = true, busy = busy),
            { onAccept(proposal) }, onCancel, Modifier.testTag("task_proposal"),
        )
    }
    result.completedActions.singleOrNull()?.let { receipt ->
        if (receipt.outcome.undo == UndoCapability.LOCAL_REVISION_CHECKED) SecondaryButton(
            "Undo task creation", { onUndo(receipt) }, Modifier.testTag("undo_created_task"), enabled = !busy)
    }
}
