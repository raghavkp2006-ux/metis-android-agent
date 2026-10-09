package dev.metis.agent.domain.planner

import dev.metis.agent.domain.decision.DecisionContext
import dev.metis.agent.domain.decision.DecisionEngine
import dev.metis.agent.domain.decision.DecisionInputs
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleStatus

data class TimeSpan(val start: Long, val end: Long) {
    init { require(end > start) }
    fun overlaps(other: TimeSpan) = start < other.end && other.start < end
}
data class PlannedTask(val task: SavedTask, val span: TimeSpan)
data class PlannerOutput(val blocks: List<PlannedTask>, val freeMillis: Long, val existingConflicts: Int,
    val excludedCount: Int)

/** Whole tasks only, earliest deadline first, never assumes scheduled work has completed prerequisites. */
object PlannerEngine {
    const val VERSION = "DEADLINE_PLAN_V1"
    const val MAX_BLOCKS = 16

    fun generate(inputs: DecisionInputs, schedules: List<SavedSchedule>, window: TimeSpan,
        bookedTaskIds: Set<String> = emptySet()): PlannerOutput {
        val busy = schedules.filter { it.status in setOf(ScheduleStatus.ACCEPTED, ScheduleStatus.COMPLETED) }
            .map { TimeSpan(it.startAt, it.endAt) }.filter { it.overlaps(window) }
        val gaps = freeTime(window, busy).toMutableList()
        val free = gaps.sumOf { it.end - it.start }
        val booked = schedules.filter { it.status == ScheduleStatus.ACCEPTED && it.endAt > window.start }
            .mapNotNull { it.taskId }.toSet() + bookedTaskIds
        val candidates = DecisionEngine.rank(inputs, DecisionContext(window.start)).map { it.task }
            .filter { it.estimatedSeconds != null && it.metadata.id !in booked }
            .sortedWith(compareBy<SavedTask> { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.priority }
                .thenBy { it.metadata.createdAt }.thenBy { it.metadata.id })
        val blocks = buildList {
            candidates.forEach { task ->
                if (size < MAX_BLOCKS) allocate(task, gaps)?.let { add(PlannedTask(task, it)) }
            }
        }
        val conflicts = busy.indices.sumOf { i -> (i + 1 until busy.size).count { busy[i].overlaps(busy[it]) } }
        return PlannerOutput(blocks, free, conflicts, inputs.tasks.size - blocks.size)
    }

    fun freeTime(window: TimeSpan, busy: List<TimeSpan>): List<TimeSpan> {
        var cursor = window.start
        return buildList {
            busy.filter { it.overlaps(window) }.sortedBy { it.start }.forEach {
                val start = maxOf(window.start, it.start)
                if (cursor < start) add(TimeSpan(cursor, start))
                cursor = maxOf(cursor, minOf(window.end, it.end))
            }
            if (cursor < window.end) add(TimeSpan(cursor, window.end))
        }
    }

    @Suppress("ReturnCount") // Overflow and no-fit checks precede mutating the free gap list.
    private fun allocate(task: SavedTask, gaps: MutableList<TimeSpan>): TimeSpan? {
        val seconds = requireNotNull(task.estimatedSeconds)
        // Reject oversized estimates before multiplication; the planning window is at most one local day.
        if (seconds > MAX_SECONDS) return null
        val duration = seconds * MILLIS_PER_SECOND
        val index = gaps.indexOfFirst { gap -> gap.end - gap.start >= duration &&
            (task.dueAt == null || gap.start + duration <= task.dueAt) }
        if (index < 0) return null
        val gap = gaps.removeAt(index)
        val block = TimeSpan(gap.start, gap.start + duration)
        if (block.end < gap.end) gaps.add(index, TimeSpan(block.end, gap.end))
        return block
    }

    private const val MAX_SECONDS = 90_000L
    private const val MILLIS_PER_SECOND = 1_000L
}
