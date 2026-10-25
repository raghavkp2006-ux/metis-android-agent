package dev.metis.agent

import dev.metis.agent.domain.agent.GraphReadReplyV18
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphReadAnswersV18Test {
    @Test fun projectReadsFollowSavedLinksOrderDeadlinesAndReportCountAndNextDeadline(): Unit = runBlocking {
        val f = GraphFixtureV18()
        val project = SavedProject("RF project")
        val goal = SavedGoal("Saved goal", projectId = project.metadata.id)
        val early = f.clock.instant().minusSeconds(60).toEpochMilli()
        val later = f.clock.instant().plusSeconds(3600).toEpochMilli()
        f.data.projectRows = listOf(project)
        f.data.goalRows = listOf(goal)
        f.data.taskRows = listOf(
            SavedTask("Later task", projectId = project.metadata.id, dueAt = later, dueZoneId = "UTC"),
            SavedTask("No deadline", projectId = project.metadata.id),
            SavedTask("Earlier goal-linked task", goalId = goal.metadata.id, dueAt = early, dueZoneId = "UTC"),
            SavedTask("Completed task", projectId = project.metadata.id, status = TaskStatus.COMPLETED,
                completedAt = early), SavedTask("Unlinked RF project text"),
        )
        val answer = answer(f, "what is pending in RF project")
        assertTrue(answer.contains("Open tasks in RF project — saved sources: 3 tasks."))
        assertTrue(answer.indexOf("Earlier goal-linked task") < answer.indexOf("Later task"))
        assertTrue(answer.indexOf("Later task") < answer.indexOf("No deadline"))
        assertTrue(answer.contains("Next deadline: due 2026-10-25 05:59 Z (overdue)"))
        assertFalse(answer.contains("Completed task"))
        assertFalse(answer.contains("Unlinked"))
        assertEquals(0, f.store.writes)
    }

    @Test fun explicitOpenPromisesHaveSavedDatesOverdueFirstAndNoClosedPromises(): Unit = runBlocking {
        listOf("what did I promise Rahul that is still open", "what promises to Rahul are still open").forEach { text ->
            val f = GraphFixtureV18()
            val person = SavedPerson("Rahul")
            f.data.personRows = listOf(person)
            f.data.promiseRows = listOf(
                SavedPromise(person.metadata.id, "Later promise", "OPEN",
                    dueAt = f.clock.instant().plusSeconds(60).toEpochMilli()),
                SavedPromise(person.metadata.id, "No date promise", "OPEN"),
                SavedPromise(person.metadata.id, "Overdue promise", "OPEN",
                    dueAt = f.clock.instant().minusSeconds(60).toEpochMilli()),
                SavedPromise(person.metadata.id, "Done promise", "FULFILLED"),
                SavedPromise(SavedPerson("Maya").metadata.id, "Other person's promise", "OPEN"),
            )
            val answer = answer(f, text)
            assertTrue(answer.contains("Open promises to Rahul — saved sources: 3 promises."))
            assertTrue(answer.indexOf("Overdue promise") < answer.indexOf("Later promise"))
            assertTrue(answer.indexOf("Later promise") < answer.indexOf("No date promise"))
            assertTrue(answer.contains("(overdue)"))
            assertTrue(answer.contains("no saved deadline"))
            assertFalse(answer.contains("Done promise"))
            assertFalse(answer.contains("Other person's promise"))
        }
    }

    @Test fun pendingPersonIncludesOnlyExplicitSavedLinksAndDeduplicatesTasks(): Unit = runBlocking {
        val f = GraphFixtureV18()
        val person = SavedPerson("Rahul")
        val task = SavedTask("Linked open task")
        val closedEdgeTask = SavedTask("Linked through completed promise")
        f.data.personRows = listOf(person)
        f.data.taskRows = listOf(task, closedEdgeTask, SavedTask("Call Rahul inferred from title"))
        f.data.promiseRows = listOf(
            SavedPromise(person.metadata.id, "Open promise", "OPEN", taskId = task.metadata.id),
            SavedPromise(person.metadata.id, "Completed promise", "FULFILLED", taskId = closedEdgeTask.metadata.id),
        )
        f.data.reminderRows = listOf(graphReminderV18(person, task), graphReminderV18(person, task, "CANCELLED"),
            graphReminderV18(SavedPerson("Maya"), SavedTask("Other task")))
        val answer = answer(f, "what is pending with Rahul")
        assertTrue(answer.contains("2 tasks, 1 promise, 1 reminder"))
        assertEquals(1, Regex("Linked open task").findAll(answer).count())
        assertTrue(answer.contains("Linked through completed promise"))
        assertFalse(answer.contains("Completed promise"))
        assertFalse(answer.contains("inferred from title"))
        assertFalse(answer.contains("Other task"))
    }

    @Test fun everyQueryHandlesEmptyUnknownAmbiguousAndManyResults(): Unit = runBlocking {
        listOf("what is pending in Research", "what is pending with Rahul",
            "what promises to Rahul are still open").forEach { text ->
            val f = GraphFixtureV18()
            val project = SavedProject("Research")
            val person = SavedPerson("Rahul")
            f.data.projectRows = listOf(project)
            f.data.personRows = listOf(person)
            assertTrue(answer(f, text).contains("Nothing is pending."))
            f.data.projectRows = emptyList()
            f.data.personRows = emptyList()
            assertTrue(answer(f, text).contains("No saved"))
            f.data.projectRows = listOf(project, SavedProject("Research"))
            f.data.personRows = listOf(person, SavedPerson("Rahul"))
            assertTrue(f.port.graph(f.plan(text)) is GraphReadReplyV18.Choose)
            f.data.projectRows = listOf(project)
            f.data.personRows = listOf(person)
            f.data.taskRows = (1..8).map { SavedTask("Saved task $it", projectId = project.metadata.id,
                metadata = RecordMetadata(createdAt = it.toLong())) }
            f.data.promiseRows = (1..8).map { SavedPromise(person.metadata.id, "Saved promise $it", "OPEN",
                taskId = f.data.taskRows[it - 1].metadata.id) }
            val many = answer(f, text)
            val total = if (text.contains("pending with")) 16 else 8
            assertEquals(5, many.lines().count { it.startsWith("• ") })
            assertTrue(many.contains("And ${total - 5} more."))
        }
    }

    @Test fun exactNamesTakePrecedenceAndUnknownNamesOfferOnlySavedPrefixes(): Unit = runBlocking {
        val f = GraphFixtureV18()
        f.data.personRows = listOf(SavedPerson("Rahul"), SavedPerson("Rahul Kumar"), SavedPerson("Maya"))
        assertTrue(answer(f, "what is pending with Rahul").contains("Nothing is pending"))
        val prefix = f.port.graph(f.plan("what is pending with Ra")) as GraphReadReplyV18.Choose
        assertTrue(prefix.question.contains("No saved person exactly matches"))
        assertEquals(2, prefix.choices.size)
        assertTrue(prefix.choices.all { it.label.startsWith("Rahul") })
        assertFalse(prefix.choices.any { it.label.contains("Maya") })
        f.data.projectRows = (1..8).map { SavedProject("Research $it") }
        val projects = f.port.graph(f.plan("what is pending in Research")) as GraphReadReplyV18.Choose
        assertEquals(5, projects.choices.size)
        assertTrue(projects.question.contains("And 3 more"))
    }

    @Test fun onlyPendingOrScheduledRemindersAreOpen(): Unit = runBlocking {
        listOf("PENDING", "SCHEDULED", "FIRED", "CANCELLED", "FAILED", "UNSUPPORTED", "DENIED").forEach { state ->
            val f = GraphFixtureV18()
            val person = SavedPerson("Rahul")
            f.data.personRows = listOf(person)
            f.data.reminderRows = listOf(graphReminderV18(person, state = state))
            val result = answer(f, "what is pending with Rahul")
            assertEquals(state, state in setOf("PENDING", "SCHEDULED"), result.contains("• Reminder:"))
        }
    }

    private suspend fun answer(f: GraphFixtureV18, text: String) =
        (f.port.graph(f.plan(text)) as GraphReadReplyV18.Answer).message
}
