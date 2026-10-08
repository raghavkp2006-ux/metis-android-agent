package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.storage.MemoryRepository
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.MemorySearchResult
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleRepository
import dev.metis.agent.domain.storage.TaskRepository
import dev.metis.agent.domain.storage.TaskDependencyRepository
import dev.metis.agent.domain.storage.SavedTaskDependency
import dev.metis.agent.domain.storage.PreferenceRepository
import dev.metis.agent.domain.storage.SavedPreference
import dev.metis.agent.domain.storage.PlanningRepository
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedGoal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val dependencies: List<SavedTaskDependency> = emptyList(),
    val preferences: List<SavedPreference> = emptyList(),
    val search: MemorySearchUiState = MemorySearchUiState(),
    val projects: List<SavedProject> = emptyList(),
    val goals: List<SavedGoal> = emptyList(),
)

data class MemorySearchUiState(
    val query: String = "", val loading: Boolean = false, val failed: Boolean = false,
    val result: MemorySearchResult? = null,
    val candidateLimit: Int = MemorySearchQuery.DEFAULT_CANDIDATES,
)

class RecordsViewModel(
    private val tasks: TaskRepository,
    private val schedules: ScheduleRepository,
    private val memories: MemoryRepository,
    private val dependencies: TaskDependencyRepository,
    private val preferences: PreferenceRepository,
    private val planning: PlanningRepository,
) : ViewModel() {
    private val state = MutableStateFlow(RecordsUiState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    private var searchJob: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        searchJob?.cancel()
        state.value = RecordsUiState()
        observation = viewModelScope.launch {
            try {
                val base = combine(
                    tasks.observeTasks(), schedules.observeSchedules(), memories.observeMemories(),
                    dependencies.observeDependencies(), preferences.observePreferences(),
                ) { t, s, m, d, p ->
                    RecordsUiState(
                        loading = false, tasks = t, schedules = s, memories = m, dependencies = d, preferences = p,
                    )
                }
                combine(base, planning.observeProjects(), planning.observeGoals()) { records, projects, goals ->
                    records.copy(projects = projects, goals = goals)
                }.collect {
                    state.value = it.copy(search = state.value.search)
                    if (state.value.search.query.isNotBlank()) {
                        startSearch(state.value.search.query, state.value.search.candidateLimit)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Never log personal content or exception messages; discard previously decrypted UI rows.
                searchJob?.cancel()
                state.value = RecordsUiState(loading = false, failed = true)
            }
        }
    }

    fun searchMemories(text: String) {
        val budget = if (text == state.value.search.query) state.value.search.candidateLimit
            else MemorySearchQuery.DEFAULT_CANDIDATES
        startSearch(text, budget)
    }

    fun expandSearch() {
        startSearch(state.value.search.query, MemorySearchQuery.MAX_CANDIDATES)
    }

    private fun startSearch(text: String, candidateLimit: Int) {
        searchJob?.cancel()
        val query = text.take(MemorySearchQuery.MAX_SEARCH_LENGTH)
        state.value = state.value.copy(search = MemorySearchUiState(
            query, loading = query.isNotBlank(), candidateLimit = candidateLimit,
        ))
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            try {
                delay(SEARCH_DELAY_MS)
                val result = memories.searchMemories(MemorySearchQuery(query, candidateLimit = candidateLimit))
                state.value = state.value.copy(search = MemorySearchUiState(
                    query, result = result, candidateLimit = candidateLimit,
                ))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value.copy(search = MemorySearchUiState(
                    query, failed = true, candidateLimit = candidateLimit,
                ))
            }
        }
    }

    private companion object { const val SEARCH_DELAY_MS = 250L }
}
