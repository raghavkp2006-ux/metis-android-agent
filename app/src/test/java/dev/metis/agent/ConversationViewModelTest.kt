package dev.metis.agent

import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.RequestViewModel
import dev.metis.agent.presentation.navigation.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = ConversationClock()
    private val store = ConversationTaskStore(clock)
    private val model = RequestViewModel(ConfirmedTaskAgent(ConversationReads(), store, { clock.zone }), clock)
    private val origin = ConversationOrigin(Workspace.CHAT)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun bothCardsRemainEligibleAndEachAcceptWritesOnlyItsOwnAction() {
        model.submit("add a task to read and add a task to write", origin)
        val state = model.uiState.value
        val first = requireNotNull(state.result).proposals.single()
        val second = state.parts.single().proposals.single()
        assertEquals(0, store.writes)
        assertTrue(state.turns.last().parts.single() === state.parts.single())
        model.accept(second)
        assertEquals(1, store.writes)
        assertTrue(model.uiState.value.result?.proposals?.single() === first)
        assertTrue(model.uiState.value.parts.single().completedActions.isNotEmpty())
        model.accept(first)
        assertEquals(2, store.writes)
        model.accept(first)
        assertEquals(2, store.writes)
    }

    @Test fun referencesUsePriorSingleTurnsAndNewChatClearsThem() {
        model.submit("add a task to study", origin)
        model.submit("complete it", origin)
        assertEquals(AgentResultStatus.PROPOSAL, model.uiState.value.result?.status)
        val stale = requireNotNull(model.uiState.value.result).proposals.single()
        model.dismiss()
        model.accept(stale)
        assertEquals(0, store.writes)
        model.submit("complete it", origin)
        assertEquals("Which item do you mean?", model.uiState.value.result?.message)
    }

    @Test fun navigatingAwayInvalidatesBothPendingCards() {
        model.submit("add a task to read and add a task to write", origin)
        val first = requireNotNull(model.uiState.value.result).proposals.single()
        val second = model.uiState.value.parts.single().proposals.single()
        model.cancelReminderClarification()
        model.accept(first)
        model.accept(second)
        assertEquals(0, store.writes)
    }

    @Test fun barePersonReferenceResumesTheExistingSingleClarification() {
        val actions = ConversationActionStore(store, clock)
        val reminders = ConfirmedReminderAgent(ConfirmedTaskAgent(ConversationReads(), store),
            actions, { clock.zone }, clock)
        val promises = ConfirmedPromiseAgent(reminders, actions, { clock.zone }, clock)
        val requestModel = RequestViewModel(promises, clock, reminderClarification = reminders,
            promiseClarification = promises)
        requestModel.submit("what did I promise Rahul", origin)
        requestModel.submit("I promised the slides", origin)
        assertEquals(setOf("person"), requestModel.uiState.value.result?.followUp?.missingFields)
        requestModel.submit("him", origin)
        val action = requireNotNull(requestModel.uiState.value.result).proposals.single().action as CreatePromiseAction
        assertEquals("Rahul", action.personName)
        assertEquals(0, actions.writes.size)
    }

    @Test fun ambiguousBareReferenceKeepsThePendingActionUntilAChoice() {
        val actions = ConversationActionStore(store, clock)
        val reminders = ConfirmedReminderAgent(ConfirmedTaskAgent(ConversationReads(), store),
            actions, { clock.zone }, clock)
        val promises = ConfirmedPromiseAgent(reminders, actions, { clock.zone }, clock)
        val requestModel = RequestViewModel(promises, clock, reminderClarification = reminders,
            promiseClarification = promises)
        requestModel.submit("promise Rahul the slides and promise Maya the notes", origin)
        requestModel.cancelPromiseClarification()
        requestModel.submit("I promised the paper", origin)
        requestModel.submit("him", origin)
        assertEquals("Who do you mean?", requestModel.uiState.value.result?.message)
        requestModel.chooseActionPerson(1)
        val action = requireNotNull(requestModel.uiState.value.result).proposals.single().action as CreatePromiseAction
        assertEquals("Maya", action.personName)
        assertEquals("the paper", action.description)
        assertEquals(0, actions.writes.size)
    }
}
