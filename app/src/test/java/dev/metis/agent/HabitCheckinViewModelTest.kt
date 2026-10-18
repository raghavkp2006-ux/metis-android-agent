package dev.metis.agent

import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.presentation.navigation.ARCHIVE_REJECTED
import dev.metis.agent.presentation.navigation.BACKFILL_REJECTED
import dev.metis.agent.presentation.navigation.CHECKIN_CONFLICT_MESSAGE
import dev.metis.agent.presentation.navigation.HabitCheckinViewModel
import dev.metis.agent.presentation.navigation.START_REJECTED
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitCheckinViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-03-10T09:00:00Z"), ZoneOffset.UTC)
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 3, 10)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun repo() = FakeDisciplineRepository(clock) { zone }

    private fun vm(repository: FakeDisciplineRepository) = HabitCheckinViewModel(repository, clock) { zone }

    @Test
    fun todayAndWindowComeFromTheInjectedZone() {
        val state = vm(repo()).uiState.value
        assertEquals(today, state.today)
        assertEquals((0..7).map { today.minusDays(it.toLong()) }, state.backupDays)
        assertFalse(state.loading)
        assertFalse(state.failed)
    }

    @Test
    fun futureAndOlderDaysAreRejected() {
        val model = vm(repo())
        model.selectDay(today.plusDays(1))
        assertEquals(BACKFILL_REJECTED, model.uiState.value.message)
        assertEquals(today, model.uiState.value.selectedDate)
        model.selectDay(today.minusDays(8))
        assertEquals(BACKFILL_REJECTED, model.uiState.value.message)
        model.selectDay(today.minusDays(3))
        assertEquals(today.minusDays(3), model.uiState.value.selectedDate)
        assertNull(model.uiState.value.message)
    }

    @Test
    fun manualDateParsingRejectsBadInput() {
        val model = vm(repo())
        model.selectDayText("not-a-date")
        assertEquals(BACKFILL_REJECTED, model.uiState.value.message)
        model.selectDayText(today.minusDays(2).toString())
        assertEquals(today.minusDays(2), model.uiState.value.selectedDate)
        assertNull(model.uiState.value.message)
    }

    @Test
    fun tickCreatesThenTogglesTheCheckin() {
        val model = vm(repo())
        model.createHabit("Read", null, HabitCadence.DAILY, today.toString())
        val habitId = model.uiState.value.habits.single().metadata.id
        model.toggleTick(habitId, true)
        assertNotNull(model.uiState.value.checkin)
        assertEquals(true, model.uiState.value.completions[habitId])
        model.toggleTick(habitId, false)
        assertEquals(false, model.uiState.value.completions[habitId])
    }

    @Test
    fun moodAndNotePersistAcrossReload() {
        val repository = repo()
        val model = vm(repository)
        model.createHabit("Read", null, HabitCadence.DAILY, today.toString())
        model.setMood(4)
        model.setNote("Felt good")
        model.saveCheckin()
        assertEquals(4, model.uiState.value.mood)
        assertEquals("Felt good", model.uiState.value.note)
        model.reload()
        assertEquals(4, model.uiState.value.mood)
        assertEquals("Felt good", model.uiState.value.note)
    }

    @Test
    fun expectedHabitsFollowCadence() {
        val model = vm(repo())
        model.createHabit("Daily", null, HabitCadence.DAILY, today.toString())
        model.createHabit("Today only", null, HabitCadence.weekdays(setOf(today.dayOfWeek)), today.toString())
        model.createHabit("Tomorrow only", null, HabitCadence.weekdays(setOf(today.dayOfWeek.plus(1))),
            today.toString())
        val expected = model.uiState.value.expectedHabits().map { it.name }
        assertEquals(listOf("Daily", "Today only"), expected)
    }

    @Test
    fun archiveExcludesFromExpectedAndRestoreCreatesNew() {
        val model = vm(repo())
        model.createHabit("Read", null, HabitCadence.DAILY, today.minusDays(1).toString())
        val id = model.uiState.value.habits.single().metadata.id
        model.archiveHabit(id)
        val archived = model.uiState.value.habits.single()
        assertFalse(archived.active)
        assertEquals(today, archived.archivedAt)
        assertTrue(model.uiState.value.expectedHabits().isEmpty())
        model.restoreHabit(id)
        assertEquals(2, model.uiState.value.habits.size)
        val restored = model.uiState.value.habits.first { it.metadata.id != id }
        assertEquals(today, restored.startLocalDate)
        model.deleteHabit(restored.metadata.id)
        assertEquals(1, model.uiState.value.habits.size)
    }

    @Test
    fun archiveOfSameDayHabitIsRejectedWithMessage() {
        val model = vm(repo())
        model.createHabit("Read", null, HabitCadence.DAILY, today.toString())
        val id = model.uiState.value.habits.single().metadata.id
        model.archiveHabit(id)
        assertEquals(ARCHIVE_REJECTED, model.uiState.value.message)
        assertTrue(model.uiState.value.habits.single().active)
    }

    @Test
    fun staleRevisionShowsTheSavedValueInsteadOfOverwriting() {
        val repository = repo()
        val model = vm(repository)
        model.createHabit("Read", null, HabitCadence.DAILY, today.toString())
        val habitId = model.uiState.value.habits.single().metadata.id
        model.toggleTick(habitId, true)
        val stored = repository.storedCheckin(today)
        assertNotNull(stored)
        runBlocking { repository.saveCheckin(stored!!.copy(mood = 5)) }
        model.toggleTick(habitId, false)
        val state = model.uiState.value
        assertTrue(state.conflict)
        assertEquals(CHECKIN_CONFLICT_MESSAGE, state.message)
        assertEquals(5, state.mood)
        assertEquals(true, state.completions[habitId])
        assertEquals(5, repository.storedCheckin(today)?.mood)
    }

    @Test
    fun keyFailureShowsFailedWithoutReplacementData() {
        val repository = repo()
        repository.failReads = true
        val state = vm(repository).uiState.value
        assertTrue(state.failed)
        assertFalse(state.loading)
        assertTrue(state.habits.isEmpty())
        assertNull(state.checkin)
    }

    @Test
    fun createRejectsStartOutsideTheWindow() {
        val model = vm(repo())
        model.createHabit("Read", null, HabitCadence.DAILY, today.plusDays(1).toString())
        assertEquals(START_REJECTED, model.uiState.value.message)
        model.createHabit("Read", null, HabitCadence.DAILY, "yesterday")
        assertEquals(START_REJECTED, model.uiState.value.message)
        assertTrue(model.uiState.value.habits.isEmpty())
    }

    @Test
    fun deleteRemovesTheHabit() {
        val model = vm(repo())
        model.createHabit("Read", null, HabitCadence.DAILY, today.toString())
        model.deleteHabit(model.uiState.value.habits.single().metadata.id)
        assertTrue(model.uiState.value.habits.isEmpty())
    }
}
