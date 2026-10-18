@file:Suppress("MagicNumber") // Scores, weekly rules and boundary dates are intentional test fixtures.

package dev.metis.agent

import dev.metis.agent.domain.discipline.HabitContributionCalculator
import dev.metis.agent.domain.discipline.HabitContributionDay
import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitContributionCalculatorTest {
    private val zone = ZoneOffset.UTC

    private fun calculator(today: LocalDate, zone: ZoneId = this.zone): HabitContributionCalculator =
        HabitContributionCalculator(Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone), zone)

    private fun habit(
        name: String,
        start: LocalDate,
        cadence: HabitCadence = HabitCadence.DAILY,
        archivedAt: LocalDate? = null,
    ) = HabitDefinition(name, start, cadence, archivedAt = archivedAt)

    private fun checkin(date: LocalDate, completed: List<HabitDefinition>, zoneId: String = zone.id): CheckinDay {
        val record = DailyCheckin(date, zoneId)
        return CheckinDay(record, completed.map { HabitTick(record.metadata.id, it.metadata.id, true) })
    }

    @Test
    fun localTodayAndHeatmapRangeComeFromFixedClockAndZone() {
        val instant = Instant.parse("2024-03-10T04:30:00Z")
        val pacific = ZoneId.of("America/Los_Angeles")
        val calculator = HabitContributionCalculator(Clock.fixed(instant, ZoneOffset.UTC), pacific)

        assertEquals(LocalDate.of(2024, 3, 9), calculator.today())
        val range = calculator.range()
        assertEquals(DayOfWeek.MONDAY, range.from.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, range.through.dayOfWeek)
        assertEquals(370L, ChronoUnit.DAYS.between(range.from, range.through))
    }

    @Test
    fun dayScoreUsesOnlyExpectedCompletedHabitsAndAppliesBands() {
        val today = LocalDate.of(2024, 3, 12)
        val habits = (1..4).map { habit("Habit $it", today.minusDays(400)) }
        val report = calculator(today).calculate(habits, listOf(checkin(today, habits.take(1))))
        val day = report.days.single { it.date == today }

        assertEquals(4, day.expectedCount)
        assertEquals(1, day.completedCount)
        assertEquals(0.25, day.score!!, 0.0)
        assertEquals(2, day.intensity)
        assertEquals(1, HabitContributionDay.intensityFor(0.01))
        assertEquals(2, HabitContributionDay.intensityFor(0.25))
        assertEquals(3, HabitContributionDay.intensityFor(0.50))
        assertEquals(4, HabitContributionDay.intensityFor(0.75))
        assertEquals(0, HabitContributionDay.intensityFor(0.0))
        assertEquals(0, HabitContributionDay.intensityFor(null))
    }

    @Test
    fun zeroCompletionIsZeroScoreButNoExpectedHabitsIsNoData() {
        val today = LocalDate.of(2024, 3, 10)
        val startsToday = habit("Starts today", today)
        val report = calculator(today).calculate(listOf(startsToday), emptyList())
        val previousDay = report.days.single { it.date == today.minusDays(1) }
        val currentDay = report.days.single { it.date == today }

        assertNull(previousDay.score)
        assertEquals(0, previousDay.intensity)
        assertEquals(0.0, currentDay.score!!, 0.0)
        assertEquals(0, currentDay.intensity)
    }

    @Test
    fun noDataAndFutureDatesNeverCountAsStreakFailures() {
        val today = LocalDate.of(2024, 3, 12)
        val weekdayHabit = habit("Weekdays", today.minusDays(30), HabitCadence.weekdays(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        ))
        val report = calculator(today).calculate(listOf(weekdayHabit), emptyList())
        val unscheduled = report.days.first { it.date.dayOfWeek == DayOfWeek.SUNDAY && !it.isFuture }
        val future = report.days.first { it.isFuture }

        assertNull(unscheduled.score)
        assertFalse(unscheduled.isStreakDay)
        assertNull(future.score)
        assertTrue(report.totalExpectedCompletions > 0)
        assertEquals(0, report.currentStreak)
    }

    @Test
    fun oneRestPerRollingSevenCalendarDaysKeepsRunButDoesNotAddToCount() {
        val today = LocalDate.of(2024, 1, 10)
        val start = today.minusDays(9)
        val daily = habit("Daily", start)
        val restDays = setOf(start.plusDays(1), start.plusDays(8))
        val checkins = (0..9).filter { start.plusDays(it.toLong()) !in restDays }
            .map { checkin(start.plusDays(it.toLong()), listOf(daily)) }
        val report = calculator(today).calculate(listOf(daily), checkins)

        assertEquals(8, report.currentStreak)
        assertEquals(8, report.longestStreak)
    }

    @Test
    fun secondRestSixDaysLaterBreaksRunAndStartsCurrentStreakAfterward() {
        val today = LocalDate.of(2024, 1, 15)
        val start = today.minusDays(14)
        val daily = habit("Daily", start)
        val restDays = setOf(start.plusDays(5), start.plusDays(11))
        val checkins = (0..14).filter { start.plusDays(it.toLong()) !in restDays }
            .map { checkin(start.plusDays(it.toLong()), listOf(daily)) }
        val report = calculator(today).calculate(listOf(daily), checkins)

        assertEquals(3, report.currentStreak)
        assertEquals(10, report.longestStreak)
    }

    @Test
    fun currentAndLongestStreakAreComputedSeparately() {
        val today = LocalDate.of(2024, 1, 11)
        val start = today.minusDays(10)
        val daily = habit("Daily", start)
        val rests = setOf(start.plusDays(7), start.plusDays(8))
        val checkins = (0..10).filter { start.plusDays(it.toLong()) !in rests }
            .map { checkin(start.plusDays(it.toLong()), listOf(daily)) }
        val report = calculator(today).calculate(listOf(daily), checkins)

        assertEquals(2, report.currentStreak)
        assertEquals(7, report.longestStreak)
        assertEquals(9, report.qualifyingDays)
    }

    @Test
    fun streakCountsScheduledDaysAndNoDataDatesDoNotConsumeRest() {
        val today = LocalDate.of(2024, 1, 15)
        val monday = LocalDate.of(2024, 1, 1)
        val mondays = habit("Mondays", monday, HabitCadence.weekdays(setOf(DayOfWeek.MONDAY)))
        val done = (0..2).map { checkin(monday.plusWeeks(it.toLong()), listOf(mondays)) }
        val report = calculator(today).calculate(listOf(mondays), done)

        assertEquals(3, report.currentStreak)
        assertEquals(3, report.longestStreak)
        assertEquals(3, report.qualifyingDays)
    }

    @Test
    fun cadenceAndExclusiveArchiveBoundarySetExpectedDenominator() {
        val today = LocalDate.of(2024, 3, 10)
        val monday = LocalDate.of(2024, 3, 4)
        val daily = habit("Daily", monday, archivedAt = LocalDate.of(2024, 3, 8))
        val weekdays = habit("Weekdays and Sunday", monday, HabitCadence.weekdays(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
        ))
        val report = calculator(today).calculate(listOf(daily, weekdays), emptyList())

        assertEquals(2, report.days.single { it.date == monday }.expectedCount)
        assertEquals(2, report.days.single { it.date == LocalDate.of(2024, 3, 7) }.expectedCount)
        assertEquals(1, report.days.single { it.date == LocalDate.of(2024, 3, 8) }.expectedCount)
        assertEquals(0, report.days.single { it.date == LocalDate.of(2024, 3, 9) }.expectedCount)
        assertEquals(1, report.days.single { it.date == LocalDate.of(2024, 3, 10) }.expectedCount)
    }

    @Test
    fun daylightSavingLeapDayAndYearEdgesUseLocalDateSteps() {
        val newYork = ZoneId.of("America/New_York")
        val springClock = Clock.fixed(Instant.parse("2024-03-10T04:30:00Z"), ZoneOffset.UTC)
        val spring = HabitContributionCalculator(springClock, newYork)
        assertEquals(LocalDate.of(2024, 3, 9), spring.today())
        val springStart = LocalDate.of(2024, 3, 10).atStartOfDay(newYork).toInstant()
        val nextStart = LocalDate.of(2024, 3, 11).atStartOfDay(newYork).toInstant()
        assertEquals(23L, ChronoUnit.HOURS.between(springStart, nextStart))
        val daily = habit("Spring", LocalDate.of(2024, 3, 9))
        val springReport = spring.calculate(
            listOf(daily), listOf(checkin(LocalDate.of(2024, 3, 9), listOf(daily), newYork.id),
                checkin(LocalDate.of(2024, 3, 10), listOf(daily), newYork.id)), LocalDate.of(2024, 3, 10),
        )
        assertTrue(springReport.days.any { it.date == LocalDate.of(2024, 3, 9) })
        assertTrue(springReport.days.any { it.date == LocalDate.of(2024, 3, 10) })

        val fallClock = Clock.fixed(Instant.parse("2024-11-03T04:30:00Z"), ZoneOffset.UTC)
        val fall = HabitContributionCalculator(fallClock, newYork)
        assertEquals(LocalDate.of(2024, 11, 3), fall.today())
        val fallStart = LocalDate.of(2024, 11, 3).atStartOfDay(newYork).toInstant()
        val followingStart = LocalDate.of(2024, 11, 4).atStartOfDay(newYork).toInstant()
        assertEquals(25L, ChronoUnit.HOURS.between(fallStart, followingStart))

        val leapDay = LocalDate.of(2024, 2, 29)
        val leapHabit = habit("Leap day", leapDay)
        val leapReport = calculator(LocalDate.of(2024, 3, 1)).calculate(
            listOf(leapHabit), listOf(checkin(leapDay, listOf(leapHabit))),
        )
        assertEquals(1.0, leapReport.days.single { it.date == leapDay }.score!!, 0.0)

        val newYearReport = calculator(LocalDate.of(2025, 1, 2)).calculate(emptyList(), emptyList())
        assertTrue(newYearReport.days.any { it.date == LocalDate.of(2024, 1, 1) })
        assertTrue(newYearReport.days.any { it.date == LocalDate.of(2024, 12, 31) })
        assertTrue(newYearReport.days.any { it.date == LocalDate.of(2025, 1, 1) })
    }

    @Test
    fun disciplineRangeAcceptsExactlyFourHundredDatesAndRejectsTheNext() {
        val through = LocalDate.of(2025, 1, 1)
        val from = through.minusDays(399)
        assertEquals(400L, ChronoUnit.DAYS.between(from, through) + 1)
        assertEquals(from, DisciplineDateRange(from, through).from)
        assertTrue(runCatching { DisciplineDateRange(through.minusDays(400), through) }.isFailure)
    }

    @Test
    fun viewIsExactlyFiftyThreeMondayFirstWeeksAndFutureDatesAreNeutral() {
        val today = LocalDate.of(2026, 10, 17)
        val report = calculator(today).calculate(emptyList(), emptyList())
        assertEquals(371, report.days.size)
        assertEquals(DayOfWeek.MONDAY, report.range.from.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, report.range.through.dayOfWeek)
        assertEquals(today.plusDays(7 - today.dayOfWeek.value.toLong()), report.range.through)
        assertTrue(report.days.takeWhile { !it.isFuture }.all { it.score == null })
        assertTrue(report.days.any { it.isFuture })
    }
}
