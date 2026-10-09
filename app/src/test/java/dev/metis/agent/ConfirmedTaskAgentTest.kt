package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.RevisionTarget
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.agent.UndoResult
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedTaskAgentTest {
    @Test
    fun completionProposalBindsResolvedRevisionAndRequiresCanonicalAcceptance(): Unit = runBlocking {
        val store = FakeAcceptedStore()
        val target = RevisionTarget(EntityReference(MemoryEntityType.TASK, UUID.randomUUID()), 7)
        store.completion = CompletionSelection.Selected("Synthetic study!", target)
        val agent = ConfirmedTaskAgent(FakeReads(), store)
        val proposal = agent.process(request("complete task: Synthetic study!")).proposals.single()
        assertEquals(TaskMutation.Complete(target), (proposal.action as TaskAction).mutation)
        assertTrue(proposal.reason.contains("Synthetic study!"))
        assertEquals(0, store.accepts)
        assertEquals(AgentResultStatus.ANSWER, agent.accept(proposal).status)
        assertEquals(1, store.accepts)
        assertEquals(AgentResultStatus.DENIED, agent.accept(proposal).status)
    }

    @Test
    fun unresolvedNegatedAndAnswerOnlyCompletionCannotExecute(): Unit = runBlocking {
        val store = FakeAcceptedStore()
        val agent = ConfirmedTaskAgent(FakeReads(), store)
        assertEquals(AgentResultStatus.FOLLOW_UP, agent.process(request("complete task: study")).status)
        store.completion = CompletionSelection.Selected("study",
            RevisionTarget(EntityReference(MemoryEntityType.TASK, UUID.randomUUID()), 0))
        assertEquals(AgentResultStatus.DENIED, agent.process(request("don't complete task: study")).status)
        store.authority = AutonomyLevel.ANSWER_ONLY
        assertEquals(AgentResultStatus.DENIED, agent.process(request("complete task: study")).status)
        store.authority = AutonomyLevel.SUGGEST
        store.unresolvedFields = setOf("task")
        assertEquals(AgentResultStatus.FOLLOW_UP, agent.process(request("complete task: study")).status)
        assertEquals(0, store.accepts)
    }

    @Test
    fun suggestionNeverExecutesUntilCanonicalProposalIsAccepted(): Unit = runBlocking {
        val store = FakeAcceptedStore()
        val agent = ConfirmedTaskAgent(FakeReads(), store)
        val result = agent.process(request("add a task to study"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        assertEquals(0, store.accepts)
        val proposal = result.proposals.single()
        val copy = ActionProposal(proposal.id, proposal.action, proposal.risk, proposal.reason,
            proposal.evidence, true, proposal.createdAt, proposal.expiresAt)
        assertEquals(AgentResultStatus.DENIED, agent.accept(copy).status)
        assertEquals(0, store.accepts)
        assertEquals(AgentResultStatus.ANSWER, agent.accept(proposal).status)
        assertEquals(1, store.accepts)
        assertEquals(AgentResultStatus.DENIED, agent.accept(proposal).status)
    }

    @Test
    fun discardAndNewRequestsInvalidatePreviousProposal(): Unit = runBlocking {
        val store = FakeAcceptedStore()
        val agent = ConfirmedTaskAgent(FakeReads(), store)
        val first = agent.process(request("create a task to read")).proposals.single()
        agent.discardProposal()
        assertEquals(AgentResultStatus.DENIED, agent.accept(first).status)
        val second = agent.process(request("add a task to study")).proposals.single()
        agent.process(request("show my tasks"))
        assertEquals(AgentResultStatus.DENIED, agent.accept(second).status)
        assertEquals(0, store.accepts)
    }

    @Test
    fun negationUnknownOverlongTitleAndAnswerOnlyCannotEnableActions(): Unit = runBlocking {
        val store = FakeAcceptedStore()
        val agent = ConfirmedTaskAgent(FakeReads(), store)
        assertEquals(AgentResultStatus.DENIED, agent.process(request("don't add a task to study")).status)
        assertEquals(AgentResultStatus.FOLLOW_UP, agent.process(request("please do something")).status)
        assertEquals(AgentResultStatus.FOLLOW_UP, agent.process(request("add a task to " + "x".repeat(501))).status)
        store.authority = AutonomyLevel.ANSWER_ONLY
        assertEquals(AgentResultStatus.DENIED, agent.process(request("add a task to study")).status)
        assertEquals(0, store.accepts)
    }

    @Test
    fun concurrentAcceptanceConsumesTheProposalOnce(): Unit = runBlocking {
        val store = FakeAcceptedStore()
        store.release = CompletableDeferred()
        val agent = ConfirmedTaskAgent(FakeReads(), store)
        val proposal = agent.process(request("add a task to study")).proposals.single()
        val first = async { agent.accept(proposal) }
        store.started.await()
        val second = agent.accept(proposal)
        assertEquals(AgentResultStatus.DENIED, second.status)
        store.release!!.complete(Unit)
        assertEquals(AgentResultStatus.ANSWER, first.await().status)
        assertEquals(1, store.accepts)
    }

    @Test
    fun cancellationAfterAcceptanceDoesNotAbandonTheCommittedOperation(): Unit = runBlocking {
        val store = FakeAcceptedStore()
        store.release = CompletableDeferred()
        val agent = ConfirmedTaskAgent(FakeReads(), store)
        val proposal = agent.process(request("add a task to study")).proposals.single()
        val accepting = async { agent.accept(proposal) }
        store.started.await()
        accepting.cancel()
        store.release!!.complete(Unit)
        accepting.join()
        assertTrue(store.committed)
        assertEquals(1, store.accepts)
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, Instant.now())
}

private class FakeReads : AgentReadPort {
    override suspend fun tasks() = "Synthetic saved tasks"
    override suspend fun memories(query: String, at: Long) = "Synthetic saved memory"
    override suspend fun promises(person: String) = SpecialistResult.Answer("Synthetic saved promises")
}

private class FakeAcceptedStore : AcceptedTaskStore {
    var completion: CompletionSelection = CompletionSelection.Unavailable
    override suspend fun resolveCompletion(title: String) = completion
    var accepts = 0
    var authority = AutonomyLevel.SUGGEST
    var unresolvedFields = emptySet<String>()
    var release: CompletableDeferred<Unit>? = null
    val started = CompletableDeferred<Unit>()
    var committed = false
    override suspend fun context(request: AgentRequest): ResolvedContext {
        val now = Instant.now()
        return ResolvedContext(now, ZoneId.of("UTC"), authority,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            unresolvedFields = unresolvedFields,
            screenContext = request.screenContext, conversationId = request.conversationId)
    }
    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
        accepts++
        started.complete(Unit)
        release?.await()
        committed = true
        val now = Instant.now()
        return ActionReceipt(UUID.randomUUID(), proposal.action.identity, ActionType.TASK,
            ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
            "Synthetic task saved")
    }
    override suspend fun retry(actionId: UUID): ActionReceipt = error("Not used")
    override suspend fun cancelPending(actionId: UUID) = Unit
    override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
    override fun observeHistory() = flowOf(emptyList<TaskActionHistoryEntry>())
}
