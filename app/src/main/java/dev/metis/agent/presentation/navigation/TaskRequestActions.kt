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
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.MessageAction
import dev.metis.agent.presentation.designsystem.ProposalDisplay
import dev.metis.agent.presentation.designsystem.SecondaryButton
@Suppress("CyclomaticComplexMethod") // Rendering supported input/result states.
@Composable
internal fun TaskRequestActions(
    result: AgentResult, busy: Boolean, onAccept: (ActionProposal) -> Unit, onUndo: (ActionReceipt) -> Unit,
    onCancel: () -> Unit, eligible: Boolean = true,
) {
    result.proposals.singleOrNull()?.let { proposal ->
        if (proposal.action is dev.metis.agent.domain.agent.AcceptPlanAction) {
            dev.metis.agent.presentation.designsystem.ActionProposal(
                ProposalDisplay("Accept local plan", listOf("Expires: ${proposal.expiresAt}"),
                    "R1 · local schedule blocks", proposal.reason, canAccept = eligible, busy = busy,
                        inactive = !eligible),
                { onAccept(proposal) }, onCancel, Modifier.testTag(
                    if (eligible) "plan_proposal" else "inactive_plan_proposal"))
        }
        (proposal.action as? CreateReminderAction)?.let { reminder ->
            dev.metis.agent.presentation.designsystem.ActionProposal(
                ProposalDisplay("Schedule reminder", listOf(reminder.title,
                    "${reminder.trigger.local} (${reminder.trigger.zone})", "Approximate · may be late.",
                    "Expires: ${proposal.expiresAt}"), "R1 · local notification", proposal.reason,
                    canAccept = eligible, busy = busy, inactive = !eligible),
                { onAccept(proposal) }, onCancel, Modifier.testTag(
                    if (eligible) "reminder_proposal" else "inactive_reminder_proposal"))
        }
        (proposal.action as? CreatePromiseAction)?.let { promise ->
            dev.metis.agent.presentation.designsystem.ActionProposal(
                ProposalDisplay("Save promise", listOf("Person: ${promise.personName}",
                    "Promise: ${promise.description}",
                    "Due: ${promise.dueDate?.toString() ?: "no deadline"}",
                    "Expires: ${proposal.expiresAt}"), "R1 · local write", proposal.reason,
                    canAccept = eligible, busy = busy, inactive = !eligible),
                { onAccept(proposal) }, onCancel, Modifier.testTag(
                    if (eligible) "promise_proposal" else "inactive_promise_proposal"))
        }
        (proposal.action as? CreateTimerAction)?.let { timer ->
            dev.metis.agent.presentation.designsystem.ActionProposal(
                ProposalDisplay("Start timer", listOf("Duration: ${timer.durationSeconds}s", "Label: ${timer.label}",
                    "Expires: ${proposal.expiresAt}"), "R1 · timer", proposal.reason,
                    canAccept = eligible, busy = busy, inactive = !eligible),
                { onAccept(proposal) }, onCancel, Modifier.testTag(
                    if (eligible) "timer_proposal" else "inactive_timer_proposal"))
        }
        (proposal.action as? CreateAlarmAction)?.let { alarm ->
            dev.metis.agent.presentation.designsystem.ActionProposal(
                ProposalDisplay("Set alarm", listOf("Time: ${alarm.trigger.local.toLocalTime()}",
                    "Label: ${alarm.label}", "Expires: ${proposal.expiresAt}"), "R1 · alarm", proposal.reason,
                    canAccept = eligible, busy = busy, inactive = !eligible),
                { onAccept(proposal) }, onCancel, Modifier.testTag(
                    if (eligible) "alarm_proposal" else "inactive_alarm_proposal"))
        }
        (proposal.action as? CallAction)?.let { call ->
            dev.metis.agent.presentation.designsystem.ActionProposal(
                ProposalDisplay("Call contact", listOf("Recipient: ${call.targetLabel}",
                    "Number: ${call.phoneNumber}", "Expires: ${proposal.expiresAt}"), "R2 · dialer", proposal.reason,
                    canAccept = eligible, busy = busy, inactive = !eligible),
                { onAccept(proposal) }, onCancel, Modifier.testTag(
                    if (eligible) "call_proposal" else "inactive_call_proposal"))
        }
        (proposal.action as? MessageAction)?.let { message ->
            dev.metis.agent.presentation.designsystem.ActionProposal(
                ProposalDisplay("Send message", listOf("Recipient: ${message.recipient}",
                    "Message: ${message.body}", "Expires: ${proposal.expiresAt}"), "R2 · message composer",
                    proposal.reason, canAccept = eligible, busy = busy, inactive = !eligible),
                { onAccept(proposal) }, onCancel, Modifier.testTag(
                    if (eligible) "message_proposal" else "inactive_message_proposal"))
        }
        val create = (proposal.action as? TaskAction)?.mutation as? TaskMutation.Create
        if (create != null) dev.metis.agent.presentation.designsystem.ActionProposal(
            ProposalDisplay("Create task", listOf(create.title, "Saved locally. No deadline or reminder.",
                "Expires: ${proposal.expiresAt}"), "R1 · local write", proposal.reason, canAccept = eligible,
                    busy = busy, inactive = !eligible),
            { onAccept(proposal) }, onCancel, Modifier.testTag(
                if (eligible) "task_proposal" else "inactive_task_proposal"),
        )
        val complete = (proposal.action as? TaskAction)?.mutation as? TaskMutation.Complete
        if (complete != null) dev.metis.agent.presentation.designsystem.ActionProposal(
            ProposalDisplay("Complete task", listOf("Open → Completed", "Reminders and schedules stay as they are.",
                "Expires: ${proposal.expiresAt}"),
                "R1 · local write", proposal.reason, canAccept = eligible, busy = busy, inactive = !eligible),
            { onAccept(proposal) }, onCancel, Modifier.testTag(
                if (eligible) "task_completion_proposal" else "inactive_task_completion_proposal"),
        )
        val mutation = (proposal.action as? TaskAction)?.mutation
        if (mutation is TaskMutation.Update || mutation is TaskMutation.Postpone || mutation is TaskMutation.Delete) {
            TaskEditProposal(proposal, busy, onAccept, onCancel, eligible)
        }
    }
    result.completedActions.singleOrNull()?.let { receipt ->
        if (receipt.outcome.undo == UndoCapability.LOCAL_REVISION_CHECKED) SecondaryButton(
            if (eligible) "Undo accepted action" else "Undo unavailable", { onUndo(receipt) },
            Modifier.testTag(if (eligible) "undo_created_task" else "inactive_undo"),
                enabled = eligible && !busy)
    }
}
@Composable
private fun TaskEditProposal(
    proposal: ActionProposal, busy: Boolean, onAccept: (ActionProposal) -> Unit, onCancel: () -> Unit,
        eligible: Boolean = true,
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
            canAccept = eligible, busy = busy, inactive = !eligible), { onAccept(proposal) }, onCancel,
                Modifier.testTag(if (eligible) "task_edit_proposal" else "inactive_task_edit_proposal"))
}
