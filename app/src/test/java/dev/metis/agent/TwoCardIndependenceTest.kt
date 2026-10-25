package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.ValidationResult
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.RequestViewModel
import dev.metis.agent.presentation.navigation.Workspace
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwoCardIndependenceTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun acceptCancelAndFailedRecheckAreIndependentForEachCard() {
        data class Case(val name: String, val cancelA: Boolean = false,
            val acceptBFirst: Boolean = false, val failB: Boolean = false)
        listOf(
            Case("accept A then accept B"),
            Case("cancel A then accept B", cancelA = true),
            Case("accept B then cancel A", cancelA = true, acceptBFirst = true),
            Case("accept A then fail B recheck", failB = true),
        ).forEach { case ->
            val clock = ConversationClock()
            val store = RecheckingTwoCardStore(clock)
            val model = RequestViewModel(ConfirmedTaskAgent(ConversationReads(), store, { clock.zone }), clock)
            model.submit("add a task to read and add a task to write", ConversationOrigin(Workspace.CHAT))
            val a = requireNotNull(model.uiState.value.result).proposals.single()
            val b = model.uiState.value.parts.single().proposals.single()
            assertNotEquals(case.name, a.action.identity.id, b.action.identity.id)
            assertTrue(case.name, store.receipts.isEmpty())
            assertTrue(case.name, store.attempts.isEmpty())
            if (case.acceptBFirst) {
                model.accept(b)
                assertEquals(case.name, listOf(b.action.identity.id), store.receipts.map { it.identity.id })
                assertEquals(case.name, listOf(b.action.identity.id), store.attempts)
                assertTrue(case.name, model.uiState.value.result?.proposals?.single() === a)
                model.reject(a)
            } else {
                if (case.cancelA) model.reject(a) else model.accept(a)
                assertEquals(case.name, if (case.cancelA) emptyList() else listOf(a.action.identity.id),
                    store.receipts.map { it.identity.id })
                assertEquals(case.name, if (case.cancelA) emptyList() else listOf(a.action.identity.id), store.attempts)
                assertTrue(case.name, model.uiState.value.parts.single().proposals.single() === b)
                if (case.failB) store.denied.add(b.action.identity.id)
                model.accept(b)
            }
            val expected = buildList {
                if (!case.cancelA) add(a.action.identity.id)
                if (!case.failB) add(b.action.identity.id)
            }
            assertEquals(case.name, expected.toSet(), store.receipts.map { it.identity.id }.toSet())
            assertEquals(case.name, expected.size, store.receipts.size)
            assertEquals(case.name, store.receipts.size, store.receipts.map { it.id }.distinct().size)
            val results = listOfNotNull(model.uiState.value.result) + model.uiState.value.parts
            val renderedReceipts = results.flatMap { it.completedActions }
            assertEquals(case.name, store.receipts.size, renderedReceipts.size)
            assertEquals(case.name, store.receipts.toSet(), renderedReceipts.toSet())
            results.forEachIndexed { index, result ->
                val identity = if (index == 0) a.action.identity else b.action.identity
                result.completedActions.forEach { assertEquals(case.name, identity, it.identity) }
            }
            if (case.failB) {
                assertEquals(AgentResultStatus.DENIED, model.uiState.value.parts.single().status)
                assertTrue(model.uiState.value.parts.single().completedActions.isEmpty())
            }
            if (case.cancelA) assertEquals(AgentResultStatus.DENIED, model.uiState.value.result?.status)
            model.accept(a)
            model.accept(b)
            assertEquals(case.name, expected.size, store.receipts.size)
        }
    }
}

private class RecheckingTwoCardStore(private val clock: ConversationClock,
    private val base: ConversationTaskStore = ConversationTaskStore(clock)) : AcceptedTaskStore by base {
    val receipts = mutableListOf<ActionReceipt>()
    val attempts = mutableListOf<UUID>()
    val denied = mutableSetOf<UUID>()
    private val contexts = mutableMapOf<UUID, ResolvedContext>()

    override suspend fun context(request: AgentRequest) = base.context(request).also { contexts[request.id] = it }

    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
        attempts.add(proposal.action.identity.id)
        val prior = requireNotNull(contexts[proposal.requestId])
        val capabilities = if (proposal.action.identity.id in denied)
            prior.capabilities.available - Capability.LOCAL_WRITE else prior.capabilities.available
        val refreshed = ResolvedContext(clock.instant(), prior.zoneId, prior.autonomy,
            CapabilitySnapshot(clock.instant(), capabilities), screenContext = prior.screenContext,
            conversationId = prior.conversationId, referencedEntities = prior.referencedEntities)
        val review = ProposalPolicy().reviewProposal(proposal, refreshed)
        if (review is ValidationResult.Invalid) throw ActionRejectedException(review.code, review.userMessage)
        return base.accept(proposal, confirmationId).also(receipts::add)
    }
}
