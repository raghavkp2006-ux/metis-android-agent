package dev.metis.agent

import dev.metis.agent.domain.storage.CalendarInterval
import dev.metis.agent.domain.storage.CalendarOverlap
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.ScheduleStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarOverlapTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-08T12:00:00Z"), ZoneOffset.UTC)
    private val utc = ZoneOffset.UTC

    @Test fun halfOpenBoundariesExcludeBackToBackEventsButIncludeOneMinuteOfOverlap() {
        val start = clock.millis()
        val first = CalendarInterval(start, start + HOUR)
        assertFalse(first.overlaps(CalendarInterval(start + HOUR, start + 2 * HOUR)))
        assertFalse(CalendarInterval(start - HOUR, start).overlaps(first))
        assertTrue(first.overlaps(CalendarInterval(start + HOUR - MINUTE, start + 2 * HOUR)))
        assertTrue(first.overlaps(CalendarInterval(start - HOUR, start + MINUTE)))
        assertTrue(first.overlaps(CalendarInterval(start, start + HOUR)))
    }

    @Test fun invalidAndZeroLengthIntervalsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CalendarInterval(clock.millis(), clock.millis()) }
        assertThrows(IllegalArgumentException::class.java) { CalendarInterval(clock.millis() + 1, clock.millis()) }
    }

    @Test fun allDayAndCrossMidnightEventsUseRealInstantBoundaries() {
        val day = LocalDate.of(2026, 4, 10)
        val allDay = event("All day", day.atStartOfDay(utc).toInstant(),
            day.plusDays(1).atStartOfDay(utc).toInstant())
        val crossing = event("Crossing", day.atTime(23, 30).toInstant(utc),
            day.plusDays(1).atTime(0, 30).toInstant(utc))
        assertEquals(listOf("Crossing"), CalendarOverlap.conflicts(allDay, listOf(crossing)).map { it.title })
        assertTrue(CalendarOverlap.overlapsDay(crossing, day.plusDays(1), utc))
        assertFalse(CalendarOverlap.overlapsDay(allDay, day.plusDays(1), utc))
    }

    @Test fun springAndAutumnAllDayDurationsAreTwentyThreeAndTwentyFiveHours() {
        val newYork = ZoneId.of("America/New_York")
        val spring = LocalDate.of(2026, 3, 8)
        val autumn = LocalDate.of(2026, 11, 1)
        fun interval(day: LocalDate) = CalendarInterval(day.atStartOfDay(newYork).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(newYork).toInstant().toEpochMilli())
        assertEquals(23 * HOUR, interval(spring).end - interval(spring).start)
        assertEquals(25 * HOUR, interval(autumn).end - interval(autumn).start)
        val springEvent = event("Spring", Instant.ofEpochMilli(interval(spring).start),
            Instant.ofEpochMilli(interval(spring).end), newYork)
        assertTrue(CalendarOverlap.overlapsDay(springEvent, spring, newYork))
        assertFalse(CalendarOverlap.overlapsDay(springEvent, spring.plusDays(1), newYork))
        assertTrue(CalendarOverlap.candidateWindow(springEvent).start < springEvent.startAt)
    }

    @Test fun storedZonesDoNotChangeInstantOverlap() {
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val kolkata = ZoneId.of("Asia/Kolkata")
        val instant = clock.instant()
        val first = event("Los Angeles", instant, instant.plusSeconds(3_600), losAngeles)
        val second = event("Kolkata", instant.plusSeconds(1_800), instant.plusSeconds(5_400), kolkata)
        assertEquals(listOf("Kolkata"), CalendarOverlap.conflicts(first, listOf(second)).map { it.title })
    }

    @Test fun cancelledAndUnacceptedItemsAreExcludedAndEditingExcludesSelf() {
        val instant = clock.instant()
        val current = event("Current", instant, instant.plusSeconds(3_600))
        val cancelled = event("Cancelled", instant, instant.plusSeconds(3_600), status = ScheduleStatus.CANCELLED)
        val proposed = event("Declined or archived", instant, instant.plusSeconds(3_600),
            status = ScheduleStatus.PROPOSED)
        val completed = event("Completed", instant, instant.plusSeconds(3_600),
            status = ScheduleStatus.COMPLETED)
        assertEquals(listOf("Completed"), CalendarOverlap.conflicts(current,
            listOf(current, cancelled, proposed, completed)).map { it.title })
        assertTrue(CalendarOverlap.conflicts(cancelled, listOf(current)).isEmpty())
        assertTrue(CalendarOverlap.conflicts(proposed, listOf(current)).isEmpty())
    }

    private fun event(title: String, start: Instant, end: Instant, zone: ZoneId = utc,
        status: ScheduleStatus = ScheduleStatus.ACCEPTED) =
        SavedSchedule(title, start.toEpochMilli(), end.toEpochMilli(), zone.id, "Synthetic event", status = status)

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
