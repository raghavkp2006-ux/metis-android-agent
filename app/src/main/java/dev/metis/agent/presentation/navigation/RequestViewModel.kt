package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.agent.AgentDestination
import dev.metis.agent.domain.agent.AgentOrchestrator
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ScreenContext
import dev.metis.agent.domain.agent.baselineAgentOrchestrator
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RequestUiState(val busy: Boolean = false, val result: AgentResult? = null)

/** Requests/results remain in RAM. The composer is the sole current entry into the shared protocol. */
class RequestViewModel(
    private val orchestrator: AgentOrchestrator = baselineAgentOrchestrator(),
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val state = MutableStateFlow(RequestUiState())
    val uiState = state.asStateFlow()
    private var processing: Job? = null
    private var generation = 0L

    fun submit(text: String, destination: ShellDestination) {
        if (state.value.busy || text.isBlank() || text.length > ShellViewModel.MAX_DRAFT_LENGTH) return
        val request = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, clock.instant(),
            ScreenContext(AgentDestination.valueOf(destination.name)))
        val currentGeneration = ++generation
        state.value = RequestUiState(busy = true)
        processing = viewModelScope.launch {
            val result = try {
                orchestrator.process(request).also {
                    currentCoroutineContext().ensureActive()
                    require(it.requestId == request.id)
                }
            } catch (cancelled: CancellationException) {
                if (generation == currentGeneration) state.value = RequestUiState()
                throw cancelled
            } catch (_: Exception) {
                AgentResult(request.id, AgentResultStatus.FAILED, "The request could not be processed. Try again.")
            }
            if (generation == currentGeneration) state.value = RequestUiState(result = result)
        }
    }

    fun dismiss() {
        generation++
        processing?.cancel()
        processing = null
        state.value = RequestUiState()
    }
}
