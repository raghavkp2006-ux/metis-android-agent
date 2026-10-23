package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.AcceptedReminderStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.ReminderHistoryEntry
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.contacts.ContactCandidate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactActionFlowTest {
    private val now = Instant.parse("2026-10-23T10:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(now, zone)
    private val session = UUID.randomUUID()

    @Test fun promiseDoesNotProposeUntilAmbiguousContactIsChosen(): Unit = runBlocking {
        val contacts = FakeContactsResolver(listOf(contact("Raghav Shah"), contact("Raghav Sharma")))
        val store = PromiseStore()
        val agent = ConfirmedPromiseAgent(NoopAgent(), store, { zone }, clock, contacts)
        val request = request("I promised Raghav the slides")
        val question = agent.process(request)
        assertEquals(AgentResultStatus.FOLLOW_UP, question.status)
        assertEquals("Which Raghav?", question.message)
        assertEquals(2, question.followUp?.suggestedChoices?.size)
        assertTrue(question.proposals.isEmpty())
        assertEquals(0, store.writes)
        val answer = agent.answerPromise(request.id, requireNotNull(question.followUp).id, request("1"))
        assertEquals(AgentResultStatus.PROPOSAL, answer.status)
        val action = answer.proposals.single().action as CreatePromiseAction
        assertTrue(action.personName.startsWith("Raghav"))
        assertTrue(requireNotNull(action.contactLookupKey).startsWith("lookup-"))
        assertEquals(0, store.writes)
        agent.accept(answer.proposals.single())
        assertEquals(1, store.writes)
    }

    @Test fun promiseDenialAndRevocationKeepTypedNameWithoutRepeatingPermission(): Unit = runBlocking {
        val contacts = FakeContactsResolver(listOf(contact("Rahul")), permitted = false)
        val store = PromiseStore()
        var mayAsk = true
        val agent = ConfirmedPromiseAgent(NoopAgent(), store, { zone }, clock, contacts, { mayAsk })
        val request = request("I owe Rahul the slides")
        val question = agent.process(request)
        assertEquals(setOf("contact_permission"), question.followUp?.missingFields)
        assertTrue(question.proposals.isEmpty())
        mayAsk = false
        val fallback = agent.answerPromiseContactPermission(request.id, requireNotNull(question.followUp).id,
            request("permission result"), false)
        assertEquals(AgentResultStatus.PROPOSAL, fallback.status)
        val action = fallback.proposals.single().action as CreatePromiseAction
        assertEquals("Rahul", action.personName)
        assertEquals(null, action.contactLookupKey)
        assertEquals(0, store.writes)
        val later = agent.process(request("I owe Rahul the slides"))
        assertEquals(AgentResultStatus.PROPOSAL, later.status)
        assertEquals(null, (later.proposals.single().action as CreatePromiseAction).contactLookupKey)
        contacts.permitted = true
        val resolved = agent.process(request("I owe Rahul the slides"))
        assertTrue((resolved.proposals.single().action as CreatePromiseAction).contactLookupKey != null)
        contacts.permitted = false
        val revoked = agent.process(request("I owe Rahul the slides"))
        assertEquals(AgentResultStatus.PROPOSAL, revoked.status)
        assertEquals(null, (revoked.proposals.single().action as CreatePromiseAction).contactLookupKey)
    }

    @Test fun reminderAmbiguityAndRevocationNeverScheduleBeforeAcceptance(): Unit = runBlocking {
        val contacts = FakeContactsResolver(listOf(contact("Raghav Shah"), contact("Raghav Sharma")))
        val store = ReminderStore()
        val agent = ConfirmedReminderAgent(NoopAgent(), store, { zone }, clock, contacts)
        val request = request("remind me tomorrow at 8 pm to call Raghav")
        val question = agent.process(request)
        assertEquals(AgentResultStatus.FOLLOW_UP, question.status)
        assertEquals("Which Raghav?", question.message)
        assertTrue(question.proposals.isEmpty())
        assertEquals(0, store.writes)
        contacts.permitted = false
        val fallback = agent.answerReminderPerson(request.id, requireNotNull(question.followUp).id, request("1"))
        assertEquals(AgentResultStatus.PROPOSAL, fallback.status)
        val action = fallback.proposals.single().action as CreateReminderAction
        assertEquals(null, action.personId)
        assertEquals(null, action.contactLookupKey)
        assertEquals(0, store.writes)
    }

    @Test fun reminderPermissionSkipAndExactSavedPersonAreSafe(): Unit = runBlocking {
        val contacts = FakeContactsResolver(listOf(contact("Rahul")), permitted = false)
        val store = ReminderStore()
        val agent = ConfirmedReminderAgent(NoopAgent(), store, { zone }, clock, contacts)
        val request = request("remind me tomorrow at 8 pm to call Rahul")
        val question = agent.process(request)
        assertEquals(setOf("reminder_contact_permission"), question.followUp?.missingFields)
        assertTrue(question.proposals.isEmpty())
        val skipped = agent.answerReminderContactPermission(request.id, requireNotNull(question.followUp).id,
            request("permission result"), false)
        assertEquals(AgentResultStatus.PROPOSAL, skipped.status)
        assertEquals(null, (skipped.proposals.single().action as CreateReminderAction).personId)
        val saved = PromisePersonMatch(UUID.randomUUID(), "RAHUL")
        store.people = listOf(saved)
        val exact = agent.process(request("remind me tomorrow at 8 pm to call Rahul"))
        assertEquals(AgentResultStatus.PROPOSAL, exact.status)
        assertEquals(saved.id, (exact.proposals.single().action as CreateReminderAction).personId)
        assertFalse(exact.message.contains("lookup-"))
    }

    @Test fun unclearContactFollowUpsReaskOnceThenDropWithoutProposal(): Unit = runBlocking {
        val many = FakeContactsResolver(List(6) { contact("Raghav") })
        val promises = ConfirmedPromiseAgent(NoopAgent(), PromiseStore(), { zone }, clock, many)
        val promiseRequest = request("I promised Raghav the slides")
        val first = promises.process(promiseRequest)
        assertEquals(setOf("contact_refine"), first.followUp?.missingFields)
        assertTrue(first.proposals.isEmpty())
        val second = promises.answerPromise(promiseRequest.id, requireNotNull(first.followUp).id,
            request("Raghav"))
        assertEquals(AgentResultStatus.FOLLOW_UP, second.status)
        val dropped = promises.answerPromise(promiseRequest.id, requireNotNull(second.followUp).id,
            request("Raghav"))
        assertEquals(AgentResultStatus.DENIED, dropped.status)
        assertTrue(dropped.proposals.isEmpty())

        val reminders = ConfirmedReminderAgent(NoopAgent(), ReminderStore(), { zone }, clock,
            FakeContactsResolver(listOf(contact("Raghav Shah"), contact("Raghav Sharma"))))
        val reminderRequest = request("remind me tomorrow at 8 pm to call Raghav")
        val choice = reminders.process(reminderRequest)
        val retried = reminders.answerReminderPerson(reminderRequest.id, requireNotNull(choice.followUp).id,
            request("maybe"))
        assertEquals(AgentResultStatus.FOLLOW_UP, retried.status)
        val denied = reminders.answerReminderPerson(reminderRequest.id, requireNotNull(retried.followUp).id,
            request("maybe"))
        assertEquals(AgentResultStatus.DENIED, denied.status)
        assertTrue(denied.proposals.isEmpty())
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        now, conversationId = session)

    private fun contact(name: String) = ContactCandidate(UUID.randomUUID().toString(), name,
        lookupKey = "lookup-${UUID.randomUUID()}")

    private fun context(request: AgentRequest) = ResolvedContext(now, zone, AutonomyLevel.SUGGEST,
        CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE,
            Capability.REMINDER_SCHEDULE, Capability.NOTIFICATIONS)), conversationId = request.conversationId)

    private inner class PromiseStore : AcceptedPromiseStore {
        var writes = 0
        override suspend fun context(request: AgentRequest) = this@ContactActionFlowTest.context(request)
        override suspend fun matchingPeople(name: String) = emptyList<PromisePersonMatch>()
        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            return ActionReceipt(UUID.randomUUID(), proposal.action.identity, ActionType.CREATE_PROMISE,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL), "Saved")
        }
    }

    private inner class ReminderStore : AcceptedReminderStore {
        var writes = 0
        var people = emptyList<PromisePersonMatch>()
        override suspend fun context(request: AgentRequest) = this@ContactActionFlowTest.context(request)
        override suspend fun matchingPeople(name: String) = people.filter { it.name.equals(name, true) }
        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            return error("Unexpected reminder acceptance")
        }
        override suspend fun retry(actionId: UUID): ActionReceipt = error("Unexpected retry")
        override suspend fun cancel(actionId: UUID): UndoResult = error("Unexpected cancel")
        override suspend fun undo(receipt: ActionReceipt): UndoResult = error("Unexpected undo")
        override suspend fun reconcile() = Unit
        override fun history(): Flow<List<ReminderHistoryEntry>> = emptyFlow()
    }

    private class NoopAgent : ConfirmableAgent {
        override suspend fun process(request: AgentRequest) =
            AgentResult(request.id, AgentResultStatus.UNSUPPORTED, "Unsupported")
        override suspend fun accept(proposal: ActionProposal): AgentResult = error("Unexpected acceptance")
        override fun discardProposal() = Unit
        override suspend fun undo(receipt: ActionReceipt): UndoResult = UndoResult.NOT_SUPPORTED
    }
}
