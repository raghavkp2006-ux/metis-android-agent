package dev.metis.agent

import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.BaselineContextBuilder
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.LocalAgentOrchestrator
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectQueriesTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val rules = EnglishRules(readCandidate = ReadCandidate.V16)

    private fun orchestrator(data: GraphDataV18): LocalAgentOrchestrator {
        val reads = LocalAgentReads(savedData = data)
        val specialist = LanguageSpecialist(reads, localCalendar = true, readCandidate = ReadCandidate.V16)
        val contexts = BaselineContextBuilder(clock) { zone }
        return LocalAgentOrchestrator(
            classifier = rules,
            extractor = rules,
            contexts = contexts,
            specialist = specialist,
            policy = ProposalPolicy(),
            localCalendar = true,
            readCandidate = ReadCandidate.V16
        )
    }

    private fun request(text: String) = AgentRequest(
        id = UUID.randomUUID(),
        text = text,
        source = InputSource.TEXT,
        timestamp = now,
        conversationId = UUID.randomUUID()
    )

    @Test
    fun projectWithTasksAndPromises_countsDeadlinesAndOrdersItems(): Unit = runBlocking {
        val data = GraphDataV18()
        val projId = UUID.randomUUID().toString()
        val personId = UUID.randomUUID().toString()
        val project = SavedProject("Project X", metadata = RecordMetadata(id = projId, createdAt = 0L))
        val contactA = SavedPerson("Contact A", metadata = RecordMetadata(id = personId, createdAt = 0L))
        val earlyDue = now.minusSeconds(3600).toEpochMilli() // overdue
        val laterDue = now.plusSeconds(7200).toEpochMilli() // future

        val task1 = SavedTask(
            "Overdue task", projectId = project.metadata.id, dueAt = earlyDue, dueZoneId = "UTC",
            status = TaskStatus.OPEN
        )
        val task2 = SavedTask(
            "Task with promise", projectId = project.metadata.id, dueAt = laterDue, dueZoneId = "UTC",
            status = TaskStatus.OPEN
        )
        val promise = SavedPromise(
            personId = contactA.metadata.id, content = "Send update", status = "OPEN",
            taskId = task2.metadata.id, dueAt = laterDue
        )

        data.projectRows = listOf(project)
        data.personRows = listOf(contactA)
        data.taskRows = listOf(task1, task2)
        data.promiseRows = listOf(promise)

        val pipeline = orchestrator(data)
        val result = pipeline.process(request("What is pending in the Project X?"))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains(
            "Project 'Project X' status: 1 open task(s), 1 open promise(s). Overdue: 1."
        ))
        assertTrue(result.message.contains("Task: Overdue task (overdue since"))
        assertTrue(result.message.contains("Promise to Contact A: Send update (due"))
        // Overdue task must be listed before promise with later deadline
        assertTrue(result.message.indexOf("Overdue task") < result.message.indexOf("Promise to Contact A"))
    }

    @Test
    fun overdueOrdering_placesPastDeadlinesFirstAndNoDeadlinesLast(): Unit = runBlocking {
        val data = GraphDataV18()
        val pId = UUID.randomUUID().toString()
        val project = SavedProject("Project X", metadata = RecordMetadata(id = pId, createdAt = 0L))
        val overdueDue = now.minusSeconds(7200).toEpochMilli()
        val futureDue = now.plusSeconds(3600).toEpochMilli()

        val taskOverdue = SavedTask(
            "Overdue item", projectId = project.metadata.id, dueAt = overdueDue, dueZoneId = "UTC",
            status = TaskStatus.OPEN
        )
        val taskFuture = SavedTask(
            "Future item", projectId = project.metadata.id, dueAt = futureDue, dueZoneId = "UTC",
            status = TaskStatus.OPEN
        )
        val taskNoDeadline = SavedTask("No deadline item", projectId = project.metadata.id, status = TaskStatus.OPEN)

        data.projectRows = listOf(project)
        data.taskRows = listOf(taskFuture, taskNoDeadline, taskOverdue)

        val pipeline = orchestrator(data)
        val result = pipeline.process(request("what is pending in the Project X"))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        val overdueIdx = result.message.indexOf("Overdue item")
        val futureIdx = result.message.indexOf("Future item")
        val noDeadlineIdx = result.message.indexOf("No deadline item")

        assertTrue("Overdue before future", overdueIdx < futureIdx)
        assertTrue("Future before no deadline", futureIdx < noDeadlineIdx)
    }

    @Test
    fun emptyProject_reportsNothingPending(): Unit = runBlocking {
        val data = GraphDataV18()
        val pId = UUID.randomUUID().toString()
        val project = SavedProject("Project X", metadata = RecordMetadata(id = pId, createdAt = 0L))
        data.projectRows = listOf(project)

        val pipeline = orchestrator(data)
        val result = pipeline.process(request("what is pending in the Project X"))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertEquals("Project 'Project X': nothing pending.", result.message)
    }

    @Test
    fun unknownProject_offersToCreateOne(): Unit = runBlocking {
        val data = GraphDataV18()
        val pipeline = orchestrator(data)
        val result = pipeline.process(request("what is pending in project Unknown"))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Unknown project 'project Unknown'. Would you like to create one?"))
    }

    @Test
    fun ambiguousProject_promptsClarification(): Unit = runBlocking {
        val data = GraphDataV18()
        val p1 = SavedProject("Alpha One", metadata = RecordMetadata(id = UUID.randomUUID().toString(), createdAt = 0L))
        val p2 = SavedProject("Alpha Two", metadata = RecordMetadata(id = UUID.randomUUID().toString(), createdAt = 0L))
        data.projectRows = listOf(p1, p2)

        val pipeline = orchestrator(data)
        val result = pipeline.process(request("what is pending in project Alpha"))

        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertTrue(result.message.contains("Multiple projects match"))
    }

    @Test
    fun personCommitmentsAcrossProjects_includesLinkedTaskAndProject(): Unit = runBlocking {
        val data = GraphDataV18()
        val pId = UUID.randomUUID().toString()
        val proj1Id = UUID.randomUUID().toString()
        val proj2Id = UUID.randomUUID().toString()
        val contactA = SavedPerson("Contact A", metadata = RecordMetadata(id = pId, createdAt = 0L))
        val proj1 = SavedProject("Project X", metadata = RecordMetadata(id = proj1Id, createdAt = 0L))
        val proj2 = SavedProject("Project Y", metadata = RecordMetadata(id = proj2Id, createdAt = 0L))

        val task1 = SavedTask("Deliver spec", projectId = proj1.metadata.id, status = TaskStatus.OPEN)
        val task2 = SavedTask("Deploy build", projectId = proj2.metadata.id, status = TaskStatus.OPEN)

        val promise1 = SavedPromise(
            personId = contactA.metadata.id, content = "Share spec doc", status = "OPEN",
            taskId = task1.metadata.id
        )
        val promise2 = SavedPromise(
            personId = contactA.metadata.id, content = "Deploy by Friday", status = "OPEN",
            taskId = task2.metadata.id
        )
        val promise3 = SavedPromise(personId = contactA.metadata.id, content = "Call on weekend", status = "OPEN")

        data.personRows = listOf(contactA)
        data.projectRows = listOf(proj1, proj2)
        data.taskRows = listOf(task1, task2)
        data.promiseRows = listOf(promise1, promise2, promise3)

        val pipeline = orchestrator(data)
        val result = pipeline.process(request("What did I promise Contact A?"))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Share spec doc"))
        assertTrue(result.message.contains("linked task: Deliver spec; project: Project X"))
        assertTrue(result.message.contains("Deploy by Friday"))
        assertTrue(result.message.contains("linked task: Deploy build; project: Project Y"))
        assertTrue(result.message.contains("Call on weekend"))
        val callPart = result.message.substringAfter("Call on weekend").substringBefore("\n")
        assertFalse(callPart.contains("linked task"))
        assertFalse(callPart.contains("project:"))
    }

    @Test
    fun endToEndThroughLocalAgentOrchestrator_withRealPhrases(): Unit = runBlocking {
        val data = GraphDataV18()
        val projId = UUID.randomUUID().toString()
        val personId = UUID.randomUUID().toString()
        val project = SavedProject("Project X", metadata = RecordMetadata(id = projId, createdAt = 0L))
        val contact = SavedPerson("Contact A", metadata = RecordMetadata(id = personId, createdAt = 0L))
        val task = SavedTask("Draft RFC", projectId = project.metadata.id, status = TaskStatus.OPEN)
        val promise = SavedPromise(
            personId = contact.metadata.id, content = "RFC review", status = "OPEN",
            taskId = task.metadata.id
        )

        data.projectRows = listOf(project)
        data.personRows = listOf(contact)
        data.taskRows = listOf(task)
        data.promiseRows = listOf(promise)

        val pipeline = orchestrator(data)

        // Real phrase 1
        val r1 = pipeline.process(request("What is pending in the Project X?"))
        assertEquals(AgentResultStatus.ANSWER, r1.status)
        assertTrue(r1.message.contains("Project 'Project X' status:"))
        assertTrue(r1.message.contains("Promise to Contact A: RFC review"))

        // Real phrase 2
        val r2 = pipeline.process(request("What did I promise Contact A?"))
        assertEquals(AgentResultStatus.ANSWER, r2.status)
        assertTrue(r2.message.contains("RFC review"))
        assertTrue(r2.message.contains("project: Project X"))
    }
}
