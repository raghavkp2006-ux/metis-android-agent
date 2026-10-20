package dev.metis.agent

import androidx.lifecycle.ViewModelStore
import dev.metis.agent.domain.storage.CalendarConflict
import dev.metis.agent.domain.storage.CalendarOverlap
import dev.metis.agent.domain.storage.CalendarSaveResult
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.ScheduleRepository
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.presentation.navigation.CalendarEditorViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private val day = LocalDate.of(2026, 10, 20)
    private val repository = InMemoryCalendar()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun changeTimeKeepsDraftAndSavesOnlyAfterBoundaryIsClear() {
        val model = modelWithConflict()
        model.save()
        assertEquals(1, model.uiState.value.conflicts.size)
        assertEquals(1, repository.rows.value.size)
        model.changeTime()
        assertTrue(model.uiState.value.conflicts.isEmpty())
        val draft = requireNotNull(model.uiState.value.draft)
        model.change(draft.copy(start = "10:30", end = "11:30"))
        model.save()
        assertTrue(model.uiState.value.finished)
        assertEquals(2, repository.rows.value.size)
    }

    @Test fun saveAnywayCommitsAndCancelLeavesTheDatabaseUntouched() {
        val model = modelWithConflict()
        model.save()
        model.saveAnyway()
        assertTrue(model.uiState.value.finished)
        assertEquals(2, repository.rows.value.size)
        val saved = repository.rows.value.single { it.title == "New event" }
        assertEquals(1, CalendarOverlap.conflicts(saved, repository.rows.value).size)

        val cancelModel = CalendarEditorViewModel(repository, ZoneOffset.UTC)
        store.put("cancel", cancelModel)
        cancelModel.edit(day)
        cancelModel.change(requireNotNull(cancelModel.uiState.value.draft).copy(title = "Cancelled draft"))
        cancelModel.save()
        assertTrue(cancelModel.uiState.value.conflicts.isNotEmpty())
        cancelModel.cancel()
        assertFalse(cancelModel.uiState.value.finished)
        assertTrue(cancelModel.uiState.value.draft == null)
        assertEquals(2, repository.rows.value.size)
    }

    @Test fun newConflictBetweenWarningAndSaveAnywayRequiresAnotherChoice() {
        val model = modelWithConflict()
        model.save()
        assertEquals(listOf("Existing"), model.uiState.value.conflicts.map { it.title })
        repository.rows.value += event("Arrived later", "09:45", "10:15")
        model.saveAnyway()
        assertEquals(listOf("Existing", "Arrived later"), model.uiState.value.conflicts.map { it.title })
        assertEquals(2, repository.rows.value.size)
        assertEquals(2, repository.checks)
        model.saveAnyway()
        assertTrue(model.uiState.value.finished)
        assertEquals(3, repository.rows.value.size)
        assertEquals(3, repository.checks)
    }

    private fun modelWithConflict(): CalendarEditorViewModel {
        repository.rows.value = listOf(event("Existing", "09:30", "10:30"))
        val model = CalendarEditorViewModel(repository, ZoneOffset.UTC)
        store.put("editor", model)
        model.edit(day)
        model.change(requireNotNull(model.uiState.value.draft).copy(title = "New event"))
        return model
    }

    private fun event(title: String, start: String, end: String): SavedSchedule {
        fun instant(time: String) = day.atTime(java.time.LocalTime.parse(time)).toInstant(ZoneOffset.UTC)
        return SavedSchedule(title, instant(start).toEpochMilli(), instant(end).toEpochMilli(),
            ZoneOffset.UTC.id, "Synthetic", status = ScheduleStatus.ACCEPTED)
    }

    private class InMemoryCalendar : ScheduleRepository {
        val rows = MutableStateFlow<List<SavedSchedule>>(emptyList())
        var checks = 0
        override fun observeSchedules() = rows
        override suspend fun saveSchedule(schedule: SavedSchedule) {
            rows.value = rows.value.filterNot { it.metadata.id == schedule.metadata.id } + schedule
        }
        override suspend fun saveCalendarEvent(
            event: SavedSchedule, approved: List<CalendarConflict>?,
        ): CalendarSaveResult {
            checks++
            val conflicts = CalendarOverlap.conflicts(event, rows.value)
            if (conflicts.isNotEmpty() && conflicts != approved) return CalendarSaveResult.NeedsConfirmation(conflicts)
            saveSchedule(event)
            return CalendarSaveResult.Saved
        }
        override suspend fun deleteSchedule(id: String, revision: Long) {
            rows.value = rows.value.filterNot { it.metadata.id == id }
        }
    }
}
