package dev.metis.agent

import dev.metis.agent.domain.proactive.ProactiveCandidate
import dev.metis.agent.domain.proactive.ProactiveEngine
import dev.metis.agent.domain.proactive.ProactiveKind
import dev.metis.agent.domain.proactive.ProactivePostponement
import dev.metis.agent.domain.proactive.ProactivePriority
import dev.metis.agent.domain.proactive.ProactiveSuppressionPolicy
import dev.metis.agent.domain.proactive.ProactiveSuppression
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.domain.storage.TaskStatus
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveEngineTest {
    @Test fun deadlinePriorityBoundariesAndCompletedTasksAreDeterministic() {
        val tasks = listOf(
            task("urgent", NOW + 60 * MINUTE), task("medium", NOW + 3 * 60 * MINUTE),
            task("low", NOW + 13 * 60 * MINUTE), task("missed", NOW - 1),
            task("done", NOW + MINUTE, TaskStatus.COMPLETED),
        )
        val result = ProactiveEngine.candidates(tasks, emptyList(), emptyList(), emptyList(), NOW)
        assertEquals(listOf(ProactivePriority.URGENT, ProactivePriority.IMPORTANT,
            ProactivePriority.MEDIUM, ProactivePriority.LOW), result.map { it.priority })
        assertEquals(listOf(ProactiveKind.TASK_DEADLINE, ProactiveKind.MISSED_TASK,
            ProactiveKind.TASK_DEADLINE, ProactiveKind.TASK_DEADLINE), result.map { it.kind })
    }

    @Test fun promisesMustBeOpenAndWithinWindow() {
        val promises = listOf(
            SavedPromise(PERSON_ID, "Due now", "OPEN", dueAt = NOW + MINUTE),
            SavedPromise(PERSON_ID, "Later", "OPEN", dueAt = NOW + 25 * 60 * MINUTE),
            SavedPromise(PERSON_ID, "Done", "FULFILLED", dueAt = NOW + MINUTE),
        )
        val result = ProactiveEngine.candidates(emptyList(), promises, emptyList(), emptyList(), NOW)
        assertEquals(1, result.size)
        assertEquals(ProactiveKind.PROMISE_DUE, result.single().kind)
        assertEquals(ProactivePriority.URGENT, result.single().priority)
    }

    @Test fun onlyOverlappingAcceptedBlocksAreConflicts() {
        val first = schedule("first", NOW + MINUTE, NOW + 60 * MINUTE, ScheduleStatus.ACCEPTED)
        val overlap = schedule("overlap", NOW + 30 * MINUTE, NOW + 90 * MINUTE, ScheduleStatus.ACCEPTED)
        val proposed = schedule("proposed", NOW + 35 * MINUTE, NOW + 50 * MINUTE, ScheduleStatus.PROPOSED)
        val distinct = schedule("distinct", NOW + 100 * MINUTE, NOW + 150 * MINUTE, ScheduleStatus.ACCEPTED)
        val result = ProactiveEngine.candidates(emptyList(), emptyList(),
            listOf(first, overlap, proposed, distinct), emptyList(), NOW)
        assertEquals(1, result.size)
        assertEquals(ProactiveKind.SCHEDULE_CONFLICT, result.single().kind)
        assertEquals(ProactivePriority.IMPORTANT, result.single().priority)
    }

    @Test fun threeRecentPostponementsOfAnOpenTaskRaiseOneCandidate() {
        val task = task("repeat", null)
        val recent = (1..3).map { ProactivePostponement(task.metadata.id, NOW - it * 60_000L) }
        val result = ProactiveEngine.candidates(listOf(task), emptyList(), emptyList(), recent, NOW)
        assertEquals(ProactiveCandidate(ProactiveKind.REPEATED_POSTPONEMENT,
            "postponed-task:${task.metadata.id}", ProactivePriority.IMPORTANT), result.single())
    }

    @Test fun postponementsAreBoundedByAgeAndOpenTaskState() {
        val task = task("repeat", null)
        val old = listOf(1, 2, 3).map { ProactivePostponement(task.metadata.id,
            NOW - ProactiveEngine.POSTPONEMENT_WINDOW_MILLIS - it) }
        assertTrue(ProactiveEngine.candidates(listOf(task), emptyList(), emptyList(), old, NOW).isEmpty())
        assertTrue(ProactiveEngine.candidates(listOf(task.copy(status = TaskStatus.CANCELLED)), emptyList(),
            emptyList(), (1..3).map { ProactivePostponement(task.metadata.id, NOW - it) }, NOW).isEmpty())
    }

    @Test fun suppressionEnforcesDuplicateMinimumGapAndRollingDailyCap() {
        val policy = ProactiveSuppressionPolicy()
        assertFalse(ProactiveSuppression.allows(NOW, emptyList(), NOW - MINUTE, null, policy))
        assertFalse(ProactiveSuppression.allows(NOW, emptyList(), NOW - 5 * 60 * MINUTE,
            NOW - MINUTE, policy))
        assertFalse(ProactiveSuppression.allows(NOW, listOf(NOW - 1, NOW - 2), null, null, policy))
        assertTrue(ProactiveSuppression.allows(NOW, listOf(NOW - 25 * 60 * MINUTE),
            NOW - 5 * 60 * MINUTE, NOW - 25 * 60 * MINUTE, policy))
    }

    private fun task(label: String, dueAt: Long?, status: TaskStatus = TaskStatus.OPEN) = SavedTask(
        title = label, dueAt = dueAt, dueZoneId = dueAt?.let { "UTC" }, status = status,
        completedAt = if (status == TaskStatus.COMPLETED) NOW else null,
        metadata = RecordMetadata(id = uuid(label)),
    )

    private fun schedule(label: String, start: Long, end: Long, status: ScheduleStatus) = SavedSchedule(
        title = label, startAt = start, endAt = end, zoneId = "UTC", reason = "saved",
        status = status, metadata = RecordMetadata(id = uuid(label)),
    )

    private fun uuid(label: String) = UUID.nameUUIDFromBytes(label.toByteArray()).toString()

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
        val PERSON_ID = UUID.nameUUIDFromBytes("person".toByteArray()).toString()
    }
}
