package dev.metis.agent

import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.DisciplineDates
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DisciplineDomainTest {
    private val today = LocalDate.of(2026, 3, 10)

    @Test fun datesAreStrictCanonicalGregorianDates() {
        assertEquals(LocalDate.of(2024, 2, 29), DisciplineDates.parse("2024-02-29"))
        listOf("2026-02-29", "2026-02-30", "2026-3-10", "26-03-10", "0000-01-01",
            "2026-03-10T00:00", " 2026-03-10", "+10000-01-01").forEach { value ->
            assertThrows(RuntimeException::class.java) { DisciplineDates.parse(value) }
        }
    }

    @Test fun moodMustBeAnOptionalIntegerFromOneThroughFive() {
        (1..5).forEach { assertEquals(it, DailyCheckin(today, "UTC", mood = it).mood) }
        assertEquals(null, DailyCheckin(today, "UTC").mood)
        listOf(-1, 0, 6).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { DailyCheckin(today, "UTC", mood = value) }
        }
    }

    @Test fun invalidZonesAndOversizedPersonalTextAreRejected() {
        assertThrows(RuntimeException::class.java) { DailyCheckin(today, "Invalid/Zone") }
        assertThrows(IllegalArgumentException::class.java) { HabitDefinition(" ", today) }
        assertThrows(IllegalArgumentException::class.java) { HabitDefinition("x".repeat(201), today) }
        assertThrows(IllegalArgumentException::class.java) { HabitDefinition("Synthetic", today,
            description = "x".repeat(4001)) }
        assertThrows(IllegalArgumentException::class.java) { DailyCheckin(today, "UTC", note = "x".repeat(4001)) }
    }

    @Test fun weekdayBitsMapMondayThroughSundayAndDailyMatchesAllSeven() {
        DayOfWeek.entries.forEach { day ->
            val cadence = HabitCadence.weekdays(setOf(day))
            assertEquals(1 shl (day.value - 1), cadence.weekdayMask)
            DayOfWeek.entries.forEach { candidate -> assertEquals(candidate == day, cadence.matches(candidate)) }
            assertTrue(HabitCadence.DAILY.matches(day))
        }
        assertEquals(127, HabitCadence.weekdays(DayOfWeek.entries.toSet()).weekdayMask)
        assertThrows(IllegalArgumentException::class.java) { HabitCadence.weekdays(emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { HabitCadence(-1) }
        assertThrows(IllegalArgumentException::class.java) { HabitCadence(128) }
    }

    @Test fun expectedDaysIncludeStartExcludeArchiveAndMatchCadence() {
        val monday = LocalDate.of(2026, 3, 9)
        val habit = HabitDefinition("Synthetic", monday, HabitCadence.weekdays(setOf(DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY)), archivedAt = monday.plusDays(7))
        assertFalse(habit.active)
        assertFalse(habit.isExpectedOn(monday.minusDays(1)))
        assertTrue(habit.isExpectedOn(monday))
        assertFalse(habit.isExpectedOn(monday.plusDays(1)))
        assertFalse(habit.isExpectedOn(monday.plusDays(7)))
        assertEquals(2, habit.expectedDays(DisciplineDateRange(monday.minusDays(1), monday.plusDays(8))))
    }

    @Test fun archiveAtOrBeforeStartIsRejected() {
        listOf(today.minusDays(1), today).forEach { archive ->
            assertThrows(IllegalArgumentException::class.java) {
                HabitDefinition("Synthetic", today, archivedAt = archive)
            }
        }
    }

    @Test fun exactlySevenPreviousLocalDaysAndTodayAreWritable() {
        val at = Instant.parse("2026-03-10T12:00:00Z")
        val zone = ZoneId.of("UTC")
        (0L..7L).forEach { DisciplineDates.requireWritable(today.minusDays(it), at, zone) }
        listOf(today.minusDays(8), today.plusDays(1)).forEach { date ->
            assertThrows(IllegalArgumentException::class.java) { DisciplineDates.requireWritable(date, at, zone) }
        }
    }

    @Test fun entryDayUsesCapturedZoneAtTheSameInstant() {
        val at = Instant.parse("2026-03-10T00:30:00Z")
        DisciplineDates.requireWritable(today, at, ZoneId.of("Pacific/Auckland"))
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineDates.requireWritable(today, at, ZoneId.of("America/Los_Angeles"))
        }
    }

    @Test fun backfillUsesCalendarDaysAcrossDstRatherThanElapsedHours() {
        val zone = ZoneId.of("America/New_York")
        val at = Instant.parse("2026-03-10T04:30:00Z")
        val localToday = at.atZone(zone).toLocalDate()
        DisciplineDates.requireWritable(localToday.minusDays(7), at, zone)
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineDates.requireWritable(localToday.minusDays(8), at, zone)
        }
    }

    @Test fun springAndAutumnDstDaysEachCountAsOneExpectedDay() {
        listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1)).forEach { dstDay ->
            val habit = HabitDefinition("Synthetic", dstDay.minusDays(1), archivedAt = dstDay.plusDays(2))
            assertEquals(1, habit.expectedDays(DisciplineDateRange(dstDay, dstDay)))
            assertEquals(3, habit.expectedDays(DisciplineDateRange(dstDay.minusDays(1), dstDay.plusDays(1))))
        }
    }

    @Test fun monthYearAndLeapDayEdgesUseLocalDates() {
        val start = LocalDate.of(2024, 2, 28)
        assertEquals(3, HabitDefinition("Synthetic", start).expectedDays(DisciplineDateRange(start, start.plusDays(2))))
        val yearEnd = LocalDate.of(2025, 12, 31)
        assertEquals(2, HabitDefinition("Synthetic", yearEnd).expectedDays(
            DisciplineDateRange(yearEnd, LocalDate.of(2026, 1, 1))))
    }

    @Test fun rangesCannotBeReversedOrReadUnboundedHistory() {
        DisciplineDateRange(today, today.plusDays(399))
        assertThrows(IllegalArgumentException::class.java) { DisciplineDateRange(today, today.minusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { DisciplineDateRange(today, today.plusDays(400)) }
    }
}
