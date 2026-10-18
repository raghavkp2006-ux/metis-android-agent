@file:Suppress("MagicNumber") // Seven-day backfill window and mood bounds are Phase 1 domain constants.

package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.DisciplineDates
import dev.metis.agent.domain.storage.DisciplineRepository
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.RevisionConflictException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Clear user-facing failures; the key/storage failure reuses the shared storage warning. */
internal const val BACKFILL_REJECTED = "Choose today or one of the previous seven days."
internal const val CHECKIN_CONFLICT_MESSAGE = "This check-in changed. Showing the saved version."
internal const val ARCHIVE_REJECTED = "That habit can't be archived yet. Delete it instead."
internal const val START_REJECTED = "Choose a start date from today or the previous seven days."
internal const val NOTE_LIMIT = 4000
private const val BACKFILL_DAYS = 7L

internal data class HabitCheckinUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val busy: Boolean = false,
    val conflict: Boolean = false,
    val saved: Boolean = false,
    val today: LocalDate,
    val selectedDate: LocalDate,
    val backupDays: List<LocalDate>,
    val habits: List<HabitDefinition> = emptyList(),
    val checkin: DailyCheckin? = null,
    val completions: Map<String, Boolean> = emptyMap(),
    val mood: Int? = null,
    val note: String = "",
    val message: String? = null,
) {
    fun expectedHabits(): List<HabitDefinition> = habits.filter { it.isExpectedOn(selectedDate) }
}

/**
 * Direct, revision-checked writes through the Phase 1 [DisciplineRepository] only. A day is the local
 * date at entry in the device zone; the repository captures the zone and rejects future/older days.
 */
@Suppress("TooManyFunctions") // One screen owns the day check-in and habit management.
internal class HabitCheckinViewModel(
    private val repository: DisciplineRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val state = MutableStateFlow(initialState())
    val uiState = state.asStateFlow()

    init {
        reload()
        startMidnightTimer()
    }

    private fun today(): LocalDate = clock.instant().atZone(zone()).toLocalDate()

    private fun range(): DisciplineDateRange {
        val day = today()
        return DisciplineDateRange(day.minusDays(BACKFILL_DAYS), day)
    }

    private fun windowDays(day: LocalDate): List<LocalDate> =
        (0..BACKFILL_DAYS.toInt()).map { day.minusDays(it.toLong()) }

    private fun initialState(): HabitCheckinUiState {
        val day = today()
        return HabitCheckinUiState(today = day, selectedDate = day, backupDays = windowDays(day))
    }

    /**
     * Re-derives today from the injected clock/zone. Called on app resume and at each local midnight
     * so the day chips, seven-day window and selection can never go stale. A selection that fell out
     * of the new window resets to the new today; recorded days are only read, never rewritten.
     */
    fun refreshToday() {
        val day = today()
        val current = state.value
        val windowStart = day.minusDays(BACKFILL_DAYS)
        val fellOut = current.selectedDate.isBefore(windowStart) || current.selectedDate.isAfter(day)
        val selected = if (fellOut) day else current.selectedDate
        state.value = current.copy(
            today = day, selectedDate = selected, backupDays = windowDays(day), message = null,
        )
        execute { apply(read(), preserveDraft = !fellOut) }
    }

    private fun startMidnightTimer() {
        viewModelScope.launch {
            while (true) {
                val now = clock.instant()
                val zoned = zone()
                val nextMidnight = now.atZone(zoned).toLocalDate().plusDays(1).atStartOfDay(zoned).toInstant()
                delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L))
                refreshToday()
            }
        }
    }

    fun reload() {
        state.value = state.value.copy(loading = true, failed = false, message = null)
        execute { apply(read(), preserveDraft = false) }
    }

    fun selectDay(date: LocalDate) {
        if (state.value.busy) return
        val day = today()
        if (date.isAfter(day) || date.isBefore(day.minusDays(BACKFILL_DAYS))) {
            state.value = state.value.copy(message = BACKFILL_REJECTED)
            return
        }
        state.value = state.value.copy(
            today = day, selectedDate = date, backupDays = windowDays(day),
            conflict = false, saved = false, message = null,
        )
        execute { apply(read(), preserveDraft = false) }
    }

    fun selectDayText(value: String) {
        val date = try {
            DisciplineDates.parse(value.trim())
        } catch (_: Exception) {
            null
        }
        if (date == null) {
            state.value = state.value.copy(message = BACKFILL_REJECTED)
            return
        }
        selectDay(date)
    }

    fun toggleTick(habitId: String, completed: Boolean) = execute {
        val current = state.value
        val checkin = current.checkin
        if (checkin == null) {
            repository.saveCheckin(
                DailyCheckin(current.selectedDate, zone().id, current.mood, current.note.trim().ifBlank { null }),
                mapOf(habitId to completed),
            )
        } else {
            repository.setTick(checkin.metadata.id, checkin.metadata.revision, habitId, completed)
        }
        apply(read(), preserveDraft = true)
    }

    fun setMood(mood: Int?) {
        if (state.value.busy) return
        state.value = state.value.copy(mood = mood, message = null)
    }

    fun setNote(note: String) {
        if (state.value.busy) return
        state.value = state.value.copy(note = note.take(NOTE_LIMIT), message = null)
    }

    fun saveCheckin() = execute {
        val current = state.value
        val note = current.note.trim().ifBlank { null }
        val existing = current.checkin
        val checkin = if (existing == null) DailyCheckin(current.selectedDate, zone().id, current.mood, note)
        else existing.copy(mood = current.mood, note = note)
        repository.saveCheckin(checkin)
        apply(read(), preserveDraft = false)
    }

    fun createHabit(name: String, description: String?, cadence: HabitCadence, startText: String) = execute {
        val clean = name.trim()
        val start = validStart(startText)
        if (clean.isBlank() || start == null) {
            state.value = state.value.copy(message = START_REJECTED)
        } else {
            repository.saveHabit(HabitDefinition(clean, start, cadence, description?.trim()?.ifBlank { null }))
            state.value = state.value.copy(saved = true)
            apply(read(), preserveDraft = true)
        }
    }

    private fun validStart(startText: String): LocalDate? {
        val day = today()
        return runCatching { DisciplineDates.parse(startText.trim()) }.getOrNull()
            ?.takeIf { !it.isAfter(day) && !it.isBefore(day.minusDays(BACKFILL_DAYS)) }
    }

    fun archiveHabit(id: String) = execute {
        val habit = state.value.habits.first { it.metadata.id == id }
        repository.archiveHabit(id, habit.metadata.revision)
        state.value = state.value.copy(saved = true)
        apply(read(), preserveDraft = true)
    }

    fun restoreHabit(id: String) = execute {
        val habit = state.value.habits.first { it.metadata.id == id }
        repository.restoreHabit(id, habit.metadata.revision)
        state.value = state.value.copy(saved = true)
        apply(read(), preserveDraft = true)
    }

    fun deleteHabit(id: String) = execute {
        val habit = state.value.habits.first { it.metadata.id == id }
        repository.deleteHabit(id, habit.metadata.revision)
        state.value = state.value.copy(saved = true)
        apply(read(), preserveDraft = true)
    }

    private data class DaySnapshot(
        val habits: List<HabitDefinition>,
        val checkin: DailyCheckin?,
        val completions: Map<String, Boolean>,
    )

    private suspend fun read(): DaySnapshot {
        val window = range()
        val habits = repository.habits(window)
        val day = repository.checkins(window).firstOrNull { it.checkin.localDate == state.value.selectedDate }
        return DaySnapshot(habits, day?.checkin, day?.ticks?.associate { it.habitId to it.completed }.orEmpty())
    }

    private fun apply(snapshot: DaySnapshot, preserveDraft: Boolean) {
        val current = state.value
        state.value = current.copy(
            loading = false, failed = false, habits = snapshot.habits, checkin = snapshot.checkin,
            completions = snapshot.completions,
            mood = if (preserveDraft) current.mood else snapshot.checkin?.mood,
            note = if (preserveDraft) current.note else snapshot.checkin?.note.orEmpty(),
        )
    }

    private fun execute(block: suspend () -> Unit) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                block()
                state.value = state.value.copy(busy = false, loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RevisionConflictException) {
                reloadAfterConflict()
            } catch (_: IllegalArgumentException) {
                state.value = state.value.copy(busy = false, message = ARCHIVE_REJECTED)
            } catch (_: Exception) {
                // Key/storage failure: never substitute replacement data.
                state.value = state.value.copy(busy = false, loading = false, failed = true)
            }
        }
    }

    private suspend fun reloadAfterConflict() {
        try {
            val snapshot = read()
            state.value = state.value.copy(
                loading = false, busy = false, failed = false, conflict = true,
                habits = snapshot.habits, checkin = snapshot.checkin, completions = snapshot.completions,
                mood = snapshot.checkin?.mood, note = snapshot.checkin?.note.orEmpty(),
                message = CHECKIN_CONFLICT_MESSAGE,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.value = state.value.copy(busy = false, loading = false, failed = true)
        }
    }
}
