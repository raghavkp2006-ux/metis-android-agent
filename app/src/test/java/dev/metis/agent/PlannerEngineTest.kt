package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.decision.DecisionInputs
import dev.metis.agent.domain.planner.PlannerEngine
import dev.metis.agent.domain.planner.PlannerWindow
import dev.metis.agent.domain.planner.TimeSpan
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.domain.storage.TaskStatus
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerEngineTest {
    private val now = Instant.parse("2026-10-09T07:00:00Z")
    private val start = now.plusSeconds(3_600).toEpochMilli()
    private val window = TimeSpan(start, start + 4 * HOUR)

    @Test fun overlappingNestedClippedAndAdjacentCommitmentsProduceExactGaps() {
        val gaps = PlannerEngine.freeTime(window, listOf(TimeSpan(start - HOUR, start + HOUR),
            TimeSpan(start, start + HOUR / 2), TimeSpan(start + HOUR, start + 2 * HOUR),
            TimeSpan(start + 3 * HOUR, start + 5 * HOUR)))
        assertEquals(listOf(TimeSpan(start + 2 * HOUR, start + 3 * HOUR)), gaps)
    }

    @Test fun usesEarliestDeadlineBeforePriorityAndStableTieBreaks() {
        val early = task("Early", 600, start + HOUR, priority = 0)
        val later = task("Later", 600, start + 2 * HOUR, priority = 3)
        val unbounded = task("No deadline", 600, priority = 3)
        val tasks = listOf(unbounded, later, early)
        val result = PlannerEngine.generate(DecisionInputs(tasks, emptyList()), emptyList(), window)
        assertEquals(listOf(early, later, unbounded), result.blocks.map { it.task })
        assertEquals(result, PlannerEngine.generate(DecisionInputs(tasks.reversed(), emptyList()), emptyList(), window))
    }

    @Test fun skipsTooLargeTasksAndAllocatesLaterShortTasksWithoutSplittingOrConflicts() {
        val busy = schedule(start + HOUR, start + 2 * HOUR)
        val oversized = task("Oversized", 5 * 3_600L)
        val short = task("Short", 1_800)
        val result = PlannerEngine.generate(DecisionInputs(listOf(oversized, short), emptyList()), listOf(busy), window)
        assertEquals(listOf(short), result.blocks.map { it.task })
        assertEquals(start + HOUR / 2, result.blocks.single().span.end)
        assertEquals(3 * HOUR, result.freeMillis)
    }

    @Test fun unknownEstimatesPastDeadlinesAndUnfinishedPrerequisitesAreNeverScheduled() {
        val prerequisite = task("Prerequisite", 600)
        val dependent = task("Dependent", 600)
        val tasks = listOf(task("Unknown", null), task("Overdue", 600, start - 1),
            task("Cannot meet deadline", 600, start + 1), prerequisite, dependent)
        val inputs = DecisionInputs(tasks, listOf(SavedTaskDependency(dependent.metadata.id, prerequisite.metadata.id)))
        val result = PlannerEngine.generate(inputs, emptyList(), window)
        assertEquals(listOf(prerequisite), result.blocks.map { it.task })
        assertEquals(4, result.excludedCount)
    }

    @Test fun completedPrerequisiteAllowsTaskAndAcceptedFutureBookingPreventsDuplicateWork() {
        val prerequisite = task("Done", 600).copy(status = TaskStatus.COMPLETED, completedAt = start - 1)
        val dependent = task("Dependent", 600)
        val inputs = DecisionInputs(listOf(prerequisite, dependent),
            listOf(SavedTaskDependency(dependent.metadata.id, prerequisite.metadata.id)))
        assertEquals(1, PlannerEngine.generate(inputs, emptyList(), window).blocks.size)
        val elsewhere = schedule(window.end + 1, window.end + HOUR).copy(taskId = dependent.metadata.id)
        assertTrue(PlannerEngine.generate(inputs, listOf(elsewhere), window).blocks.isEmpty())
        assertTrue(PlannerEngine.generate(inputs, emptyList(), window, setOf(dependent.metadata.id)).blocks.isEmpty())
    }

    @Test fun proposedAndCancelledBlocksDoNotReserveTimeAndExistingConflictsAreReported() {
        val busy = listOf(schedule(start, start + HOUR), schedule(start + HOUR / 2, start + 2 * HOUR),
            schedule(start + 2 * HOUR, window.end).copy(status = ScheduleStatus.PROPOSED),
            schedule(start + 2 * HOUR, window.end).copy(status = ScheduleStatus.CANCELLED))
        val result = PlannerEngine.generate(DecisionInputs(listOf(task("Work", 600)), emptyList()), busy, window)
        assertEquals(1, result.existingConflicts)
        assertEquals(start + 2 * HOUR, result.blocks.single().span.start)
    }

    @Test fun hugeDurationCannotOverflowAndBoundedPlanContainsAtMostSixteenBlocks() {
        val tasks = List(20) { task("Short $it", 60) } + task("Huge", Long.MAX_VALUE)
        val result = PlannerEngine.generate(DecisionInputs(tasks, emptyList()), emptyList(), window)
        assertEquals(16, result.blocks.size)
        result.blocks.zipWithNext().forEach { (a, b) -> assertFalse(a.span.overlaps(b.span)) }
    }

    @Test fun defaultDayAndEveningDiscloseAssumptionsAndUseSavedDayStart() {
        val zone = ZoneId.of("UTC")
        val day = requireNotNull(PlannerWindow.resolve("Plan my day", now, zone, LocalTime.of(9, 0)))
        assertTrue(day.assumed)
        assertEquals(now.plusSeconds(7_200).toEpochMilli(), day.span.start)
        val evening = requireNotNull(PlannerWindow.resolve("Plan my evening", now, zone, null))
        assertEquals("2026-10-09T18:00:00Z", Instant.ofEpochMilli(evening.span.start).toString())
        val remaining = requireNotNull(PlannerWindow.resolve("Plan my day", now, zone, null))
        assertTrue(remaining.span.start > now.toEpochMilli())
    }

    @Test fun explicitTomorrowWindowsRejectPastOvernightAndInvalidTimes() {
        val zone = ZoneId.of("UTC")
        val tomorrow = requireNotNull(PlannerWindow.resolve("plan my day tomorrow from 09:00 to 17:00",
            now, zone, null))
        assertFalse(tomorrow.assumed)
        assertEquals("2026-10-10T09:00:00Z", Instant.ofEpochMilli(tomorrow.span.start).toString())
        listOf("plan my day from 06:00 to 17:00", "plan my day from 22:00 to 08:00",
            "plan my day from 25:00 to 26:00").forEach { assertNull(PlannerWindow.resolve(it, now, zone, null)) }
    }

    @Test fun daylightSavingGapAndOverlapAreNotGuessed() {
        val zone = ZoneId.of("America/New_York")
        assertNull(PlannerWindow.resolve("plan my day tomorrow from 02:30 to 04:00",
            Instant.parse("2026-03-07T12:00:00Z"), zone, null))
        assertNull(PlannerWindow.resolve("plan my day tomorrow from 01:30 to 03:00",
            Instant.parse("2026-10-31T12:00:00Z"), zone, null))
    }

    @Test fun closedLanguageGrammarRecognizesPlanWithoutOverridingNegation() {
        fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, now)
        assertEquals(AgentIntent.PLAN_DAY, EnglishRules().parse(request("Please plan my evening tomorrow.")).intent)
        assertEquals(AgentIntent.UNKNOWN, EnglishRules().parse(request("don't plan my day")).intent)
        assertEquals(AgentIntent.UNKNOWN, EnglishRules().parse(request("plan my week")).intent)
        assertEquals(AgentIntent.PLAN_DAY,
            EnglishRules().parse(request("plan my day").copy(source = InputSource.VOICE)).intent)
    }

    private fun task(title: String, seconds: Long?, due: Long? = null, priority: Int = 0) =
        SavedTask(title, RecordMetadata(createdAt = now.toEpochMilli()), dueAt = due,
            dueZoneId = due?.let { "UTC" }, estimatedSeconds = seconds, priority = priority)
    private fun schedule(from: Long, until: Long) = SavedSchedule("Synthetic commitment", from, until, "UTC",
        "Explicit commitment", status = ScheduleStatus.ACCEPTED)
    private companion object { const val HOUR = 3_600_000L }
}
