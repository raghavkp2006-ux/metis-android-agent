package dev.metis.agent

import androidx.lifecycle.ViewModelStore
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.presentation.navigation.HabitContributionViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HabitContributionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private val clock = AdvancingClock(Instant.parse("2026-10-18T23:59:00Z"))
    private var zone: ZoneId = ZoneOffset.UTC
    private val repository = FakeDisciplineRepository(clock) { zone }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model() = HabitContributionViewModel(repository, clock) { zone }.also {
        store.put("contribution", it)
    }

    @Test fun refreshReadsSavedTicksAndPreservesSelectedDate() = runBlocking {
        val today = LocalDate.of(2026, 10, 18)
        val habit = repository.saveHabit(HabitDefinition("Read", today))
        val model = model()
        assertEquals(0, model.uiState.value.report!!.totalCompletedHabits)
        model.selectDate(today.minusDays(1))
        repository.saveCheckin(DailyCheckin(today, zone.id), mapOf(habit.metadata.id to true))
        model.reload()
        assertEquals(1, model.uiState.value.report!!.totalCompletedHabits)
        assertEquals(1, model.uiState.value.report!!.currentStreak)
        assertEquals(today.minusDays(1), model.uiState.value.selectedDate)
    }

    @Test fun unreadableKeyHidesOldProgressAndRetryRecovers() {
        val model = model()
        repository.failReads = true
        model.reload()
        assertTrue(model.uiState.value.failed)
        assertFalse(model.uiState.value.loading)
        assertNull(model.uiState.value.report)
        repository.failReads = false
        model.reload()
        assertFalse(model.uiState.value.failed)
        assertEquals(371, model.uiState.value.report!!.days.size)
    }

    @Test fun resumeAcrossWeekAndZoneChangeRecomputesTodayAndBoundedRange() {
        val model = model()
        val previousStart = model.uiState.value.report!!.range.from
        model.selectDate(previousStart)
        clock.now = Instant.parse("2026-10-19T00:01:00Z")
        model.refreshToday()
        assertEquals(LocalDate.of(2026, 10, 19), model.uiState.value.today)
        assertEquals(previousStart.plusWeeks(1), model.uiState.value.report!!.range.from)
        assertEquals(model.uiState.value.today, model.uiState.value.selectedDate)
        zone = ZoneId.of("America/Los_Angeles")
        model.refreshToday()
        assertEquals(LocalDate.of(2026, 10, 18), model.uiState.value.today)
        assertEquals(previousStart, model.uiState.value.report!!.range.from)
        assertEquals(371, model.uiState.value.report!!.days.size)
    }

    @Test fun midnightTimerRefreshesWithoutResume() {
        val model = model()
        clock.now = Instant.parse("2026-10-19T00:00:01Z")
        dispatcher.scheduler.advanceTimeBy(60_001)
        dispatcher.scheduler.runCurrent()
        assertEquals(LocalDate.of(2026, 10, 19), model.uiState.value.report!!.today)
    }

    private class AdvancingClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
}
