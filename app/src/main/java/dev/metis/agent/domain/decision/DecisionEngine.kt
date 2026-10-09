package dev.metis.agent.domain.decision

import dev.metis.agent.domain.storage.GoalStatus
import dev.metis.agent.domain.storage.ProjectStatus
import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import dev.metis.agent.domain.storage.TaskStatus
import java.time.Duration

data class DecisionContext(val now: Long, val availableSeconds: Long? = null, val focusMinutes: Int? = null) {
    init {
        require(availableSeconds == null || availableSeconds in 1..MAX_AVAILABLE_SECONDS)
        require(focusMinutes == null || focusMinutes in MIN_FOCUS_MINUTES..MAX_FOCUS_MINUTES)
    }
}

data class DecisionInputs(
    val tasks: List<SavedTask>, val dependencies: List<SavedTaskDependency>,
    val goals: List<SavedGoal> = emptyList(), val projects: List<SavedProject> = emptyList(),
)

/** Numeric contributions, not probabilities. The final score is clamped after subtracting effort. */
data class DecisionScore(
    val deadline: Double, val priority: Double, val goal: Double,
    val overdue: Double, val preference: Double, val effortPenalty: Double,
) {
    init {
        listOf(deadline, priority, goal, overdue, preference, effortPenalty).forEach {
            require(it.isFinite() && it in 0.0..1.0)
        }
    }
    val total: Double get() = (deadline + priority + goal + overdue + preference - effortPenalty).coerceIn(0.0, 1.0)
}

data class TaskDecision(val task: SavedTask, val components: DecisionScore, val goal: SavedGoal?)

/** Versioned deterministic baseline. No behavioral inference, calendar assumptions or executor. */
@Suppress("MagicNumber") // Frozen, documented V1 scoring weights and time horizons.
object DecisionEngine {
    const val VERSION = "TASK_DECISION_V1"

    fun rank(inputs: DecisionInputs, context: DecisionContext): List<TaskDecision> {
        val tasks = inputs.tasks.associateBy { it.metadata.id }
        val dependencies = inputs.dependencies.groupBy { it.taskId }
        val goals = inputs.goals.associateBy { it.metadata.id }
        val projects = inputs.projects.associateBy { it.metadata.id }
        return inputs.tasks.filter { task ->
            task.status == TaskStatus.OPEN && task.recurrenceRule == null &&
                (task.projectId == null || projects[task.projectId]?.status == ProjectStatus.ACTIVE) &&
                activeGoal(task, goals, projects) &&
                dependencies[task.metadata.id].orEmpty().all {
                    tasks[it.dependsOnTaskId]?.status == TaskStatus.COMPLETED
                } &&
                fits(task, context)
        }.map { task ->
            val goal = goals[task.goalId]
            TaskDecision(task, score(task, goal, context), goal)
        }.sortedWith(compareByDescending<TaskDecision> { it.components.total }
            .thenBy { it.task.dueAt ?: Long.MAX_VALUE }.thenBy { it.task.metadata.createdAt }
            .thenBy { it.task.metadata.id })
    }

    private fun fits(task: SavedTask, context: DecisionContext): Boolean =
        context.availableSeconds == null || task.estimatedSeconds?.let { it <= context.availableSeconds } == true

    private fun activeGoal(task: SavedTask, goals: Map<String, SavedGoal>,
        projects: Map<String, SavedProject>): Boolean {
        val goal = task.goalId?.let { goals[it] }
        return task.goalId == null || (goal?.status == GoalStatus.ACTIVE &&
            (goal.projectId == null || projects[goal.projectId]?.status == ProjectStatus.ACTIVE))
    }

    private fun score(task: SavedTask, goal: SavedGoal?, context: DecisionContext): DecisionScore {
        val effort = task.estimatedSeconds?.let { (it.toDouble() / 14_400.0).coerceAtMost(1.0) } ?: 0.5
        val preference = if (context.focusMinutes != null && task.estimatedSeconds != null &&
            task.estimatedSeconds <= context.focusMinutes * 60L) 1.0 else 0.0
        return DecisionScore(deadline(task, context.now) * 0.35, task.priority / 3.0 * 0.30,
            goal?.let { (it.priority + 1) / 4.0 * 0.15 } ?: 0.0,
            if (task.dueAt != null && task.dueAt < context.now) 0.10 else 0.0,
            preference * 0.10, effort * 0.10)
    }

    private fun deadline(task: SavedTask, now: Long): Double = task.dueAt?.let { due ->
            val hours = Duration.between(java.time.Instant.ofEpochMilli(now),
                java.time.Instant.ofEpochMilli(due)).toHours()
            when { due <= now -> 1.0; hours < 24 -> 0.9; hours < 72 -> 0.6; hours < 168 -> 0.3; else -> 0.1 }
        } ?: 0.0
}

private const val MAX_AVAILABLE_SECONDS = 86_400L
private const val MIN_FOCUS_MINUTES = 5
private const val MAX_FOCUS_MINUTES = 240
