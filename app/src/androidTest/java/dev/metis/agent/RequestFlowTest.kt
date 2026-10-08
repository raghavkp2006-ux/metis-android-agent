package dev.metis.agent

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.agent.AgentOrchestrator
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.presentation.navigation.RequestViewModel
import dev.metis.agent.presentation.navigation.ShellDestination
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun duplicateSubmitDuringProcessingCreatesOneRequestAndDismissCancelsLateResult() {
        val calls = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val orchestrator = object : AgentOrchestrator {
            override suspend fun process(request: AgentRequest): AgentResult {
                calls.incrementAndGet()
                // Even a dependency that delays cancellation must not republish a dismissed request.
                withContext(NonCancellable) { release.await() }
                return AgentResult(request.id, AgentResultStatus.ANSWER, "Synthetic response")
            }
        }
        val model = RequestViewModel(orchestrator)
        composeRule.runOnUiThread {
            model.submit("Synthetic first request", ShellDestination.PLAN)
            model.submit("Synthetic duplicate", ShellDestination.PLAN)
        }
        composeRule.waitUntil(TIMEOUT) { calls.get() == 1 }
        composeRule.runOnUiThread { model.dismiss() }
        release.complete(Unit)
        composeRule.waitForIdle()
        assertEquals(1, calls.get())
        assertNull(model.uiState.value.result)
        assertFalse(model.uiState.value.busy)
    }

    @Test
    fun invalidInputNeverReachesPipelineAndPrivateFailureIsNotDisplayed() {
        val calls = AtomicInteger()
        val model = RequestViewModel(object : AgentOrchestrator {
            override suspend fun process(request: AgentRequest): AgentResult {
                calls.incrementAndGet()
                error("Synthetic private failure")
            }
        })
        composeRule.runOnUiThread {
            model.submit(" ", ShellDestination.YOU)
            model.submit("x".repeat(4_001), ShellDestination.YOU)
        }
        assertEquals(0, calls.get())
        composeRule.runOnUiThread { model.submit("Synthetic valid request", ShellDestination.YOU) }
        composeRule.waitUntil(TIMEOUT) { model.uiState.value.result != null }
        assertEquals(AgentResultStatus.FAILED, model.uiState.value.result?.status)
        assertFalse(model.uiState.value.result!!.message.contains("private"))
        assertFalse(model.uiState.value.busy)
    }
    private companion object { const val TIMEOUT = 10_000L }
}
