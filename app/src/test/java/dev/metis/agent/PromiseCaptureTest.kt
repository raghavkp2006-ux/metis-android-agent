package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRequirements
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.PromiseResolution
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.UndoResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromiseCaptureTest {
    private val now = Instant.parse("2026-10-23T10:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val session = UUID.randomUUID()

    @Test fun grammarExtractsOnlyFirstPersonCommitmentsAndLeavesReadQueriesAlone() {
        val cases = listOf(
            "I promised Rahul the slides by Friday" to Triple("Rahul", "the slides", "2026-10-23"),
            "I told Rahul I'd send slides by tomorrow" to Triple("Rahul", "send slides", "2026-10-24"),
            "I owe Rahul the slides" to Triple("Rahul", "the slides", null),
            "I owe Rahul Sharma slides" to Triple("Rahul Sharma", "slides", null),
            "I promised Rahul the slides" to Triple("Rahul", "the slides", null),
        )
        cases.forEach { (utterance, expected) ->
            val request = request(utterance)
            assertEquals(utterance, AgentIntent.CREATE_PROMISE, EnglishRules { zone }.parse(request).intent)
            val parsed = requireNotNull(PromiseResolution.parse(request, zone))
            assertEquals(expected.first, parsed.person)
            assertEquals(expected.second, parsed.description)
            assertEquals(expected.third, parsed.dueDate?.toString())
        }
        listOf("what did I promise Rahul?", "What did I promise Rahul", "what did i promise rahul!").forEach {
            assertEquals(it, AgentIntent.WHAT_DID_I_PROMISE, EnglishRules { zone }.parse(request(it)).intent)
        }
    }

    @Test fun noProposalOrWriteWhileSlotsMissingAndReceiptOnlyAfterAccept(): Unit = runBlocking {
        val store = FakeStore()
        val agent = agent(store)
        listOf("I promised", "I promised the slides", "I promised Rahul").forEach { text ->
            val result = agent.process(request(text))
            assertEquals(text, AgentResultStatus.FOLLOW_UP, result.status)
            assertTrue(result.proposals.isEmpty())
            assertEquals(0, store.writes)
        }
        var result = agent.process(request("I promised the slides"))
        assertEquals("Who did you promise?", result.message)
        result = agent.answerPromise(result.requestId, requireNotNull(result.followUp).id, request("Rahul"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        assertEquals(0, store.writes)
        assertTrue(result.completedActions.isEmpty())
        val proposal = result.proposals.single()
        val action = proposal.action as CreatePromiseAction
        assertEquals("Rahul", action.personName)
        assertEquals("the slides", action.description)
        assertEquals(null, action.dueDate)
        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, store.writes)
        assertEquals(ActionType.CREATE_PROMISE, accepted.completedActions.single().actionType)
    }

    @Test fun followUpsRetainDueDateAndTypedPerson(): Unit = runBlocking {
        val store = FakeStore()
        val agent = agent(store)
        var result = agent.process(request("I promised the slides by Friday"))
        result = agent.answerPromise(result.requestId, requireNotNull(result.followUp).id, request("Rahul"))
        val personAnswer = result.proposals.single().action as CreatePromiseAction
        assertEquals("Rahul", personAnswer.personName)
        assertEquals("the slides", personAnswer.description)
        assertEquals("2026-10-23", personAnswer.dueDate.toString())
        result = agent.process(request("I promised Rahul by tomorrow"))
        result = agent.answerPromise(result.requestId, requireNotNull(result.followUp).id,
            request("send the slides"))
        val descriptionAnswer = result.proposals.single().action as CreatePromiseAction
        assertEquals("Rahul", descriptionAnswer.personName)
        assertEquals("send the slides", descriptionAnswer.description)
        assertEquals("2026-10-24", descriptionAnswer.dueDate.toString())
        assertEquals(0, store.writes)
    }

    @Test fun missingDescriptionAmbiguityInvalidAnswerCancelAndExpiryAreFailClosed(): Unit = runBlocking {
        val store = FakeStore()
        val agent = agent(store)
        var result = agent.process(request("I owe Rahul"))
        assertEquals("What did you promise?", result.message)
        result = agent.answerPromise(result.requestId, requireNotNull(result.followUp).id, request("the slides"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        assertEquals(0, store.writes)
        store.people = listOf(PromisePersonMatch(UUID.randomUUID(), "Rahul"),
            PromisePersonMatch(UUID.randomUUID(), "RAHUL"))
        result = agent.process(request("I owe Rahul the slides"))
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertTrue(result.message.startsWith("Which person?"))
        assertEquals(0, store.writes)
        val first = agent.answerPromise(result.requestId, requireNotNull(result.followUp).id,
            request("create a task to phone"))
        assertEquals(AgentResultStatus.FOLLOW_UP, first.status)
        val second = agent.answerPromise(result.requestId, requireNotNull(first.followUp).id,
            request("create a task to phone"))
        assertEquals(AgentResultStatus.DENIED, second.status)
        assertEquals(0, store.writes)
        result = agent.process(request("I owe Rahul the slides"))
        val selected = agent.answerPromise(result.requestId, requireNotNull(result.followUp).id, request("1"))
        assertEquals(AgentResultStatus.PROPOSAL, selected.status)
        assertEquals(store.people.first().id,
            (selected.proposals.single().action as CreatePromiseAction).matchedPersonId)
        assertEquals(0, store.writes)
        result = agent.process(request("I owe Rahul the slides"))
        agent.cancelPromiseClarification()
        assertEquals(AgentResultStatus.DENIED, agent.answerPromise(result.requestId,
            requireNotNull(result.followUp).id, request("1")).status)
        listOf("ignore", "cancel").forEach { word ->
            val current = agent.process(request("I owe Rahul the slides"))
            assertEquals(AgentResultStatus.DENIED, agent.answerPromise(current.requestId,
                requireNotNull(current.followUp).id, request(word)).status)
            assertEquals(AgentResultStatus.DENIED, agent.answerPromise(current.requestId,
                current.followUp.id, request("1")).status)
        }
        result = agent.process(request("I owe Rahul the slides"))
        assertEquals(AgentResultStatus.DENIED, agent.answerPromise(result.requestId,
            requireNotNull(result.followUp).id, request("1", now.plusSeconds(301))).status)
        assertEquals(0, store.writes)
    }

    @Test fun exactMatchingAndReadRoutingDoNotCreatePromises(): Unit = runBlocking {
        val store = FakeStore()
        val agent = agent(store)
        store.people = listOf(PromisePersonMatch(UUID.randomUUID(), "RAHUL"))
        val proposal = agent.process(request("I promised Rahul the slides")).proposals.single()
        assertEquals(store.people.single().id, (proposal.action as CreatePromiseAction).matchedPersonId)
        assertEquals(ActionRequirements.risk(TaskAction(ActionIdentity(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID()), TaskMutation.Create("example"))), ActionRequirements.risk(proposal.action))
        val read = agent.process(request("what did I promise Rahul?"))
        assertEquals(AgentResultStatus.ANSWER, read.status)
        assertTrue(read.proposals.isEmpty())
        assertFalse(read.message.contains("Review the promise"))
        assertEquals(0, store.writes)
    }

    private fun agent(store: FakeStore): ConfirmedPromiseAgent = ConfirmedPromiseAgent(object : ConfirmableAgent {
        override suspend fun process(request: AgentRequest) = AgentResult(request.id, AgentResultStatus.ANSWER,
            "Read request delegated.")
        override suspend fun accept(proposal: ActionProposal) = AgentResult(proposal.requestId,
            AgentResultStatus.DENIED, "Unsupported.")
        override fun discardProposal() = Unit
        override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
    }, store, { zone }, Clock.fixed(now, zone))

    private fun request(text: String, at: Instant = now) = AgentRequest(UUID.randomUUID(), text,
        InputSource.TEXT, at, conversationId = session)

    private inner class FakeStore : AcceptedPromiseStore {
        var writes = 0
        var people = emptyList<PromisePersonMatch>()
        override suspend fun context(request: AgentRequest): ResolvedContext = ResolvedContext(now, zone,
            AutonomyLevel.SUGGEST, CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request.screenContext, conversationId = request.conversationId)
        override suspend fun matchingPeople(name: String) = people.filter { it.name.equals(name, true) }
        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            return ActionReceipt(UUID.randomUUID(), proposal.action.identity, ActionType.CREATE_PROMISE,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
                "Promise saved.")
        }
    }
}
