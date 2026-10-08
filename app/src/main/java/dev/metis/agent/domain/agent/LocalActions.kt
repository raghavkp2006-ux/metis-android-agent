package dev.metis.agent.domain.agent

import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.MemoryType
import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.SavedPreference
import java.util.UUID

data class ActionIdentity(
    val id: UUID,
    val requestId: UUID,
    val idempotencyKey: UUID,
    val schemaVersion: Int = PROTOCOL_VERSION,
) { init { require(schemaVersion == PROTOCOL_VERSION) } }

sealed interface Action {
    val identity: ActionIdentity
    val type: ActionType
}

sealed interface FieldChange<out T> {
    data object Unchanged : FieldChange<Nothing>
    data class Set<T>(val value: T) : FieldChange<T>
}

data class TaskPatch(
    val title: FieldChange<String> = FieldChange.Unchanged,
    val due: FieldChange<ResolvedTime?> = FieldChange.Unchanged,
    val priority: FieldChange<Int> = FieldChange.Unchanged,
) {
    init {
        require(title != FieldChange.Unchanged || due != FieldChange.Unchanged || priority != FieldChange.Unchanged)
        if (title is FieldChange.Set) requireText(title.value)
        if (priority is FieldChange.Set) require(priority.value in 0..MAX_PRIORITY)
    }
}

sealed interface TaskMutation {
    data class Create(
        val title: String,
        val due: ResolvedTime? = null,
        val priority: Int = 0,
        val projectId: UUID? = null,
        val goalId: UUID? = null,
    ) : TaskMutation { init { requireText(title); require(priority in 0..MAX_PRIORITY) } }
    data class Update(val target: RevisionTarget, val patch: TaskPatch) : TaskMutation {
        init { requireTarget(target, MemoryEntityType.TASK) }
    }
    data class Complete(val target: RevisionTarget) : TaskMutation {
        init { requireTarget(target, MemoryEntityType.TASK) }
    }
    data class Postpone(val target: RevisionTarget, val due: ResolvedTime) : TaskMutation {
        init { requireTarget(target, MemoryEntityType.TASK) }
    }
    data class Delete(val target: RevisionTarget) : TaskMutation {
        init { requireTarget(target, MemoryEntityType.TASK) }
    }
}

data class TaskAction(override val identity: ActionIdentity, val mutation: TaskMutation) : Action {
    override val type = ActionType.TASK
}

/** Agent memory mutations contain explicit user content only; derived provenance is never accepted here. */
sealed interface MemoryMutation {
    data class Create(val type: MemoryType, val content: String) : MemoryMutation {
        init { requireText(content) }
    }
    data class Update(val target: RevisionTarget, val type: MemoryType, val content: String) : MemoryMutation {
        init { requireTarget(target, MemoryEntityType.MEMORY); requireText(content) }
    }
    data class Delete(val target: RevisionTarget) : MemoryMutation {
        init { requireTarget(target, MemoryEntityType.MEMORY) }
    }
}
data class MemoryAction(override val identity: ActionIdentity, val mutation: MemoryMutation) : Action {
    override val type = ActionType.MEMORY
}

sealed interface GoalMutation {
    data class Create(val title: String) : GoalMutation { init { requireText(title) } }
    data class Update(val target: RevisionTarget, val title: String) : GoalMutation {
        init { requireTarget(target, MemoryEntityType.GOAL); requireText(title) }
    }
}
data class GoalAction(override val identity: ActionIdentity, val mutation: GoalMutation) : Action {
    override val type = ActionType.GOAL
}
data class PreferenceAction(
    override val identity: ActionIdentity,
    val key: PreferenceKey,
    val value: PreferenceValue,
    val target: RevisionTarget? = null,
) : Action {
    override val type = ActionType.PREFERENCE
    init {
        SavedPreference(key, value)
        target?.let { requireTarget(it, MemoryEntityType.PREFERENCE) }
    }
}
private const val MAX_PRIORITY = 3
