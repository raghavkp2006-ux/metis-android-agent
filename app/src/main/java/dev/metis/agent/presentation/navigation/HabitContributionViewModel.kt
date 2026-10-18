@file:Suppress("MagicNumber") // The grid refreshes at the next local midnight.

package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.discipline.HabitContributionCalculator
import dev.metis.agent.domain.discipline.HabitContributionReport
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

internal data class HabitContributionUiState(
    val today: LocalDate,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val report: HabitContributionReport? = null,
    val selectedDate: LocalDate? = null,
) {
    val selectedDay get() = report?.days?.firstOrNull { it.date == selectedDate }
}

/** Read-only progress screen. Every read uses the same bounded Phase 1 repository range. */
internal class HabitContributionViewModel(
    private val repository: DisciplineRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private fun calculator() = HabitContributionCalculator(clock, zone())
    private val state = MutableStateFlow(HabitContributionUiState(today = calculator().today()))
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null

    init {
        reload()
        startMidnightTimer()
    }

    /** Re-read the current zone's local date on resume and after a timezone change. */
    fun refreshToday() = reload()

    fun selectDate(date: LocalDate) {
        if (state.value.report?.range?.let { date in it.from..it.through } == true) {
            state.value = state.value.copy(selectedDate = date)
        }
    }

    fun reload() {
        val calculator = calculator()
        val today = calculator.today()
        val range = calculator.range(today)
        val current = state.value
        val selected = current.selectedDate?.takeIf { it in range.from..range.through } ?: today
        state.value = current.copy(today = today, loading = true, failed = false,
            report = null, selectedDate = selected)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val habits = repository.habits(range)
                val checkins = repository.checkins(range)
                val report = calculator.calculate(habits, checkins, today)
                state.value = state.value.copy(loading = false, failed = false, report = report,
                    selectedDate = selected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Never replace an unreadable key with empty or fabricated progress.
                state.value = state.value.copy(loading = false, failed = true, report = null)
            }
        }
    }

    private fun startMidnightTimer() {
        viewModelScope.launch {
            while (true) {
                val now = clock.instant()
                val currentZone = zone()
                val nextMidnight = now.atZone(currentZone).toLocalDate().plusDays(1)
                    .atStartOfDay(currentZone).toInstant()
                delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L))
                reload()
            }
        }
    }
}
