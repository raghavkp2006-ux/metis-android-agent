package dev.metis.agent.domain.planner

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class PlanningWindow(val span: TimeSpan, val zone: ZoneId, val assumed: Boolean)

/** Closed grammar and strict zone resolution. Overnight, past and ambiguous DST windows need clarification. */
object PlannerWindow {
    const val EXPRESSION = "plan my (day|evening)( tomorrow)?(?: from (\\d{2}:\\d{2}) to (\\d{2}:\\d{2}))?[!.?]?"
    private val pattern = Regex("\\s*(?:please )?$EXPRESSION\\s*", RegexOption.IGNORE_CASE)

    @Suppress("ReturnCount") // Reject unresolved temporal boundaries without guessing offsets or dates.
    fun resolve(text: String, now: Instant, zone: ZoneId, dayStart: LocalTime?): PlanningWindow? {
        val match = pattern.matchEntire(text) ?: return null
        val tomorrow = match.groupValues[2].isNotEmpty()
        val date = now.atZone(zone).toLocalDate().plusDays(if (tomorrow) 1 else 0)
        val explicit = match.groupValues[START_GROUP].isNotEmpty()
        val (start, end) = bounds(match, dayStart)
        if (start == null || end == null || end <= start) return null
        fun instant(time: LocalTime): Instant? {
            val local = date.atTime(time)
            val offsets = zone.rules.getValidOffsets(local)
            return offsets.singleOrNull()?.let { local.toInstant(it) }
        }
        val from = instant(start) ?: return null
        val until = instant(end) ?: return null
        val roundedNow = Instant.ofEpochMilli(
            (now.toEpochMilli() / MINUTE_MILLIS + START_BUFFER_MINUTES) * MINUTE_MILLIS)
        val actual = if (explicit || tomorrow) from else maxOf(from, roundedNow)
        if (actual <= now || actual >= until) return null
        return PlanningWindow(TimeSpan(actual.toEpochMilli(), until.toEpochMilli()), zone, !explicit)
    }

    private fun bounds(match: MatchResult, dayStart: LocalTime?): Pair<LocalTime?, LocalTime?> {
        if (match.groupValues[START_GROUP].isNotEmpty()) {
            return time(match.groupValues[START_GROUP]) to time(match.groupValues[END_GROUP])
        }
        val start = if (match.groupValues[1].equals("evening", true)) LocalTime.of(EVENING_HOUR, 0)
            else dayStart ?: LocalTime.of(DAY_HOUR, 0)
        return start to LocalTime.of(END_HOUR, 0)
    }

    private fun time(value: String) = runCatching { LocalTime.parse(value) }.getOrNull()
    private const val START_GROUP = 3
    private const val END_GROUP = 4
    private const val DAY_HOUR = 8
    private const val EVENING_HOUR = 18
    private const val END_HOUR = 22
    private const val MINUTE_MILLIS = 60_000L
    private const val START_BUFFER_MINUTES = 5
}
