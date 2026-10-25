package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.AcceptedReminderStore
import dev.metis.agent.domain.agent.Action
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.EnglishConversationState
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ReminderHistoryEntry
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.contacts.ContactsResolver
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationActionFlowTest {
    private val clock = ConversationClock()
    private val tasks = ConversationTaskStore(clock)
    private val actions = ConversationActionStore(tasks, clock)
    private val session = UUID.randomUUID()

    @Test fun promisedPersonResolvesBeforeMissingTimeClarification(): Unit = runBlocking {
        val state = state()
        state.observe(request("I promised Rahul the slides"))
        val question = state.process(request("remind me tomorrow to call him")).single()
        assertEquals(setOf("time"), question.followUp?.missingFields)
        val proposal = state.process(request("8 pm")).single().proposals.single()
        assertEquals("call Rahul", (proposal.action as CreateReminderAction).title)
        assertEquals(0, actions.writes.size)
        state.accept(proposal)
        assertEquals(1, actions.writes.size)
    }

    @Test fun equalPeopleAskAndChoosingOneDoesNotAccept(): Unit = runBlocking {
        val state = state()
        state.process(request("promise Rahul the slides and promise Maya the notes"))
        val question = state.process(request("remind me tomorrow at 8 pm to call him")).single()
        assertEquals("Who do you mean?", question.message)
        assertEquals(listOf("Rahul", "Maya"), question.followUp?.suggestedChoices)
        val proposal = state.process(request("2")).single().proposals.single()
        assertEquals("call Maya", (proposal.action as CreateReminderAction).title)
        assertEquals(0, actions.writes.size)
    }

    @Test fun twoTimeClarificationsRetainIndependentPendingProposals(): Unit = runBlocking {
        val state = state()
        val initial = state.process(request("remind me tomorrow to study and remind me tomorrow to read"))
        assertEquals(2, initial.count { it.followUp?.missingFields == setOf("time") })
        val retried = state.process(request("nonsense"))
        assertEquals(2, retried.count { it.status == AgentResultStatus.FOLLOW_UP })
        val first = state.process(request("8 pm")).flatMap { it.proposals }.single()
        val both = state.process(request("9 pm")).flatMap { it.proposals }
        assertEquals(2, both.size)
        assertTrue(both.any { it === first })
        assertEquals(0, actions.writes.size)
        state.accept(both.last())
        assertEquals(1, actions.writes.size)
        assertTrue(state.results.first().proposals.single() === first)
        state.accept(first)
        assertEquals(2, actions.writes.size)
    }

    @Test fun deniedPermissionOnOneCardDoesNotConsumeItsSibling(): Unit = runBlocking {
        val state = state(FakeContactsResolver(emptyList(), permitted = false))
        val initial = state.process(request("promise Rahul the slides and add a task to read"))
        val sibling = initial.last().proposals.single()
        val question = requireNotNull(initial.first().followUp)
        assertTrue(state.hasPermissionQuestion(question.id))
        val results = state.permission(question.id, false)
        assertEquals(2, results.flatMap { it.proposals }.size)
        assertTrue(results.last().proposals.single() === sibling)
        val action = results.first().proposals.single().action as CreatePromiseAction
        assertEquals("Rahul", action.personName)
        assertEquals(null, action.contactLookupKey)
        assertEquals(0, actions.writes.size)
        assertEquals(0, tasks.writes)
    }

    private fun state(contacts: ContactsResolver? = null): EnglishConversationState {
        val reminders = ConfirmedReminderAgent(ConfirmedTaskAgent(ConversationReads(), tasks, { clock.zone }),
            actions, { clock.zone }, clock, contacts)
        return EnglishConversationState(
            ConfirmedPromiseAgent(reminders, actions, { clock.zone }, clock, contacts), clock)
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        clock.instant(), conversationId = session)
}

internal class ConversationActionStore(private val tasks: ConversationTaskStore, private val clock: ConversationClock) :
    AcceptedPromiseStore, AcceptedReminderStore {
    val writes = mutableListOf<Action>()
    override suspend fun context(request: AgentRequest) = tasks.context(request).let {
        dev.metis.agent.domain.agent.ResolvedContext(it.now, it.zoneId, it.autonomy,
            dev.metis.agent.domain.agent.CapabilitySnapshot(it.now, it.capabilities.available +
                setOf(dev.metis.agent.domain.agent.Capability.REMINDER_SCHEDULE,
                    dev.metis.agent.domain.agent.Capability.NOTIFICATIONS)),
            screenContext = it.screenContext, conversationId = it.conversationId)
    }
    override suspend fun matchingPeople(name: String) = emptyList<PromisePersonMatch>()
    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
        writes.add(proposal.action)
        return ActionReceipt(UUID.randomUUID(), proposal.action.identity,
            if (proposal.action is CreatePromiseAction) ActionType.CREATE_PROMISE else ActionType.CREATE_REMINDER,
            ReceiptOutcome(ActionStatus.SUCCEEDED, clock.instant(), clock.instant(),
                OutcomeVerification.VERIFIED_LOCAL), "Synthetic action saved")
    }
    override suspend fun retry(actionId: UUID): ActionReceipt = error("Unexpected retry")
    override suspend fun cancel(actionId: UUID): UndoResult = error("Unexpected cancel")
    override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
    override suspend fun reconcile() = Unit
    override fun history() = flowOf(emptyList<ReminderHistoryEntry>())
}
