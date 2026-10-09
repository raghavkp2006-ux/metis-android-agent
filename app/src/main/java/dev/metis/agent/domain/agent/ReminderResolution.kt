package dev.metis.agent.domain.agent

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Resolve only explicit dates and unambiguous times. Never guess an offset or roll a past time forward. */
internal object ReminderResolution {
    fun extract(request: AgentRequest, match: MatchResult, zone: ZoneId): List<ExtractedEntity> {
        val date = match.groups[1]
        val time = match.groups[2]!!
        val title = match.groups[TITLE_GROUP]
        val resolved = resolve(request, date?.value, time.value, zone)
        val start = date?.range?.first ?: time.range.first
        val end = time.range.last + 1
        return listOfNotNull(ExtractedEntity(EntityKind.TIME, request.text.substring(start, end), start, end,
            1.0, resolved?.let { NormalizedValue.Time(it) }), title?.let { entity(EntityKind.TITLE, it) })
    }

    @Suppress("ReturnCount") // Reject each unresolved boundary without normalization guesses.
    fun resolve(request: AgentRequest, day: String?, time: String, zone: ZoneId): ResolvedTime? {
        val date = date(request, day, zone) ?: return null
        val localTime = time(time) ?: return null
        val local = LocalDateTime.of(date, localTime)
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.size != 1) return null
        val instant = local.toInstant(offsets.single())
        if (instant <= request.timestamp) return null
        return ResolvedTime(instant, local, zone)
    }

    private fun date(request: AgentRequest, day: String?, zone: ZoneId): LocalDate? = when {
        day.equals("today", true) -> request.timestamp.atZone(zone).toLocalDate()
        day.equals("tomorrow", true) -> request.timestamp.atZone(zone).toLocalDate().plusDays(1)
        day != null -> runCatching { LocalDate.parse(day) }.getOrNull()
        else -> null
    }

    @Suppress("ReturnCount")
    private fun time(value: String): LocalTime? {
        val match = TIME.matchEntire(value) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        val period = match.groupValues[PERIOD_GROUP].lowercase(java.util.Locale.ROOT)
        if (minute !in 0..MAX_MINUTE) return null
        val resolvedHour = when {
            period.isNotEmpty() && hour in 1..TWELVE -> hour % TWELVE + if (period == "pm") TWELVE else 0
            period.isEmpty() && hour in 0..MAX_HOUR && match.groupValues[1].length == 2 &&
                match.groupValues[2].isNotEmpty() -> hour
            else -> return null
        }
        return LocalTime.of(resolvedHour, minute)
    }

    private val TIME = Regex("(\\d{1,2})(?::(\\d{2}))? ?(am|pm)?", RegexOption.IGNORE_CASE)
    private const val TWELVE = 12
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val TITLE_GROUP = 3
    private const val PERIOD_GROUP = 3
}
