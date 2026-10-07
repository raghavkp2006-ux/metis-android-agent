package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.storage.MemoryRepository
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleRepository
import dev.metis.agent.domain.storage.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class RecordsUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val tasks: List<SavedTask> = emptyList(),
    val schedules: List<SavedSchedule> = emptyList(),
    val memories: List<SavedMemory> = emptyList(),
)

class RecordsViewModel(
    private val tasks: TaskRepository,
    private val schedules: ScheduleRepository,
    private val memories: MemoryRepository,
) : ViewModel() {
    private val state = MutableStateFlow(RecordsUiState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        state.value = RecordsUiState()
        observation = viewModelScope.launch {
            try {
                combine(tasks.observeTasks(), schedules.observeSchedules(), memories.observeMemories()) { t, s, m ->
                    RecordsUiState(loading = false, tasks = t, schedules = s, memories = m)
                }.collect { state.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Never log personal content or exception messages; discard previously decrypted UI rows.
                state.value = RecordsUiState(loading = false, failed = true)
            }
        }
    }
}
