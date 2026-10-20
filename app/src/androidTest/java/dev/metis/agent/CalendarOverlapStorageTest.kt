package dev.metis.agent

import dev.metis.agent.domain.storage.CalendarSaveResult
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.presentation.navigation.CALENDAR_REASON
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarOverlapStorageTest {
    private val day = LocalDate.of(2026, 10, 20)

    @Test fun realRoomRechecksConflictsInsideEachSaveTransaction(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val repository = fixture.repository
            repository.saveSchedule(event("First", 9 * HOUR + 15 * MINUTE, 9 * HOUR + 45 * MINUTE))
            val incoming = event("New", 9 * HOUR, 10 * HOUR)
            val first = repository.saveCalendarEvent(incoming) as CalendarSaveResult.NeedsConfirmation
            assertEquals(listOf("First"), first.conflicts.map { it.title })
            repository.saveSchedule(event("Second", 9 * HOUR + 30 * MINUTE, 10 * HOUR + 30 * MINUTE))
            val updated = repository.saveCalendarEvent(incoming, first.conflicts)
                as CalendarSaveResult.NeedsConfirmation
            assertEquals(listOf("First", "Second"), updated.conflicts.map { it.title })
            assertEquals(2, repository.observeSchedules().first().size)
            assertEquals(CalendarSaveResult.Saved, repository.saveCalendarEvent(incoming, updated.conflicts))
            assertEquals(3, repository.observeSchedules().first().size)
        }
    }

    @Test fun candidateOverflowFailsClosedWithoutWriting(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val repository = fixture.repository
            repeat(101) { index ->
                repository.saveSchedule(event("Synthetic $index", 9 * HOUR, 10 * HOUR))
            }
            assertEquals(CalendarSaveResult.TooManyCandidates,
                repository.saveCalendarEvent(event("Blocked", 9 * HOUR, 10 * HOUR)))
            assertEquals(101, repository.observeSchedules().first().size)
            assertTrue(repository.observeSchedules().first().none { it.title == "Blocked" })
        }
    }

    private fun event(title: String, start: Long, end: Long): SavedSchedule {
        val midnight = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return SavedSchedule(title, midnight + start, midnight + end, ZoneOffset.UTC.id,
            CALENDAR_REASON, status = ScheduleStatus.ACCEPTED)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
