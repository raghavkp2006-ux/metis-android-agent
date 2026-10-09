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
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.presentation.designsystem.ProposalDisplay
import dev.metis.agent.presentation.designsystem.SecondaryButton

@Composable
internal fun TaskRequestActions(
    result: AgentResult, busy: Boolean, onAccept: (ActionProposal) -> Unit, onUndo: (ActionReceipt) -> Unit,
    onCancel: () -> Unit,
) {
    result.proposals.singleOrNull()?.let { proposal ->
        (proposal.action as? CreateReminderAction)?.let { reminder ->
            dev.metis.agent.presentation.designsystem.ActionProposal(
                ProposalDisplay("Schedule reminder", listOf(reminder.title,
                    "${reminder.trigger.local} (${reminder.trigger.zone})", "Approximate · may be late.",
                    "Expires: ${proposal.expiresAt}"), "R1 · local notification", proposal.reason,
                    canAccept = true, busy = busy),
                { onAccept(proposal) }, onCancel, Modifier.testTag("reminder_proposal"))
        }
        val create = (proposal.action as? TaskAction)?.mutation as? TaskMutation.Create
        if (create != null) dev.metis.agent.presentation.designsystem.ActionProposal(
            ProposalDisplay("Create task", listOf(create.title, "Saved locally. No deadline or reminder.",
                "Expires: ${proposal.expiresAt}"), "R1 · local write", proposal.reason, canAccept = true, busy = busy),
            { onAccept(proposal) }, onCancel, Modifier.testTag("task_proposal"),
        )
        val complete = (proposal.action as? TaskAction)?.mutation as? TaskMutation.Complete
        if (complete != null) dev.metis.agent.presentation.designsystem.ActionProposal(
            ProposalDisplay("Complete task", listOf("Open → Completed", "Reminders and schedules stay as they are.",
                "Expires: ${proposal.expiresAt}"),
                "R1 · local write", proposal.reason, canAccept = true, busy = busy),
            { onAccept(proposal) }, onCancel, Modifier.testTag("task_completion_proposal"),
        )
        val mutation = (proposal.action as? TaskAction)?.mutation
        if (mutation is TaskMutation.Update || mutation is TaskMutation.Postpone || mutation is TaskMutation.Delete) {
            TaskEditProposal(proposal, busy, onAccept, onCancel)
        }
    }
    result.completedActions.singleOrNull()?.let { receipt ->
        if (receipt.outcome.undo == UndoCapability.LOCAL_REVISION_CHECKED) SecondaryButton(
            "Undo accepted action", { onUndo(receipt) }, Modifier.testTag("undo_created_task"), enabled = !busy)
    }
}

@Composable
private fun TaskEditProposal(
    proposal: ActionProposal, busy: Boolean, onAccept: (ActionProposal) -> Unit, onCancel: () -> Unit,
) {
    val mutation = (proposal.action as TaskAction).mutation
    val fields = when (mutation) {
        is TaskMutation.Update -> listOfNotNull(
            (mutation.patch.title as? dev.metis.agent.domain.agent.FieldChange.Set)?.value?.let { "New title: $it" },
            (mutation.patch.priority as? dev.metis.agent.domain.agent.FieldChange.Set)?.value?.let { "New priority: " +
                "$it" })
        is TaskMutation.Postpone -> listOf("New deadline: ${mutation.due.local} (${mutation.due.zone})",
            "Reminders and schedule blocks stay as they are.")
        else -> listOf("Permanent deletion. No undo.",
            "Only unlinked tasks can be deleted. Personal action history will be removed.")
    }
    dev.metis.agent.presentation.designsystem.ActionProposal(
        ProposalDisplay(if (mutation is TaskMutation.Delete) "Delete task permanently" else "Change task",
            fields + "Expires: ${proposal.expiresAt}", "${proposal.risk} · local write", proposal.reason,
            canAccept = true, busy = busy), { onAccept(proposal) }, onCancel, Modifier.testTag("task_edit_proposal"))
}
