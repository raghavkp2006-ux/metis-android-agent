package dev.metis.agent

import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.EnglishConversationState
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishConversationStateTest {
    private val clock = ConversationClock()
    private val store = ConversationTaskStore(clock)
    private val agent = ConfirmedTaskAgent(ConversationReads(), store, { clock.zone })
    private val state = EnglishConversationState(agent, clock)
    private val session = UUID.randomUUID()

    @Test fun twoValidHalvesRequireTwoIndependentCanonicalAccepts(): Unit = runBlocking {
        listOf(" and ", ", then ").forEach { separator ->
            val results = state.process(request("add a task to read${separator}add a task to write"))
            val proposals = results.map { it.proposals.single() }
            val before = store.writes
            val original = proposals.first()
            val forged = ActionProposal(original.id, original.action, original.risk, original.reason,
                original.evidence, original.requiresConfirmation, original.createdAt, original.expiresAt)
            state.accept(forged)
            assertEquals(before, store.writes)
            state.accept(proposals.last())
            assertEquals(before + 1, store.writes)
            assertTrue(state.results.first().proposals.single() === original)
            state.accept(proposals.last())
            assertEquals(before + 1, store.writes)
            state.accept(original)
            assertEquals(before + 2, store.writes)
        }
    }

    @Test fun invalidHalfAsksWithoutLosingTheValidProposal(): Unit = runBlocking {
        listOf(
            "add a task to read and create something",
            "nonsense and add a task to read",
        ).forEach { text ->
            val results = state.process(request(text))
            val proposal = results.flatMap { it.proposals }.single()
            assertEquals(1, results.count { it.status == AgentResultStatus.FOLLOW_UP })
            assertEquals(0, store.writes)
            val completed = state.process(request("add a task to write"))
            assertEquals(2, completed.flatMap { it.proposals }.size)
            assertTrue(completed.any { it.proposals.any { value -> value === proposal } })
        }
    }

    @Test fun threeIntentsAndCompoundNegationNeverPropose(): Unit = runBlocking {
        listOf(
            "add a task to read and add a task to write and add a task to study",
            "don't add a task to read and add a task to write",
        ).forEach { text ->
            val result = state.process(request(text))
            assertTrue(result.all { it.proposals.isEmpty() })
            assertEquals(0, store.writes)
        }
    }

    @Test fun missingReferenceCanBeClarifiedAndResetInvalidatesProposals(): Unit = runBlocking {
        val question = state.process(request("complete it")).single()
        assertEquals("Which item do you mean?", question.message)
        assertEquals(0, store.writes)
        val resolved = state.process(request("study")).single().proposals.single()
        assertTrue((resolved.action as TaskAction).mutation is TaskMutation.Complete)
        state.reset()
        state.accept(resolved)
        assertEquals(0, store.writes)
        assertTrue(state.process(request("complete it")).single().proposals.isEmpty())
    }

    @Test fun referenceExpirySessionAndLastThreeTurnsAreEnforced(): Unit = runBlocking {
        state.observe(request("add a task to study"))
        val first = state.process(request("complete it")).single()
        assertEquals(AgentResultStatus.PROPOSAL, first.status)
        state.dropPending()
        clock.current = clock.current.plusSeconds(600)
        assertEquals(AgentResultStatus.FOLLOW_UP, state.process(request("complete it")).single().status)
        clock.current = clock.current.plusSeconds(301)
        assertEquals(AgentResultStatus.DENIED, state.process(request("study")).single().status)
        assertEquals(0, store.writes)
        state.reset()
        state.observe(request("add a task to read"))
        repeat(3) { state.observe(request("show my tasks")) }
        assertTrue(state.process(request("complete it")).single().proposals.isEmpty())
        state.reset()
        state.observe(request("add a task to read"))
        assertTrue(state.process(request("complete it").copy(conversationId = UUID.randomUUID()))
            .single().proposals.isEmpty())
    }

    @Test fun cancellationDropsBothCardsWithoutWriting(): Unit = runBlocking {
        val proposals = state.process(request("add a task to read and add a task to write"))
            .flatMap { it.proposals }
        state.process(request("cancel"))
        proposals.forEach { state.accept(it) }
        assertEquals(0, store.writes)
        assertFalse(state.results.any { it.proposals.isNotEmpty() })
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        clock.instant(), conversationId = session)
}
