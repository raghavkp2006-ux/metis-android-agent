package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.decision.DecisionContext
import dev.metis.agent.domain.decision.DecisionEngine
import dev.metis.agent.domain.decision.DecisionInputs
import dev.metis.agent.domain.storage.GoalStatus
import dev.metis.agent.domain.storage.ProjectStatus
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import dev.metis.agent.domain.storage.TaskStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {
    private val now = Instant.parse("2026-10-09T10:00:00Z").toEpochMilli()

    @Test fun overdueUrgencyBeatsUnscheduledHighPriorityAndRetainsExactComponents() {
        val overdue = task("Overdue", due = now - 1)
        val high = task("High", priority = 3)
        val result = rank(listOf(high, overdue)).first()
        assertEquals(overdue, result.task)
        assertEquals(0.35, result.components.deadline, EPSILON)
        assertEquals(0.10, result.components.overdue, EPSILON)
        assertEquals(0.05, result.components.effortPenalty, EPSILON)
        assertEquals(0.40, result.components.total, EPSILON)
    }

    @Test fun deadlineBoundariesAndPriorityAreDeterministic() {
        val dueNow = task("Due now", now)
        val tomorrow = task("Tomorrow", now + 86_400_000)
        val soon = task("Soon", now + 3_600_000)
        val results = rank(listOf(tomorrow, soon, dueNow))
        assertEquals(listOf(dueNow, soon, tomorrow), results.map { it.task })
        assertEquals(0.0, results.first().components.overdue, EPSILON)
        assertEquals(0.9 * 0.35, results[1].components.deadline, EPSILON)
        assertEquals(0.6 * 0.35, results[2].components.deadline, EPSILON)
        assertEquals(0.30, rank(listOf(task("Priority", priority = 3))).single().components.priority, EPSILON)
    }

    @Test fun explicitTimeBudgetExcludesUnknownAndTooLongEstimates() {
        val fits = task("Fits").copy(estimatedSeconds = 1_800)
        val long = task("Too long", now - 1, 3).copy(estimatedSeconds = 1_801)
        val unknown = task("Unknown", now - 1, 3)
        assertEquals(listOf(fits), rank(listOf(unknown, long, fits), 1_800).map { it.task })
        assertTrue(rank(listOf(unknown, long), 1_800).isEmpty())
        assertEquals(3, rank(listOf(unknown, long, fits)).size)
    }

    @Test fun unfinishedMissingAndCancelledPrerequisitesBlockRecommendation() {
        val prerequisite = task("Prerequisite")
        val dependent = task("Blocked", now - 1, 3)
        val edge = SavedTaskDependency(dependent.metadata.id, prerequisite.metadata.id)
        fun inputs(tasks: List<SavedTask>) = DecisionInputs(tasks, listOf(edge))
        assertEquals(listOf(prerequisite), DecisionEngine.rank(inputs(listOf(dependent, prerequisite)),
            DecisionContext(now)).map { it.task })
        assertTrue(DecisionEngine.rank(inputs(listOf(dependent)), DecisionContext(now)).isEmpty())
        assertTrue(DecisionEngine.rank(inputs(listOf(dependent, prerequisite.copy(status = TaskStatus.CANCELLED))),
            DecisionContext(now)).isEmpty())
        val completed = prerequisite.copy(status = TaskStatus.COMPLETED, completedAt = now - 100)
        assertEquals(dependent, DecisionEngine.rank(inputs(listOf(dependent, completed)),
            DecisionContext(now)).single().task)
    }

    @Test fun completedCancelledAndRecurringTasksAreNotCandidates() {
        val open = task("Open")
        val completed = task("Completed").copy(status = TaskStatus.COMPLETED, completedAt = now)
        val cancelled = task("Cancelled").copy(status = TaskStatus.CANCELLED)
        val recurring = task("Recurring").copy(recurrenceRule = "FREQ=DAILY", recurrenceZoneId = "UTC")
        assertEquals(listOf(open), rank(listOf(completed, cancelled, recurring, open)).map { it.task })
    }

    @Test fun goalRelevanceUsesOnlyActiveGoalsAndActiveProjects() {
        val project = SavedProject("Synthetic project")
        val goal = SavedGoal("Synthetic goal", projectId = project.metadata.id, priority = 3)
        val linked = task("Goal task").copy(goalId = goal.metadata.id)
        val unlinked = task("Unlinked")
        val inputs = DecisionInputs(listOf(unlinked, linked), emptyList(), listOf(goal), listOf(project))
        val chosen = DecisionEngine.rank(inputs, DecisionContext(now)).first()
        assertEquals(linked, chosen.task)
        assertEquals(0.15, chosen.components.goal, EPSILON)
        assertEquals(listOf(unlinked), DecisionEngine.rank(inputs.copy(goals = listOf(goal.copy(
            status = GoalStatus.CANCELLED))), DecisionContext(now)).map { it.task })
        assertEquals(listOf(unlinked), DecisionEngine.rank(inputs.copy(projects = listOf(project.copy(
            status = ProjectStatus.ARCHIVED))), DecisionContext(now)).map { it.task })
    }

    @Test fun explicitFocusPreferenceChangesScoreWithoutBecomingFreeTime() {
        val short = task("Short").copy(estimatedSeconds = 1_800)
        val long = task("Long").copy(estimatedSeconds = 1_801)
        val inputs = DecisionInputs(listOf(long, short), emptyList())
        val ranked = DecisionEngine.rank(inputs, DecisionContext(now, focusMinutes = 30))
        assertEquals(2, ranked.size)
        assertEquals(short, ranked.first().task)
        assertEquals(0.10, ranked.first().components.preference, EPSILON)
        assertEquals(0.0, ranked.last().components.preference, EPSILON)
    }

    @Test fun tiesAreStableAcrossInputOrderAndNoInputsProduceNoDecision() {
        val a = task("A").copy(metadata = RecordMetadata("00000000-0000-0000-0000-000000000001", now))
        val b = task("B").copy(metadata = RecordMetadata("00000000-0000-0000-0000-000000000002", now))
        assertEquals(listOf(a, b), rank(listOf(b, a)).map { it.task })
        assertEquals(rank(listOf(a, b)), rank(listOf(b, a)))
        assertTrue(rank(emptyList()).isEmpty())
    }

    @Test fun scoresAreBoundedAndInvalidBudgetFailsBeforeRanking() {
        val huge = task("Huge").copy(estimatedSeconds = Long.MAX_VALUE)
        val result = rank(listOf(huge)).single()
        assertEquals(0.10, result.components.effortPenalty, EPSILON)
        assertEquals(0.0, result.components.total, EPSILON)
        assertThrows(IllegalArgumentException::class.java) { DecisionContext(now, 0) }
        assertThrows(IllegalArgumentException::class.java) { DecisionContext(now, 86_401) }
    }

    @Test fun boundedGrammarPreservesDurationOffsetsAndRejectsNegationAndUnknownForms() {
        val rules = EnglishRules()
        fun parse(text: String) = rules.parse(AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
            Instant.ofEpochMilli(now)))
        assertEquals(AgentIntent.WHAT_SHOULD_I_DO, parse("What should I do?").intent)
        assertEquals(AgentIntent.WHAT_SHOULD_I_DO, parse("Please what should I do next!").intent)
        val text = "What should I do with 30 minutes?"
        val duration = parse(text).entities.single()
        assertEquals("30", text.substring(duration.startOffset, duration.endOffset))
        assertEquals(NormalizedValue.Duration(1_800), duration.normalizedValue)
        assertNull(parse("what should I do with 0 minutes?").entities.single().normalizedValue)
        assertNull(parse("what should I do with 1441 minutes?").entities.single().normalizedValue)
        assertTrue(parse("don't tell me what should I do").negated)
        assertEquals(AgentIntent.UNKNOWN, parse("choose something and complete it").intent)
    }

    private fun task(title: String, due: Long? = null, priority: Int = 0) = SavedTask(title,
        metadata = RecordMetadata(createdAt = now), dueAt = due, dueZoneId = due?.let { "UTC" }, priority = priority)
    private fun rank(tasks: List<SavedTask>, available: Long? = null) =
        DecisionEngine.rank(DecisionInputs(tasks, emptyList()), DecisionContext(now, available))

    private companion object { const val EPSILON = 0.000001 }
}
