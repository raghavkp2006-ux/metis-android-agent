@file:Suppress(
    "LongParameterList", // generate() has 7 params; group via data class would add boilerplate
    "MagicNumber",       // 60-based minute/hour arithmetic and 12-hour clock conversion
    "MaxLineLength",     // long lambda chains and SQL-style time conversion lines
)

package dev.metis.agent.domain.planner

import dev.metis.agent.domain.decision.DecisionContext
import dev.metis.agent.domain.decision.DecisionEngine
import dev.metis.agent.domain.decision.DecisionInputs
import dev.metis.agent.domain.storage.RoutineRule
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleBlock
import dev.metis.agent.domain.storage.ScheduleStatus
import java.time.Instant
import java.time.ZoneId

data class TimeSpan(val start: Long, val end: Long) {
    init { require(end > start) }
    fun overlaps(other: TimeSpan) = start < other.end && other.start < end
}

data class PlannedTask(val task: SavedTask, val span: TimeSpan)

data class PlannerOutput(
    val blocks: List<PlannedTask>,
    val freeMillis: Long,
    val existingConflicts: Int,
    val excludedCount: Int,
    val notes: List<String> = emptyList(),
)

/** Whole tasks only, earliest deadline first, never assumes scheduled work has completed prerequisites. */
object PlannerEngine {
    const val VERSION = "DEADLINE_PLAN_V1"
    const val MAX_BLOCKS = 16

    fun generate(
        inputs: DecisionInputs,
        schedules: List<SavedSchedule>,
        window: TimeSpan,
        bookedTaskIds: Set<String> = emptySet(),
        fixedBlocks: List<ScheduleBlock> = emptyList(),
        routineRules: List<RoutineRule> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): PlannerOutput {
        val date = Instant.ofEpochMilli(window.start).atZone(zone).toLocalDate()
        val weekday = date.dayOfWeek.value

        val scheduleBusy = schedules.filter { it.status in setOf(ScheduleStatus.ACCEPTED, ScheduleStatus.COMPLETED) }
            .map { TimeSpan(it.startAt, it.endAt) }.filter { it.overlaps(window) }

        val fixedBlockBusy = fixedBlocks.filter { it.fixed && it.weekday == weekday }.mapNotNull { block ->
            val start = date.atTime(block.startMin / 60, block.startMin % 60).atZone(zone).toInstant().toEpochMilli()
            val end = date.atTime(block.endMin / 60, block.endMin % 60).atZone(zone).toInstant().toEpochMilli()
            if (end > start) TimeSpan(start, end) else null
        }.filter { it.overlaps(window) }

        val busy = (scheduleBusy + fixedBlockBusy).distinct()
        val gaps = freeTime(window, busy).toMutableList()
        val free = gaps.sumOf { it.end - it.start }
        val booked = schedules.filter { it.status == ScheduleStatus.ACCEPTED && it.endAt > window.start }
            .mapNotNull { it.taskId }.toSet() + bookedTaskIds

        val rulesForDay = routineRules.filter { it.matchesWeekday(weekday) }
        val routineSpans = rulesForDay.mapNotNull { rule ->
            val start = date.atTime(rule.windowStartMin / 60, rule.windowStartMin % 60).atZone(zone).toInstant().toEpochMilli()
            val end = date.atTime(rule.windowEndMin / 60, rule.windowEndMin % 60).atZone(zone).toInstant().toEpochMilli()
            if (end > start) Pair(rule, TimeSpan(start, end)) else null
        }.filter { it.second.overlaps(window) }

        val candidates = DecisionEngine.rank(inputs, DecisionContext(window.start)).map { it.task }
            .filter { it.estimatedSeconds != null && it.metadata.id !in booked }
            .sortedWith(compareBy<SavedTask> { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.priority }
                .thenBy { it.metadata.createdAt }.thenBy { it.metadata.id })

        val blocks = buildList {
            candidates.forEach { task ->
                if (size < MAX_BLOCKS) {
                    val isOverdue = task.dueAt != null && task.dueAt < window.start
                    allocate(task, gaps, routineSpans.map { it.second }, isOverdue)?.let { add(PlannedTask(task, it)) }
                }
            }
        }

        val notes = mutableListOf<String>()
        routineSpans.forEach { (rule, span) ->
            if (blocks.none { it.span.overlaps(span) }) {
                notes.add("kept ${rule.key} window free")
            }
        }

        val conflicts = busy.indices.sumOf { i -> (i + 1 until busy.size).count { busy[i].overlaps(busy[it]) } }
        return PlannerOutput(blocks, free, conflicts, inputs.tasks.size - blocks.size, notes)
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

    @Suppress("ReturnCount")
    private fun allocate(
        task: SavedTask,
        gaps: MutableList<TimeSpan>,
        routineSpans: List<TimeSpan>,
        isOverdue: Boolean,
    ): TimeSpan? {
        val seconds = requireNotNull(task.estimatedSeconds)
        if (seconds > MAX_SECONDS) return null
        val duration = seconds * MILLIS_PER_SECOND

        val canUseRoutine = isOverdue && routineSpans.isNotEmpty()
        if (routineSpans.isNotEmpty()) {
            val nonOverlapping = allocateOutsideRoutines(task, gaps, routineSpans, duration, canUseRoutine)
            if (nonOverlapping != null) return nonOverlapping
            if (!canUseRoutine) return null
        }

        val index = gaps.indexOfFirst { gap ->
            gap.end - gap.start >= duration && (task.dueAt == null || canUseRoutine || gap.start + duration <= task.dueAt)
        }
        if (index < 0) return null
        val gap = gaps.removeAt(index)
        val block = TimeSpan(gap.start, gap.start + duration)
        if (block.end < gap.end) gaps.add(index, TimeSpan(block.end, gap.end))
        return block
    }

    private fun allocateOutsideRoutines(
        task: SavedTask,
        gaps: MutableList<TimeSpan>,
        routineSpans: List<TimeSpan>,
        duration: Long,
        allowPastDeadline: Boolean = false,
    ): TimeSpan? {
        for (i in gaps.indices) {
            val gap = gaps[i]
            val subGaps = freeTime(gap, routineSpans)
            val subIndex = subGaps.indexOfFirst { sub ->
                sub.end - sub.start >= duration && (task.dueAt == null || allowPastDeadline || sub.start + duration <= task.dueAt)
            }
            if (subIndex >= 0) {
                val sub = subGaps[subIndex]
                val block = TimeSpan(sub.start, sub.start + duration)
                gaps.removeAt(i)
                val remaining = mutableListOf<TimeSpan>()
                if (block.start > gap.start) remaining.add(TimeSpan(gap.start, block.start))
                if (gap.end > block.end) remaining.add(TimeSpan(block.end, gap.end))
                gaps.addAll(i, remaining)
                return block
            }
        }
        return null
    }

    private const val MAX_SECONDS = 90_000L
    private const val MILLIS_PER_SECOND = 1_000L
}
