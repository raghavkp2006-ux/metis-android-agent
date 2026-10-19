@file:Suppress("MagicNumber") // The five display bands and 53-week window are product rules.

package dev.metis.agent.domain.discipline

import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.HabitDefinition
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

const val CONTRIBUTION_WEEKS = 53
const val CONTRIBUTION_DAYS = CONTRIBUTION_WEEKS * 7

/** One local calendar date. Future dates have no score until they can be checked in. */
data class HabitContributionDay(
    val date: LocalDate,
    val expectedHabits: List<HabitDefinition>,
    val completedHabits: List<HabitDefinition>,
    val isFuture: Boolean,
) {
    val expectedCount: Int get() = expectedHabits.size
    val completedCount: Int get() = completedHabits.size
    val score: Double? get() = when {
        isFuture || expectedCount == 0 -> null
        else -> completedCount.toDouble() / expectedCount
    }
    val intensity: Int get() = intensityFor(score)
    val isStreakDay: Boolean get() = score != null && completedCount * 2 >= expectedCount

    companion object {
        /** 0 means no data or no completions; positive bands are >0–<25%, 25–<50%, 50–<75%, >=75%. */
        fun intensityFor(score: Double?): Int = when {
            score == null || score <= 0.0 -> 0
            score < 0.25 -> 1
            score < 0.50 -> 2
            score < 0.75 -> 3
            else -> 4
        }
    }
}

data class HabitContributionReport(
    val range: DisciplineDateRange,
    val today: LocalDate,
    val days: List<HabitContributionDay>,
    val currentStreak: Int,
    val longestStreak: Int,
    val qualifyingDays: Int,
    val totalExpectedCompletions: Int,
    val totalCompletedHabits: Int,
) {
    init { require(days.size == CONTRIBUTION_DAYS) }
}

/**
 * Pure, bounded contribution and streak rules. Dates come from one injected clock and zone; saved
 * records remain local dates and are never converted or rewritten when the device zone changes.
 */
class HabitContributionCalculator(
    private val clock: Clock,
    private val zone: ZoneId,
) {
    fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    /** Monday-first 53-week view ending on the Sunday of the current local week. */
    fun range(today: LocalDate = today()): DisciplineDateRange {
        val through = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return DisciplineDateRange(through.minusDays((CONTRIBUTION_DAYS - 1).toLong()), through)
    }

    fun calculate(
        habits: List<HabitDefinition>,
        checkins: List<CheckinDay>,
        today: LocalDate = today(),
    ): HabitContributionReport {
        val range = range(today)
        val checkinsByDate = checkins.associateBy { it.checkin.localDate }
        val days = buildList(CONTRIBUTION_DAYS) {
            var date = range.from
            while (!date.isAfter(range.through)) {
                val expected = habits.filter { it.isExpectedOn(date) }
                val doneIds = checkinsByDate[date]?.ticks.orEmpty()
                    .asSequence().filter { it.completed }.map { it.habitId }.toSet()
                val completed = expected.filter { it.metadata.id in doneIds }
                add(HabitContributionDay(date, expected, completed, date.isAfter(today)))
                date = date.plusDays(1)
            }
        }
        val measuredDays = days.filterNot { it.isFuture }
        val streak = calculateStreaks(measuredDays)
        return HabitContributionReport(
            range = range,
            today = today,
            days = days,
            currentStreak = streak.current,
            longestStreak = streak.longest,
            qualifyingDays = measuredDays.count { it.isStreakDay },
            totalExpectedCompletions = measuredDays.sumOf { it.expectedCount },
            totalCompletedHabits = measuredDays.sumOf { it.completedCount },
        )
    }

    private fun calculateStreaks(days: List<HabitContributionDay>): Streaks {
        var active = false
        var current = 0
        var longest = 0
        var lastRestDay: LocalDate? = null
        for (day in days) {
            if (day.expectedCount == 0) continue // No-data dates neither break nor consume a rest day.
            if (day.isStreakDay) {
                if (!active) {
                    active = true
                    current = 0
                    lastRestDay = null
                }
                current++
                longest = maxOf(longest, current)
            } else if (active) {
                val previousRest = lastRestDay
                if (previousRest == null || ChronoUnit.DAYS.between(previousRest, day.date) >= 7) {
                    // An expected day below 50% consumes one rest day; it keeps the run alive but
                    // does not increment the successful-day count. Rest becomes available again
                    // after seven calendar days, so every rolling seven-day window has at most one.
                    lastRestDay = day.date
                } else {
                    active = false
                    current = 0
                    lastRestDay = null
                }
            }
        }
        return Streaks(if (active) current else 0, longest)
    }

    private data class Streaks(val current: Int, val longest: Int)
}
