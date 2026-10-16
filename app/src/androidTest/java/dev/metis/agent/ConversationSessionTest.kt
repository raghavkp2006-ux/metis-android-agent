package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentOrchestrator
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.RequestViewModel
import dev.metis.agent.presentation.navigation.Workspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationSessionTest {
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun await(model: RequestViewModel) = runBlocking {
        withTimeout(10_000) { model.uiState.first { !it.busy } }
    }

    @Test fun resetBlocksLateCallbacksAndANewModelHasNoConversation() {
        val response = CompletableDeferred<Unit>()
        val agent = object : AgentOrchestrator {
            override suspend fun process(request: AgentRequest): AgentResult {
                withContext(NonCancellable) { response.await() }
                return AgentResult(request.id, AgentResultStatus.ANSWER, "Synthetic late answer")
            }
        }
        val model = RequestViewModel(agent)
        val session = model.uiState.value.sessionId
        main {
            model.submit("Synthetic request", ConversationOrigin(Workspace.MEMORY))
            model.submit("Duplicate must be ignored", ConversationOrigin(Workspace.CHAT))
            assertEquals(1, model.uiState.value.turns.size)
            model.dismiss()
        }
        response.complete(Unit)
        main { assertTrue(model.uiState.value.turns.isEmpty()) }
        assertNotEquals(session, model.uiState.value.sessionId)
        assertTrue(RequestViewModel(agent).uiState.value.turns.isEmpty())
    }

    @Test fun staleActionsDuplicateAcceptAndResetDuringCommitCannotMutateSavedData() {
        StorageTestFixture().use { fixture ->
            val repository = fixture.repository
            val delegate = ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions)
            val commitGate = CompletableDeferred<Unit>()
            val agent = object : ConfirmableAgent {
                override suspend fun process(request: AgentRequest) = delegate.process(request)
                override fun discardProposal() = delegate.discardProposal()
                override suspend fun accept(proposal: ActionProposal): AgentResult {
                    commitGate.await()
                    return delegate.accept(proposal)
                }
                override suspend fun undo(receipt: ActionReceipt): UndoResult = delegate.undo(receipt)
            }
            val model = RequestViewModel(agent)
            main { model.submit("add a task to Synthetic first", ConversationOrigin(Workspace.TODAY)) }
            val stale = requireNotNull(await(model).result).proposals.single()
            main { model.submit("add a task to Synthetic second", ConversationOrigin(Workspace.PLAN)) }
            val current = requireNotNull(await(model).result).proposals.single()
            main {
                model.accept(stale)
                assertFalse(model.uiState.value.busy)
                model.accept(current)
                model.accept(current)
                model.dismiss()
                assertTrue(model.uiState.value.accepting)
                assertEquals(2, model.uiState.value.turns.size)
            }
            commitGate.complete(Unit)
            val completed = await(model)
            val receipt = requireNotNull(completed.result).completedActions.single()
            val tasks = runBlocking { repository.observeTasks().first() }
            assertEquals(listOf("Synthetic second"), tasks.map { it.title })
            main { model.undo(receipt); model.undo(receipt) }
            await(model)
            assertTrue(runBlocking { repository.observeTasks().first().isEmpty() })
            assertEquals(2, model.uiState.value.turns.last().outcomes.size)
            main { model.dismiss() }
            assertTrue(model.uiState.value.turns.isEmpty())
        }
    }
}
