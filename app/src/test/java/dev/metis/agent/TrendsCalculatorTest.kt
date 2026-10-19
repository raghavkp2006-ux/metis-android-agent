@file:Suppress("MagicNumber") // Fixed observations exercise exact product thresholds.

package dev.metis.agent

import dev.metis.agent.domain.discipline.TrendDirection
import dev.metis.agent.domain.discipline.TrendWindow
import dev.metis.agent.domain.discipline.TrendsCalculator
import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendsCalculatorTest {
    private val today = LocalDate.of(2024, 3, 10)
    private val zone = ZoneOffset.UTC
    private fun calculator(date: LocalDate = today, zone: ZoneId = this.zone) = TrendsCalculator(
        Clock.fixed(date.atTime(12, 0).atZone(zone).toInstant(), zone), zone)
    private fun habit(start: LocalDate = today.minusDays(179), cadence: HabitCadence = HabitCadence.DAILY,
        archived: LocalDate? = null) = HabitDefinition("Read", start, cadence, archivedAt = archived)
    private fun checkin(date: LocalDate, mood: Int?, done: HabitDefinition? = null): CheckinDay {
        val record = DailyCheckin(date, zone.id, mood)
        return CheckinDay(record, done?.let { listOf(HabitTick(record.metadata.id, it.metadata.id, true)) }.orEmpty())
    }

    @Test fun windowsAreBoundedAndMissingMoodIsNeverZero() {
        val habit = habit()
        val records = listOf(checkin(today, 5, habit), checkin(today.minusDays(1), null, habit),
            checkin(today.minusDays(2), 3, habit), checkin(today.minusDays(3), 4, habit))
        val report = calculator().calculate(listOf(habit), records)
        assertEquals(180L, java.time.temporal.ChronoUnit.DAYS.between(calculator().range().from,
            calculator().range().through) + 1)
        assertEquals(7L, java.time.temporal.ChronoUnit.DAYS.between(
            report.periods.getValue(TrendWindow.WEEK).range.from, today) + 1)
        assertEquals(30L, java.time.temporal.ChronoUnit.DAYS.between(
            report.periods.getValue(TrendWindow.MONTH).range.from, today) + 1)
        assertEquals(90L, java.time.temporal.ChronoUnit.DAYS.between(
            report.periods.getValue(TrendWindow.QUARTER).range.from, today) + 1)
        assertEquals(3, report.periods.getValue(TrendWindow.WEEK).mood.sampleSize)
        assertEquals(4.0, report.periods.getValue(TrendWindow.WEEK).mood.value!!, 0.0)
        assertEquals(4.0 / 7.0, report.periods.getValue(TrendWindow.WEEK).completion.value!!, 0.0)
    }

    @Test fun meanAndDirectionUseExactMinimumAndHalfPointBoundary() {
        val mood = listOf(2, 2, 3, 3).mapIndexed { index, value ->
            checkin(today.minusDays((6 - index * 2).toLong()), value)
        }
        val week = calculator().calculate(emptyList(), mood).periods.getValue(TrendWindow.WEEK)
        assertEquals(2.5, week.mood.value!!, 0.0)
        assertEquals(TrendDirection.UP, week.direction)
        val exactHalf = listOf(2, 3, 3, 3).mapIndexed { index, value ->
            checkin(today.minusDays((6 - index * 2).toLong()), value)
        }
        assertEquals(TrendDirection.UP,
            calculator().calculate(emptyList(), exactHalf).periods.getValue(TrendWindow.WEEK).direction)
        val belowHalf = listOf(2, 3, 2, 3).mapIndexed { index, value ->
            checkin(today.minusDays((6 - index * 2).toLong()), value)
        }
        assertEquals(TrendDirection.FLAT,
            calculator().calculate(emptyList(), belowHalf).periods.getValue(TrendWindow.WEEK).direction)
        val below = calculator().calculate(emptyList(), mood.dropLast(1)).periods.getValue(TrendWindow.WEEK)
        assertEquals(7.0 / 3.0, below.mood.value!!, 0.0)
        assertEquals(TrendDirection.NOT_ENOUGH_DATA, below.direction)
    }

    @Test fun weekdayFourObservationBoundaryIsMondayFirst() {
        val monday = LocalDate.of(2024, 3, 4)
        val records = (0..3).map { checkin(monday.minusWeeks(it.toLong()), 4) }
        val four = calculator().calculate(emptyList(), records).weekdays
        assertEquals(DayOfWeek.MONDAY, four.first().day)
        assertEquals(4.0, four.first().mood.value!!, 0.0)
        assertEquals(4, four.first().mood.sampleSize)
        assertNull(calculator().calculate(emptyList(), records.dropLast(1)).weekdays.first().mood.value)
    }

    @Test fun habitAssociationRequiresFourOnBothSidesAndIsDescriptive() {
        val habit = habit()
        val records = (0..7).map { index -> checkin(today.minusDays(index.toLong()),
            if (index < 4) 4 else 2, if (index < 4) habit else null) }
        val report = calculator().calculate(listOf(habit), records)
        val association = report.associations.single()
        assertEquals(4, association.done.sampleSize)
        assertEquals(4, association.notDone.sampleSize)
        assertTrue(association.description.contains("tends to be higher"))
        assertTrue(association.description.contains("4 done days, 4 not done days"))
        assertTrue(calculator().calculate(listOf(habit), records.dropLast(1)).associations.isEmpty())
    }

    @Test fun cadenceAndArchiveUseSharedPhaseThreeScore() {
        val monday = LocalDate.of(2024, 3, 4)
        val habit = habit(monday.minusWeeks(3), HabitCadence.weekdays(setOf(DayOfWeek.MONDAY)),
            archived = LocalDate.of(2024, 3, 5))
        val records = (0..3).map { checkin(monday.minusWeeks(it.toLong()), 4, habit) }
        val period = calculator().calculate(listOf(habit), records).periods.getValue(TrendWindow.MONTH)
        assertEquals(4, period.completion.sampleSize)
        assertEquals(1.0, period.completion.value!!, 0.0)
        assertEquals(0.75, calculator().calculate(listOf(habit), records.dropLast(1))
            .periods.getValue(TrendWindow.MONTH).completion.value!!, 0.0)
        val onlyThreeMondays = HabitDefinition("Three", monday.minusWeeks(2),
            HabitCadence.weekdays(setOf(DayOfWeek.MONDAY)), archivedAt = LocalDate.of(2024, 3, 5))
        assertEquals(0.0, calculator().calculate(listOf(onlyThreeMondays), emptyList())
            .periods.getValue(TrendWindow.MONTH).completion.value!!, 0.0)
    }

    @Test fun previousEqualPeriodUsesIndependentSampleThresholds() {
        val habit = habit()
        val records = (0..59).map { offset -> checkin(today.minusDays(offset.toLong()),
            if (offset < 30) 4 else 2, if (offset < 30) habit else null) }
        val evolution = calculator().calculate(listOf(habit), records).evolution
        assertEquals(2.0, evolution.moodChange!!, 0.0)
        assertEquals(1.0, evolution.completionChange!!, 0.0)
        assertEquals(today.minusDays(59), evolution.previous.range.from)
        assertEquals(today.minusDays(30), evolution.previous.range.through)
        val sparse = calculator().calculate(listOf(habit), records.filter {
            it.checkin.localDate.isAfter(today.minusDays(30)) ||
                it.checkin.localDate == today.minusDays(30) ||
                it.checkin.localDate == today.minusDays(31) ||
                it.checkin.localDate == today.minusDays(32)
        }).evolution
        assertNull(sparse.moodChange)
        assertEquals(1.0, sparse.completionChange!!, 0.0)
    }

    @Test fun lowMoodRequiresFiveRecentLogsAndThreeLowValues() {
        val five = (0..4).map { checkin(today.minusDays(it.toLong()), if (it < 3) 2 else 4) }
        val message = calculator().calculate(emptyList(), five).insights.single { it.id == "low-mood" }
        assertEquals(5, message.sampleSize)
        assertTrue(message.message.contains("someone you trust or a professional"))
        assertFalse(calculator().calculate(emptyList(), five.dropLast(1)).insights.any { it.id == "low-mood" })
        assertFalse(calculator().calculate(emptyList(), five.mapIndexed { index, record ->
            if (index == 2) checkin(record.checkin.localDate, 3) else record
        }).insights.any { it.id == "low-mood" })
        assertFalse(calculator().calculate(emptyList(), five.mapIndexed { index, record ->
            checkin(today.minusDays((index * 4).toLong()), record.checkin.mood)
        }).insights.any { it.id == "low-mood" })
    }

    @Test fun everyTemplateAvoidsCausalAndDiagnosticWords() {
        val habit = habit()
        val records = (0..59).map { index -> checkin(today.minusDays(index.toLong()),
            if (index < 15) 2 else 4, habit) }
        val messages = calculator().calculate(listOf(habit), records).insights.map { it.message } +
            calculator().calculate(listOf(habit), records).associations.map { it.description }
        assertTrue(messages.isNotEmpty())
        val forbidden = Regex(
            "\\b(caused|causes|because|depression|anxiety|diagnosis|disorder)\\b",
            RegexOption.IGNORE_CASE,
        )
        assertTrue(messages.none { forbidden.containsMatchIn(it) })
    }

    @Test fun dstLeapAndYearEdgesKeepRecordedLocalDates() {
        val newYork = ZoneId.of("America/New_York")
        val clock = Clock.fixed(Instant.parse("2024-03-10T04:30:00Z"), ZoneOffset.UTC)
        val beforeDst = TrendsCalculator(clock, newYork)
        assertEquals(LocalDate.of(2024, 3, 9), beforeDst.today())
        val leap = LocalDate.of(2024, 2, 29)
        val report = beforeDst.calculate(emptyList(), listOf(checkin(leap, 5)), LocalDate.of(2024, 3, 9))
        assertTrue(report.periods.getValue(TrendWindow.MONTH).weeks.any { leap in it.from..it.through })
        val year = calculator(LocalDate.of(2025, 1, 2)).range()
        assertTrue(LocalDate.of(2024, 12, 31) in year.from..year.through)
        assertEquals(leap, checkin(leap, 5).checkin.localDate)
    }
}
