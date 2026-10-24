package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.RequestViewModel
import dev.metis.agent.presentation.navigation.Workspace
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionContactViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-23T10:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(now, zone)
    private val origin = ConversationOrigin(Workspace.CHAT)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun rationalePrecedesPermissionResultAndDenialFallsBackToProposal() {
        val contacts = FakeContactsResolver(listOf(contact("Rahul")), permitted = false)
        var mayAsk = true
        val agent = agent(contacts) { mayAsk }
        val model = RequestViewModel(agent, clock, promiseClarification = agent)
        model.submit("I owe Rahul the slides", origin)
        val question = requireNotNull(model.uiState.value.result)
        assertEquals(setOf("contact_permission"), question.followUp?.missingFields)
        assertTrue(question.proposals.isEmpty())
        val questionId = requireNotNull(question.followUp).id
        assertTrue(model.hasPendingActionContactPermission(questionId))
        mayAsk = false
        model.onActionContactPermissionResult(questionId, false)
        assertEquals(AgentResultStatus.PROPOSAL, model.uiState.value.result?.status)
        assertFalse(model.hasPendingActionContactPermission(questionId))
        model.submit("I owe Rahul the slides", origin)
        assertEquals(AgentResultStatus.PROPOSAL, model.uiState.value.result?.status)
    }

    @Test fun tappableAmbiguityDoesNotExposeContactNamesInStateString() {
        val contacts = FakeContactsResolver(listOf(contact("Raghav Shah"), contact("Raghav Sharma")))
        val agent = agent(contacts) { true }
        val model = RequestViewModel(agent, clock, promiseClarification = agent)
        model.submit("I promised Raghav the slides", origin)
        val result = requireNotNull(model.uiState.value.result)
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertEquals(2, result.followUp?.suggestedChoices?.size)
        assertFalse(model.uiState.value.toString().contains("Raghav Shah"))
        model.chooseActionPerson(1)
        assertEquals(AgentResultStatus.PROPOSAL, model.uiState.value.result?.status)
        assertTrue(model.uiState.value.turns.first().result?.followUp?.suggestedChoices?.isEmpty() == true)
        model.reject()
        assertTrue(model.uiState.value.turns.last().result?.proposals?.isEmpty() == true)
    }

    private fun agent(contacts: FakeContactsResolver, mayAsk: () -> Boolean): ConfirmedPromiseAgent =
        ConfirmedPromiseAgent(object : ConfirmableAgent {
            override suspend fun process(request: AgentRequest) =
                AgentResult(request.id, AgentResultStatus.UNSUPPORTED, "Unsupported")
            override suspend fun accept(proposal: ActionProposal): AgentResult = error("Unexpected accept")
            override fun discardProposal() = Unit
            override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
        }, object : AcceptedPromiseStore {
            override suspend fun context(request: AgentRequest) = ResolvedContext(now, zone,
                AutonomyLevel.SUGGEST, CapabilitySnapshot(now,
                    setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
                conversationId = request.conversationId)
            override suspend fun matchingPeople(name: String) = emptyList<PromisePersonMatch>()
            override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt =
                error("Unexpected accept")
        }, { zone }, clock, contacts, mayAsk)

    private fun contact(name: String) = ContactCandidate(UUID.randomUUID().toString(), name,
        lookupKey = "lookup-${UUID.randomUUID()}")
}
