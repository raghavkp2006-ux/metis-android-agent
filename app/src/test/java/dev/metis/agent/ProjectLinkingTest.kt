package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ClarificationRegistry
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ProjectExtractor
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedTask
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectLinkingTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneId.of("UTC"))
    private val zone = ZoneId.of("UTC")
    private val project1Id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val project2Id = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun projectExtractorVariants() {
        val r1 = ProjectExtractor.extract("add a task to write docs for the Apollo project")
        assertEquals("add a task to write docs", r1.cleanText)
        assertEquals("Apollo", r1.projectName)

        val r2 = ProjectExtractor.extract("prepare release in the Project X project")
        assertEquals("prepare release", r2.cleanText)
        assertEquals("Project X", r2.projectName)

        val r3 = ProjectExtractor.extract("finish slides in the Project X")
        assertEquals("finish slides", r3.cleanText)
        assertEquals("Project X", r3.projectName)

        val r4 = ProjectExtractor.extract("review report for Project Beta")
        assertEquals("review report", r4.cleanText)
        assertEquals("Project Beta", r4.projectName)
    }

    @Test
    fun taskCreation_withExactProject_linksToSavedProject(): Unit = runBlocking {
        val store = FakeTaskStore()
        store.projects.add(
            SavedProject(title = "Project X", metadata = RecordMetadata(id = project1Id.toString(), createdAt = 0L))
        )

        val agent = ConfirmedTaskAgent(FakeReadPort(), store, zone = { zone }, clock = clock)
        val result = agent.process(request("add a task to write unit tests for the Project X project"))

        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        val action = proposal.action as TaskAction
        val mutation = action.mutation as TaskMutation.Create

        assertEquals("write unit tests", mutation.title)
        assertEquals(project1Id, mutation.projectId)
        assertEquals("Project X", mutation.projectName)
        assertTrue(proposal.reason.contains("Project X"))
    }

    @Test
    fun taskCreation_withAmbiguousProject_promptsClarificationAndResolves(): Unit = runBlocking {
        val registry = ClarificationRegistry(clock)
        val store = FakeTaskStore()
        store.projects.add(
            SavedProject(title = "Apollo 11", metadata = RecordMetadata(id = project1Id.toString(), createdAt = 0L))
        )
        store.projects.add(
            SavedProject(title = "Apollo 13", metadata = RecordMetadata(id = project2Id.toString(), createdAt = 0L))
        )

        val agent = ConfirmedTaskAgent(FakeReadPort(), store, zone = { zone }, registry = registry, clock = clock)
        val result = agent.process(request("add a task to review specs for the Apollo project"))

        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        val followUp = requireNotNull(result.followUp)
        assertTrue(followUp.missingFields.contains("project"))
        assertTrue(result.message.contains("Apollo 11") && result.message.contains("Apollo 13"))

        // Answer clarification with "Apollo 11"
        val answerResult = agent.process(request("Apollo 11"))
        assertEquals(AgentResultStatus.PROPOSAL, answerResult.status)
        val proposal = answerResult.proposals.single()
        val mutation = (proposal.action as TaskAction).mutation as TaskMutation.Create

        assertEquals("review specs", mutation.title)
        assertEquals(project1Id, mutation.projectId)
        assertEquals("Apollo 11", mutation.projectName)

        // Ambiguous project: verify reply "1" resolves to first choice (Apollo 11)
        val r1 = agent.process(request("add a task to test motors for the Apollo project"))
        assertEquals(AgentResultStatus.FOLLOW_UP, r1.status)
        val a1 = agent.process(request("1"))
        assertEquals(AgentResultStatus.PROPOSAL, a1.status)
        val m1 = (a1.proposals.single().action as TaskAction).mutation as TaskMutation.Create
        assertEquals("Apollo 11", m1.projectName)

        // Ambiguous project: verify reply "second" resolves to second choice (Apollo 13)
        val r2 = agent.process(request("add a task to test thrusters for the Apollo project"))
        assertEquals(AgentResultStatus.FOLLOW_UP, r2.status)
        val a2 = agent.process(request("second"))
        assertEquals(AgentResultStatus.PROPOSAL, a2.status)
        val m2 = (a2.proposals.single().action as TaskAction).mutation as TaskMutation.Create
        assertEquals("Apollo 13", m2.projectName)
    }

    @Test
    fun taskCreation_withFreeTextProject_notPersistedBeforeAccept(): Unit = runBlocking {
        val store = FakeTaskStore()
        val agent = ConfirmedTaskAgent(FakeReadPort(), store, zone = { zone }, clock = clock)

        val result = agent.process(request("add a task to prepare launch for the Secret Beta project"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)

        val proposal = result.proposals.single()
        val mutation = (proposal.action as TaskAction).mutation as TaskMutation.Create
        assertEquals("prepare launch", mutation.title)
        assertNull(mutation.projectId)
        assertEquals("Secret Beta", mutation.projectName)

        // Verify project is NOT created before accept
        assertEquals(0, store.projects.size)
        assertTrue("No project should be persisted before accept", store.projects.isEmpty())

        // Now accept
        val receipt = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, receipt.status)
        assertEquals(1, store.acceptCount)
        assertEquals(1, store.savedTasks.size)
        val savedTask = store.savedTasks.single()
        assertEquals("prepare launch", savedTask.title)
        assertNotNull("Project should be created upon accept", savedTask.projectId)
        assertEquals(1, store.projects.size)
        val savedProject = store.projects.firstOrNull { it.metadata.id == savedTask.projectId }
        assertNotNull(savedProject)
        assertEquals("Secret Beta", savedProject!!.title)
    }

    @Test
    fun promiseCreation_withExactProject_linksToSavedProject(): Unit = runBlocking {
        val store = FakePromiseStore()
        store.projects.add(
            SavedProject(title = "Project X", metadata = RecordMetadata(id = project1Id.toString(), createdAt = 0L))
        )
        val contactA = PromisePersonMatch(UUID.randomUUID(), "Contact A")
        store.people.add(contactA)

        val readDelegate = object : ConfirmableAgent {
            override suspend fun process(request: AgentRequest) =
                AgentResult(request.id, AgentResultStatus.ANSWER, "read")
            override suspend fun accept(proposal: ActionProposal) =
                AgentResult(proposal.requestId, AgentResultStatus.DENIED, "read")
            override fun discardProposal() = Unit
            override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
        }

        val agent = ConfirmedPromiseAgent(readDelegate, store, { zone }, clock)
        val result = agent.process(request("I promised Contact A the report for the Project X project by Friday"))

        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        val action = proposal.action as CreatePromiseAction

        assertEquals(project1Id, action.projectId)
        assertEquals("Project X", action.projectName)
        assertEquals("Accept to save this promise locally.", proposal.reason)
    }

    @Test
    fun promiseCreation_withAmbiguousProject_promptsClarificationAndResolves(): Unit = runBlocking {
        val store = FakePromiseStore()
        store.projects.add(
            SavedProject(title = "Apollo 11", metadata = RecordMetadata(id = project1Id.toString(), createdAt = 0L))
        )
        store.projects.add(
            SavedProject(title = "Apollo 13", metadata = RecordMetadata(id = project2Id.toString(), createdAt = 0L))
        )
        val contactA = PromisePersonMatch(UUID.randomUUID(), "Contact A")
        store.people.add(contactA)

        val readDelegate = object : ConfirmableAgent {
            override suspend fun process(request: AgentRequest) =
                AgentResult(request.id, AgentResultStatus.ANSWER, "read")
            override suspend fun accept(proposal: ActionProposal) =
                AgentResult(proposal.requestId, AgentResultStatus.DENIED, "read")
            override fun discardProposal() = Unit
            override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
        }

        val agent = ConfirmedPromiseAgent(readDelegate, store, { zone }, clock)
        val result = agent.process(request("I promised Contact A the report for the Apollo project by Friday"))

        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        val followUp = requireNotNull(result.followUp)
        assertTrue(followUp.missingFields.contains("project"))

        val answerResult = agent.answerPromise(result.requestId, followUp.id, request("Apollo 13"))
        assertEquals(AgentResultStatus.PROPOSAL, answerResult.status)
        val proposal = answerResult.proposals.single()
        val action = proposal.action as CreatePromiseAction

        assertEquals(project2Id, action.projectId)
        assertEquals("Apollo 13", action.projectName)
    }

    @Test
    fun promiseCreation_withFreeTextProject_notPersistedBeforeAccept(): Unit = runBlocking {
        val store = FakePromiseStore()
        val contactA = PromisePersonMatch(UUID.randomUUID(), "Contact A")
        store.people.add(contactA)

        val readDelegate = object : ConfirmableAgent {
            override suspend fun process(request: AgentRequest) =
                AgentResult(request.id, AgentResultStatus.ANSWER, "read")
            override suspend fun accept(proposal: ActionProposal) =
                AgentResult(proposal.requestId, AgentResultStatus.DENIED, "read")
            override fun discardProposal() = Unit
            override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
        }

        val agent = ConfirmedPromiseAgent(readDelegate, store, { zone }, clock)
        val result = agent.process(request("I promised Contact A the draft for the Secret Alpha project by Friday"))

        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        val action = proposal.action as CreatePromiseAction
        assertNull(action.projectId)
        assertEquals("Secret Alpha", action.projectName)

        // Verify project is NOT created before accept
        assertTrue("No project should be persisted before accept", store.projects.isEmpty())

        // Accept: standalone promise persists promise only; no project or task created
        val receipt = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, receipt.status)
        assertEquals(1, store.acceptCount)
        assertEquals(0, store.projects.size)
        assertEquals(1, store.savedPromises.size)
    }

    private val session = UUID.randomUUID()
    private fun request(text: String) =
        AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, clock.instant(), conversationId = session)
}

private class FakeReadPort : AgentReadPort {
    override suspend fun tasks() = "Tasks"
    override suspend fun memories(query: String, at: Long) = "Memories"
    override suspend fun promises(person: String) = SpecialistResult.Answer("Promises")
}

private class FakeTaskStore : AcceptedTaskStore {
    val projects = mutableListOf<SavedProject>()
    val savedTasks = mutableListOf<SavedTask>()
    var acceptCount = 0

    override suspend fun savedProjects() = projects.toList()

    override suspend fun context(request: AgentRequest): ResolvedContext {
        val now = request.timestamp
        return ResolvedContext(
            now = now,
            zoneId = ZoneId.of("UTC"),
            autonomy = AutonomyLevel.SUGGEST,
            capabilities = CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )
    }

    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
        acceptCount++
        val action = proposal.action as TaskAction
        val mutation = action.mutation as TaskMutation.Create
        val effectiveProjectId = if (mutation.projectId != null) {
            mutation.projectId.toString()
        } else if (mutation.projectName != null) {
            val newId = UUID.randomUUID().toString()
            projects.add(
                SavedProject(title = mutation.projectName, metadata = RecordMetadata(id = newId, createdAt = 0L))
            )
            newId
        } else null

        val task = SavedTask(
            title = mutation.title, projectId = effectiveProjectId,
            metadata = RecordMetadata(id = UUID.randomUUID().toString(), createdAt = 0L)
        )
        savedTasks.add(task)

        val now = Instant.now()
        return ActionReceipt(
            id = UUID.randomUUID(),
            identity = action.identity,
            actionType = ActionType.TASK,
            outcome = ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
            reason = "Task saved"
        )
    }

    override suspend fun retry(actionId: UUID): ActionReceipt = error("Not used")
    override suspend fun cancelPending(actionId: UUID) = Unit
    override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
    override fun observeHistory() = flowOf(emptyList<TaskActionHistoryEntry>())
}

private class FakePromiseStore : AcceptedPromiseStore {
    val projects = mutableListOf<SavedProject>()
    val people = mutableListOf<PromisePersonMatch>()
    val savedPromises = mutableListOf<SavedPromise>()
    var acceptCount = 0

    override suspend fun savedProjects() = projects.toList()
    override suspend fun matchingPeople(name: String) = people.filter { it.name.contains(name, ignoreCase = true) }

    override suspend fun context(request: AgentRequest): ResolvedContext {
        val now = request.timestamp
        return ResolvedContext(
            now = now,
            zoneId = ZoneId.of("UTC"),
            autonomy = AutonomyLevel.SUGGEST,
            capabilities = CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )
    }

    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
        acceptCount++
        val action = proposal.action as CreatePromiseAction
        val now = Instant.now()
        val pId = people.firstOrNull { it.name.equals(action.personName, ignoreCase = true) }?.id?.toString()
            ?: UUID.randomUUID().toString()
        val promise = SavedPromise(
            personId = pId, content = action.description, status = "OPEN",
            dueAt = action.dueAt?.toEpochMilli(),
            metadata = RecordMetadata(id = UUID.randomUUID().toString(), createdAt = 0L)
        )
        savedPromises.add(promise)
        return ActionReceipt(
            id = UUID.randomUUID(),
            identity = action.identity,
            actionType = ActionType.CREATE_PROMISE,
            outcome = ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
            reason = "Promise saved"
        )
    }
}
