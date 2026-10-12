@file:Suppress("MagicNumber") // Calendar hour and day boundaries are fixed civil-time values.

package dev.metis.agent.domain.agent

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Closed local-calendar forms. V12 still validates the entire original request first. */
data class CalendarReadScope(val kind: Kind, val day: Day = Day.NONE, val period: Period = Period.ALL,
                             val subject: String = "") {
    enum class Kind { EVENTS, PLANS, MEETINGS, APPOINTMENTS, MEETINGS_WITH_PERSON,
        MEETINGS_ABOUT_TOPIC, AVAILABILITY }
    enum class Day { NONE, TODAY, TOMORROW, THIS_WEEK }
    enum class Period { ALL, MORNING, AFTERNOON, EVENING }

    fun window(now: Instant, zone: ZoneId): Pair<Long, Long> {
        val today = now.atZone(zone).toLocalDate()
        val date = if (day == Day.TOMORROW) today.plusDays(1) else today
        if (day == Day.NONE) return now.toEpochMilli() to
            today.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val (start, end) = when (day) {
            Day.THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay() to
                today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay()
            else -> date.atTime(period.start()) to if (period in setOf(Period.ALL, Period.EVENING))
                date.plusDays(1).atStartOfDay() else date.atTime(period.end())
        }
        val lower = start.atZone(zone).toInstant().toEpochMilli()
        return (if (day == Day.THIS_WEEK) maxOf(lower, now.toEpochMilli()) else lower) to
            end.atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        private val person = Regex("have (?:i got )?any meetings with (?<name>.+?) this week", RegexOption.IGNORE_CASE)
        private val topic = Regex("when do i have to meet again concerning (?:the )?(?<topic>.+?)",
            RegexOption.IGNORE_CASE)
        private val punctuation = Regex("[?!.]$\\s*")
        private val prefix = Regex("^(?:please )?(?:(?:can|could|would) you (?:please )?)?",
            RegexOption.IGNORE_CASE)

        /** Only called after ReadSafetyV12 has approved the exact full request. */
        @Suppress("ReturnCount", "CyclomaticComplexMethod") // Each validated complete form maps to a distinct scope.
        fun parse(text: String): CalendarReadScope {
            val plain = text.trim().replace(prefix, "").replace(punctuation, "").trim()
            person.matchEntire(plain)?.let {
                return CalendarReadScope(Kind.MEETINGS_WITH_PERSON, Day.THIS_WEEK,
                    subject = it.groups["name"]!!.value)
            }
            topic.matchEntire(plain)?.let {
                return CalendarReadScope(Kind.MEETINGS_ABOUT_TOPIC, subject = it.groups["topic"]!!.value)
            }
            if (plain.startsWith("do i already have", true) && plain.contains("plans for", true)) {
                val day = if (plain.contains("tomorrow", true)) Day.TOMORROW else Day.TODAY
                val period = when {
                    plain.contains("morning", true) -> Period.MORNING
                    plain.contains("afternoon", true) -> Period.AFTERNOON
                    plain.contains("evening", true) -> Period.EVENING
                    else -> Period.ALL
                }
                return CalendarReadScope(Kind.PLANS, day, period)
            }
            if (plain.startsWith("when am i free", true)) return CalendarReadScope(Kind.AVAILABILITY,
                if (plain.contains("tomorrow", true)) Day.TOMORROW else Day.TODAY)
            if (plain.equals("which meetings are coming up next", true)) return CalendarReadScope(Kind.MEETINGS)
            if (Regex("\\bmeetings?\\b", RegexOption.IGNORE_CASE).containsMatchIn(plain))
                return CalendarReadScope(Kind.MEETINGS)
            if (Regex("\\bappointments?\\b", RegexOption.IGNORE_CASE).containsMatchIn(plain))
                return CalendarReadScope(Kind.APPOINTMENTS)
            return CalendarReadScope(Kind.EVENTS)
        }
    }
}

private fun CalendarReadScope.Period.start() = when (this) {
    CalendarReadScope.Period.ALL, CalendarReadScope.Period.MORNING -> LocalTime.MIDNIGHT
    CalendarReadScope.Period.AFTERNOON -> LocalTime.NOON
    CalendarReadScope.Period.EVENING -> LocalTime.of(18, 0)
}

private fun CalendarReadScope.Period.end() = when (this) {
    CalendarReadScope.Period.MORNING -> LocalTime.NOON
    CalendarReadScope.Period.AFTERNOON -> LocalTime.of(18, 0)
    CalendarReadScope.Period.EVENING, CalendarReadScope.Period.ALL -> LocalTime.MIDNIGHT
}
