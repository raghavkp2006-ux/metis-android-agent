package dev.metis.agent.domain.agent

import dev.metis.agent.domain.storage.MemoryEntityType
import java.util.UUID

enum class SchedulingPrecision { EXACT, APPROXIMATE }

data class CreateReminderAction(
    override val identity: ActionIdentity,
    val title: String,
    val trigger: ResolvedTime,
    val precision: SchedulingPrecision,
    val personId: UUID? = null,
    val taskId: UUID? = null,
) : Action {
    override val type = ActionType.CREATE_REMINDER
    init { requireText(title) }
}
data class CancelReminderAction(override val identity: ActionIdentity, val target: RevisionTarget) : Action {
    override val type = ActionType.CANCEL_REMINDER
    init { requireTarget(target, MemoryEntityType.REMINDER) }
}
data class CreateAlarmAction(
    override val identity: ActionIdentity,
    val trigger: ResolvedTime,
    val label: String,
) : Action {
    override val type = ActionType.ALARM
    init { requireText(label) }
}
data class CreateTimerAction(
    override val identity: ActionIdentity,
    val durationSeconds: Long,
    val label: String,
) : Action {
    override val type = ActionType.TIMER
    init { require(durationSeconds > 0); requireText(label) }
}
sealed interface FocusOperation {
    data class Start(val durationSeconds: Long, val taskId: UUID? = null) : FocusOperation {
        init { require(durationSeconds > 0) }
    }
    data class Stop(val target: RevisionTarget) : FocusOperation {
        init { requireTarget(target, MemoryEntityType.FOCUS_SESSION) }
    }
}
data class FocusAction(override val identity: ActionIdentity, val operation: FocusOperation) : Action {
    override val type = ActionType.FOCUS
}

/** Reminder proposals require their own acceptance; accepting blocks cannot authorize notifications. */
class AcceptPlanAction(
    override val identity: ActionIdentity,
    val planId: UUID,
    val expectedRevision: Long,
    acceptedBlocks: List<RevisionTarget>,
    reminderProposalIds: List<UUID> = emptyList(),
) : Action {
    override val type = ActionType.ACCEPT_PLAN
    val acceptedBlocks = frozenList(acceptedBlocks)
    val reminderProposalIds = frozenList(reminderProposalIds)
    init {
        require(expectedRevision >= 0 && acceptedBlocks.isNotEmpty())
        require(acceptedBlocks.map { it.reference.id }.distinct().size == acceptedBlocks.size)
        acceptedBlocks.forEach { requireTarget(it, MemoryEntityType.SCHEDULE) }
        require(reminderProposalIds.distinct().size == reminderProposalIds.size)
    }
}
