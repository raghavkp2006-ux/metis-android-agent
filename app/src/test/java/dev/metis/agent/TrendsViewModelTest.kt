package dev.metis.agent

import androidx.lifecycle.ViewModelStore
import dev.metis.agent.domain.discipline.TrendWindow
import dev.metis.agent.presentation.navigation.TrendsViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private val clock = AdvancingClock(Instant.parse("2026-10-18T23:59:00Z"))
    private var zone: ZoneId = ZoneOffset.UTC
    private val repository = FakeDisciplineRepository(clock) { zone }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun model() = TrendsViewModel(repository, clock) { zone }.also { store.put("trends", it) }

    @Test fun refreshUsesCurrentClockAndZoneWithoutMovingStoredDays() {
        val model = model()
        assertEquals(LocalDate.of(2026, 10, 18), model.uiState.value.today)
        assertEquals(LocalDate.of(2026, 10, 18),
            model.uiState.value.report!!.periods.getValue(TrendWindow.MONTH).range.through)
        clock.now = Instant.parse("2026-10-19T00:01:00Z")
        model.refreshToday()
        assertEquals(LocalDate.of(2026, 10, 19), model.uiState.value.today)
        zone = ZoneId.of("America/Los_Angeles")
        model.refreshToday()
        assertEquals(LocalDate.of(2026, 10, 18), model.uiState.value.today)
        assertEquals(LocalDate.of(2026, 10, 18),
            model.uiState.value.report!!.periods.getValue(TrendWindow.MONTH).range.through)
    }

    @Test fun unreadableKeyClearsReportAndRetryRecovers() {
        val model = model()
        repository.failReads = true
        model.reload()
        assertTrue(model.uiState.value.failed)
        assertFalse(model.uiState.value.loading)
        assertNull(model.uiState.value.report)
        repository.failReads = false
        model.reload()
        assertFalse(model.uiState.value.failed)
        assertTrue(model.uiState.value.report != null)
        model.dismiss("completion")
        assertTrue("completion" in model.uiState.value.dismissed)
    }

    private class AdvancingClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
}
