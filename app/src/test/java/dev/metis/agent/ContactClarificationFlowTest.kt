package dev.metis.agent

import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.FollowUpQuestion
import dev.metis.agent.domain.agent.ReminderClarification
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.presentation.navigation.ContactLookupUiState
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.RequestViewModel
import dev.metis.agent.presentation.navigation.Workspace
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
class ContactClarificationFlowTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-09T05:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val origin = ConversationOrigin(Workspace.CHAT)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun reminderAnswerDoesNotConsumeContactQuestion() {
        val contacts = FakeContactsResolver(listOf(contact("Raghav"), contact("Raghav")))
        val model = RequestViewModel(SyntheticReminderAgent(), clock, SyntheticReminderAgent(), contacts)
        model.findContact("Raghav")
        val question = model.uiState.value.contactLookup as ContactLookupUiState.Ambiguous
        assertEquals("Which Raghav?", question.question)
        model.submit("8 am", origin)
        assertTrue(model.uiState.value.contactLookup is ContactLookupUiState.Ambiguous)
        model.answerContact(question.questionId, 0)
        assertTrue(model.uiState.value.contactLookup is ContactLookupUiState.Resolved)
    }

    @Test fun contactAnswerDoesNotConsumeReminderQuestion() {
        val reminders = SyntheticReminderAgent()
        val model = RequestViewModel(reminders, clock, reminders,
            FakeContactsResolver(listOf(contact("Raghav"))))
        model.submit("remind me tomorrow to study", origin)
        assertEquals(AgentResultStatus.FOLLOW_UP, model.uiState.value.result?.status)
        model.answerContact(UUID.randomUUID(), 0)
        assertEquals(AgentResultStatus.FOLLOW_UP, model.uiState.value.result?.status)
        model.submit("8 am", origin)
        assertEquals(1, reminders.answers)
        assertEquals(AgentResultStatus.ANSWER, model.uiState.value.result?.status)
    }

    @Test fun staleQuestionAndLatePermissionResultAreIgnored() {
        val contacts = FakeContactsResolver(listOf(contact("Raghav"), contact("Raghav")))
        val model = RequestViewModel(SyntheticReminderAgent(), clock, contactsResolver = contacts)
        model.findContact("Raghav")
        val first = model.uiState.value.contactLookup as ContactLookupUiState.Ambiguous
        model.findContact("Raghav")
        val second = model.uiState.value.contactLookup as ContactLookupUiState.Ambiguous
        model.answerContact(first.questionId, 0)
        assertTrue(model.uiState.value.contactLookup is ContactLookupUiState.Ambiguous)
        model.answerContact(second.questionId, 0)
        assertTrue(model.uiState.value.contactLookup is ContactLookupUiState.Resolved)

        contacts.permitted = false
        model.findContact("Raghav")
        val permission = model.uiState.value.contactLookup as ContactLookupUiState.PermissionNeeded
        model.cancelContactLookup()
        model.onContactPermissionResult(permission.questionId, true, false)
        assertEquals(ContactLookupUiState.Idle, model.uiState.value.contactLookup)

        val recreated = RequestViewModel(SyntheticReminderAgent(), clock, contactsResolver = contacts)
        recreated.onContactPermissionResult(permission.questionId, true, false)
        assertEquals(ContactLookupUiState.Idle, recreated.uiState.value.contactLookup)
    }

    @Test fun deniedPermanentAndRevokedAccessStayReadOnly() {
        val contacts = FakeContactsResolver(listOf(contact("Raghav")), permitted = false)
        val model = RequestViewModel(SyntheticReminderAgent(), clock, contactsResolver = contacts,
            contactsPermissionBlocked = { true }, explainPermanentContactsDenial = { true })
        model.findContact("Raghav")
        val needed = model.uiState.value.contactLookup as ContactLookupUiState.PermissionNeeded
        assertTrue(needed.permanentlyDenied)
        assertTrue(model.hasPendingContactPermission(needed.questionId))
        model.onContactPermissionResult(UUID.randomUUID(), true, false)
        assertTrue(model.uiState.value.contactLookup is ContactLookupUiState.PermissionNeeded)
        model.onContactPermissionResult(needed.questionId, false, true)
        assertTrue((model.uiState.value.contactLookup as ContactLookupUiState.Denied).permanentlyDenied)
        contacts.permitted = true
        model.findContact("Raghav")
        assertTrue(model.uiState.value.contactLookup is ContactLookupUiState.Resolved)
        contacts.permitted = false
        model.findContact("Raghav")
        assertTrue(model.uiState.value.contactLookup is ContactLookupUiState.PermissionNeeded)
        assertFalse(model.uiState.value.toString().contains("Raghav"))
    }

    private fun contact(name: String) = ContactCandidate(UUID.randomUUID().toString(), name, "••42")
}

private class SyntheticReminderAgent : ConfirmableAgent, ReminderClarification {
    var answers = 0
    override suspend fun process(request: AgentRequest): AgentResult =
        if (request.text.startsWith("remind me")) {
            val question = "What time? Use AM/PM or HH:mm."
            AgentResult(request.id, AgentResultStatus.FOLLOW_UP, question,
                followUp = FollowUpQuestion(UUID.randomUUID(), request.id, question, setOf("time")))
        } else AgentResult(request.id, AgentResultStatus.UNSUPPORTED, "Synthetic unsupported request")

    override suspend fun answerReminder(originalRequestId: UUID, questionId: UUID,
        answer: AgentRequest): AgentResult {
        answers++
        return AgentResult(answer.id, AgentResultStatus.ANSWER, "Synthetic reminder answer")
    }

    override fun cancelReminderClarification() = Unit
    override suspend fun accept(proposal: ActionProposal): AgentResult = error("Unexpected accept")
    override fun discardProposal() = Unit
    override suspend fun undo(receipt: ActionReceipt): UndoResult = error("Unexpected undo")
}
