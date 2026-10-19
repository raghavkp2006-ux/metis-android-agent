@file:Suppress("MagicNumber", "TooManyFunctions") // Explicit product windows and sample thresholds.

package dev.metis.agent.domain.discipline

import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.HabitDefinition
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** All values are computed from Phase 1 records at read time. No note content is inspected. */
enum class TrendWindow(val days: Long) { WEEK(7), MONTH(30), QUARTER(90) }
enum class TrendDirection { UP, FLAT, DOWN, NOT_ENOUGH_DATA }
private const val MIN_EVOLUTION_SAMPLES = 4

data class TrendMean(val value: Double?, val sampleSize: Int)
data class TrendWeek(val from: LocalDate, val through: LocalDate, val mood: TrendMean, val completion: TrendMean)
data class TrendPeriod(
    val range: DisciplineDateRange,
    val mood: TrendMean,
    val completion: TrendMean,
    val direction: TrendDirection,
    val weeks: List<TrendWeek>,
)
data class WeekdayTrend(val day: DayOfWeek, val mood: TrendMean, val completion: TrendMean)
data class HabitMoodAssociation(
    val habitName: String,
    val done: TrendMean,
    val notDone: TrendMean,
) {
    val description: String get() = when {
        done.value == null || notDone.value == null -> "Not enough data"
        done.value > notDone.value -> "Mood tends to be higher on days you did $habitName"
        done.value < notDone.value -> "Mood tends to be lower on days you did $habitName"
        else -> "Mood was similar on days you did $habitName"
    } + " (${done.sampleSize} done days, ${notDone.sampleSize} not done days)."
}
data class Evolution(val current: TrendPeriod, val previous: TrendPeriod) {
    val moodChange: Double? get() = if (current.mood.sampleSize < MIN_EVOLUTION_SAMPLES ||
        previous.mood.sampleSize < MIN_EVOLUTION_SAMPLES) null else
        current.mood.value?.let { now -> previous.mood.value?.let { now - it } }
    val completionChange: Double? get() = current.completion.value?.let { now ->
        if (current.completion.sampleSize < MIN_EVOLUTION_SAMPLES ||
            previous.completion.sampleSize < MIN_EVOLUTION_SAMPLES) null
        else previous.completion.value?.let { now - it }
    }
}
data class TrendInsight(val id: String, val message: String, val range: DisciplineDateRange, val sampleSize: Int)
data class TrendsReport(
    val periods: Map<TrendWindow, TrendPeriod>,
    val weekdays: List<WeekdayTrend>,
    val associations: List<HabitMoodAssociation>,
    val evolution: Evolution,
    val insights: List<TrendInsight>,
)

/** Minimum four observations for published means, per weekday and on BOTH association sides. */
class TrendsCalculator(private val clock: Clock, private val zone: ZoneId) {
    private val contribution = HabitContributionCalculator(clock, zone)
    fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()
    fun range(today: LocalDate = today()) = DisciplineDateRange(today.minusDays(179), today)

    fun calculate(habits: List<HabitDefinition>, checkins: List<CheckinDay>, today: LocalDate = today()): TrendsReport {
        val readRange = range(today)
        require(checkins.all { it.checkin.localDate in readRange.from..readRange.through })
        val byDate = checkins.associateBy { it.checkin.localDate }
        val days = generateSequence(readRange.from) { if (it == today) null else it.plusDays(1) }
            .map { date -> Observation(date, byDate[date]?.checkin?.mood,
                contribution.day(date, habits, byDate[date], today)) }.toList()
        val periods = TrendWindow.entries.associateWith { window ->
            period(days.takeLast(window.days.toInt()))
        }
        val current = periods.getValue(TrendWindow.MONTH)
        val previous = period(days.takeLast(60).take(30))
        val recent = days.takeLast(90)
        val weekdays = DayOfWeek.entries.map { day ->
            val subset = recent.filter { it.date.dayOfWeek == day }
            WeekdayTrend(day, meanMood(subset.mapNotNull { it.mood }, MIN_SAMPLES),
                mean(subset.mapNotNull { it.day.score }, MIN_SAMPLES))
        }
        val associations = habits.mapNotNull { habit ->
            val applicable = recent.filter { observation ->
                habit.isExpectedOn(observation.date) && observation.mood != null
            }
            val done = applicable.filter { observation ->
                observation.day.completedHabits.any { it.metadata.id == habit.metadata.id }
            }
            val missed = applicable - done.toSet()
            val doneMean = meanMood(done.mapNotNull { it.mood }, MIN_SAMPLES)
            val missedMean = meanMood(missed.mapNotNull { it.mood }, MIN_SAMPLES)
            if (doneMean.value == null || missedMean.value == null) null
            else HabitMoodAssociation(habit.name, doneMean, missedMean)
        }
        val insights = insights(days, periods)
        return TrendsReport(periods, weekdays, associations, Evolution(current, previous), insights)
    }

    private fun period(days: List<Observation>): TrendPeriod {
        val range = DisciplineDateRange(days.first().date, days.last().date)
        val logged = days.filter { it.mood != null }
        val mid = days.size / 2
        val first = days.take(mid).mapNotNull { it.mood }
        val second = days.drop(mid).mapNotNull { it.mood }
        // Direction needs two logged observations in each half and an absolute change of 0.5.
        val direction = if (first.size < 2 || second.size < 2) TrendDirection.NOT_ENOUGH_DATA else {
            val delta = second.average() - first.average()
            when {
                delta >= 0.5 -> TrendDirection.UP
                delta <= -0.5 -> TrendDirection.DOWN
                else -> TrendDirection.FLAT
            }
        }
        val weeks = days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .map { (_, part) -> TrendWeek(part.first().date, part.last().date,
                meanMood(part.mapNotNull { it.mood }), mean(part.mapNotNull { it.day.score })) }
        return TrendPeriod(range, meanMood(logged.mapNotNull { it.mood }),
            mean(days.mapNotNull { it.day.score }), direction, weeks)
    }

    private fun insights(days: List<Observation>, periods: Map<TrendWindow, TrendPeriod>): List<TrendInsight> {
        val result = mutableListOf<TrendInsight>()
        val recent = days.takeLast(14).filter { it.mood != null }.takeLast(5)
        if (recent.size == 5 && recent.count { (it.mood ?: 5) <= 2 } >= 3) {
            result += TrendInsight("low-mood", "Your mood has been low on most of your last five logged days. " +
                "If it keeps up, consider talking to someone you trust or a professional.",
                DisciplineDateRange(recent.first().date, recent.last().date), recent.size)
        }
        val month = periods.getValue(TrendWindow.MONTH)
        if (month.direction == TrendDirection.DOWN && month.mood.value != null) {
            result += TrendInsight("mood-direction", "Your logged mood has been lower recently. " +
                "There may be many reasons; take the time you need.", month.range, month.mood.sampleSize)
        }
        val week = periods.getValue(TrendWindow.WEEK)
        if (week.completion.value != null && week.completion.value >= 0.75) {
            result += TrendInsight("completion", "You completed many of your planned habits this week.",
                week.range, week.completion.sampleSize)
        }
        return result
    }

    private fun mean(values: List<Double>, minimum: Int = 1): TrendMean = TrendMean(
        if (values.size >= minimum) values.average() else null, values.size)
    private fun meanMood(values: List<Int>, minimum: Int = 1): TrendMean =
        mean(values.map { it.toDouble() }, minimum)
    private data class Observation(val date: LocalDate, val mood: Int?, val day: HabitContributionDay)

    companion object {
        const val MIN_SAMPLES = 4
    }
}
