@file:Suppress("MaxLineLength") // long parameter lists in test helper functions

package dev.metis.agent

import dev.metis.agent.domain.decision.DecisionInputs
import dev.metis.agent.domain.planner.PlannerEngine
import dev.metis.agent.domain.planner.TimeSpan
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RoutineRule
import dev.metis.agent.domain.storage.RuleHardness
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleBlock
import dev.metis.agent.domain.storage.ScheduleKind
import dev.metis.agent.domain.storage.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlannerTimetableTest {

    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 11, 9) // Monday (weekday = 1)
    private val windowStart = monday.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    private val windowEnd = monday.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
    private val window = TimeSpan(windowStart, windowEnd)

    private fun task(estimatedSeconds: Long, dueAt: Long? = null, id: String = java.util.UUID.randomUUID().toString()) = SavedTask(
        title = "Task $id",
        status = TaskStatus.OPEN,
        priority = 2,
        estimatedSeconds = estimatedSeconds,
        dueAt = dueAt,
        dueZoneId = if (dueAt != null) zone.id else null,
        metadata = RecordMetadata(id, windowStart - 10_000),
    )

    @Test
    fun plannerRespectsFixedBlocksAsUnavailableTime() {
        // Fixed class from 09:00 to 11:00 (540 to 660)
        val fixedBlock = ScheduleBlock(
            id = "math-block",
            weekday = 1,
            startMin = 9 * 60,
            endMin = 11 * 60,
            title = "Math",
            kind = ScheduleKind.CLASS,
            fixed = true,
        )

        // 1-hour task
        val t = task(3600)
        val inputs = DecisionInputs(tasks = listOf(t), dependencies = emptyList())

        val output = PlannerEngine.generate(
            inputs = inputs,
            schedules = emptyList(),
            window = window,
            fixedBlocks = listOf(fixedBlock),
            routineRules = emptyList(),
            zone = zone,
        )

        assertEquals(1, output.blocks.size)
        val planned = output.blocks.first()
        val classEndMillis = monday.atTime(11, 0).atZone(zone).toInstant().toEpochMilli()
        // Task must NOT overlap with 09:00-11:00 Math block
        assertTrue(planned.span.start >= classEndMillis)
    }

    @Test
    fun plannerKeepsRoutineWindowFreeAndExplains() {
        // Gym window: 17:00 - 18:30 (1020 - 1110)
        val gymRule = RoutineRule(
            id = "gym-rule",
            key = "gym",
            windowStartMin = 17 * 60,
            windowEndMin = 18 * 60 + 30,
            weekdaysMask = 127,
            hardness = RuleHardness.SOFT,
        )

        // Task takes 2 hours (7200 seconds)
        val t = task(7200)
        val inputs = DecisionInputs(tasks = listOf(t), dependencies = emptyList())

        val output = PlannerEngine.generate(
            inputs = inputs,
            schedules = emptyList(),
            window = window,
            fixedBlocks = emptyList(),
            routineRules = listOf(gymRule),
            zone = zone,
        )

        assertEquals(1, output.blocks.size)
        val gymStart = monday.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val gymEnd = monday.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
        val gymSpan = TimeSpan(gymStart, gymEnd)

        // Task should not overlap gym
        assertFalse(output.blocks.first().span.overlaps(gymSpan))
        // Notes must surface: "kept gym window free"
        assertTrue(output.notes.contains("kept gym window free"))
    }

    @Test
    fun overdueTaskCanUseRoutineWindow() {
        // Only 1 hour free before gym, but task needs 3 hours and is overdue
        val fixedMorning = ScheduleBlock(
            id = "fixed-morning",
            weekday = 1,
            startMin = 9 * 60,
            endMin = 16 * 60, // 09:00 to 16:00 is busy
            title = "Long Lab",
            kind = ScheduleKind.LAB,
            fixed = true,
        )
        // Gym from 17:00 to 19:00
        val gymRule = RoutineRule(
            id = "gym-rule",
            key = "gym",
            windowStartMin = 17 * 60,
            windowEndMin = 19 * 60,
            weekdaysMask = 127,
            hardness = RuleHardness.SOFT,
        )

        // Overdue task needing 2 hours (16:00 to 18:00 must use gym window)
        val overdueTask = task(7200, dueAt = windowStart - 1000)
        val inputs = DecisionInputs(tasks = listOf(overdueTask), dependencies = emptyList())

        val output = PlannerEngine.generate(
            inputs = inputs,
            schedules = emptyList(),
            window = window,
            fixedBlocks = listOf(fixedMorning),
            routineRules = listOf(gymRule),
            zone = zone,
        )

        assertEquals(1, output.blocks.size)
        // Overdue task was scheduled even though it used routine time
        val planned = output.blocks.first()
        val gymStart = monday.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val gymEnd = monday.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(planned.span.overlaps(TimeSpan(gymStart, gymEnd)))
    }

    @Test
    fun noScheduleRegressionProducesIdenticalOutput() {
        val t = task(3600)
        val inputs = DecisionInputs(tasks = listOf(t), dependencies = emptyList())

        val baselineOutput = PlannerEngine.generate(
            inputs = inputs,
            schedules = emptyList(),
            window = window,
        )

        val newOutput = PlannerEngine.generate(
            inputs = inputs,
            schedules = emptyList(),
            window = window,
            fixedBlocks = emptyList(),
            routineRules = emptyList(),
            zone = zone,
        )

        assertEquals(baselineOutput.blocks, newOutput.blocks)
        assertEquals(baselineOutput.freeMillis, newOutput.freeMillis)
        assertEquals(baselineOutput.existingConflicts, newOutput.existingConflicts)
        assertEquals(baselineOutput.excludedCount, newOutput.excludedCount)
        assertTrue(newOutput.notes.isEmpty())
    }
}
