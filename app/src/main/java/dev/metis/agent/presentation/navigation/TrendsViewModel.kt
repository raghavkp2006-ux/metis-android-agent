package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.discipline.TrendsCalculator
import dev.metis.agent.domain.discipline.TrendsReport
import dev.metis.agent.domain.storage.DisciplineRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class TrendsUiState(
    val today: LocalDate,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val report: TrendsReport? = null,
    val dismissed: Set<String> = emptySet(),
)

/** One bounded repository read per reload; never accesses discipline DAOs. */
internal class TrendsViewModel(
    private val repository: DisciplineRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private fun calculator() = TrendsCalculator(clock, zone())
    private val state = MutableStateFlow(TrendsUiState(calculator().today()))
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null

    init { reload(); startMidnightTimer() }
    fun refreshToday() = reload()
    fun dismiss(id: String) { state.value = state.value.copy(dismissed = state.value.dismissed + id) }

    fun reload() {
        val calculator = calculator()
        val today = calculator.today()
        val range = calculator.range(today)
        state.value = state.value.copy(today = today, loading = true, failed = false, report = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val habits = repository.habits(range)
                val checkins = repository.checkins(range)
                state.value = state.value.copy(loading = false,
                    report = calculator.calculate(habits, checkins, today))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // An unreadable key must never appear as a fabricated empty report.
                state.value = state.value.copy(loading = false, failed = true, report = null)
            }
        }
    }

    private fun startMidnightTimer() {
        viewModelScope.launch {
            while (true) {
                val now = clock.instant()
                val nextMidnight = now.atZone(zone()).toLocalDate().plusDays(1)
                    .atStartOfDay(zone()).toInstant()
                delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L))
                reload()
            }
        }
    }
}
