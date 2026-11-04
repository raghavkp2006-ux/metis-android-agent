package dev.metis.agent.domain.agent

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Undated reminders use the request's local day; never guess an offset or roll a past time forward. */
internal object ReminderResolution {
    const val TIME_QUESTION = "What time?"

    @Suppress("ReturnCount")
    fun extract(request: AgentRequest, match: MatchResult, zone: ZoneId): List<ExtractedEntity> {
        val date = match.groups[1]
        val withoutTime = match.groups.size == TITLE_GROUP
        val time = if (withoutTime) null else match.groups[2]
        val title = match.groups[if (withoutTime) 2 else TITLE_GROUP]

        if (date != null && DurationParser.parseToSeconds(date.value) != null) {
            val resolved = resolve(request, null, date.value, zone)
            return listOfNotNull(
                ExtractedEntity(
                    EntityKind.TIME,
                    date.value,
                    date.range.first,
                    date.range.last + 1,
                    1.0,
                    resolved?.let { NormalizedValue.Time(it) }
                ),
                title?.let { entity(EntityKind.TITLE, it) }
            )
        }

        if (time == null) return listOfNotNull(date?.let {
            entity(EntityKind.TIME, it)
        }, title?.let { entity(EntityKind.TITLE, it) })
        val resolved = resolve(request, date?.value, time.value, zone)
        val start = date?.range?.first ?: time.range.first
        val end = time.range.last + 1
        return listOfNotNull(ExtractedEntity(EntityKind.TIME, request.text.substring(start, end), start, end,
            1.0, resolved?.let { NormalizedValue.Time(it) }), title?.let { entity(EntityKind.TITLE, it) })
    }

    @Suppress("ReturnCount") // Reject each unresolved boundary without normalization guesses.
    fun resolve(request: AgentRequest, day: String?, time: String, zone: ZoneId): ResolvedTime? {
        val durationSec = DurationParser.parseToSeconds(time)
            ?: day?.let { DurationParser.parseToSeconds(it) }
        if (durationSec != null) {
            val instant = request.timestamp.plusSeconds(durationSec)
            val local = LocalDateTime.ofInstant(instant, zone)
            val offsets = zone.rules.getValidOffsets(local)
            if (offsets.size != 1) return null
            return ResolvedTime(instant, local, zone)
        }
        val date = resolveDate(request, day, zone) ?: return null
        return resolveOnDate(request, date, time, zone)
    }

    @Suppress("ReturnCount") // Invalid time, DST, and past-time boundaries fail without normalization.
    fun resolveOnDate(request: AgentRequest, date: LocalDate, time: String, zone: ZoneId): ResolvedTime? {
        val localTime = time(time) ?: return null
        val local = LocalDateTime.of(date, localTime)
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.size != 1) return null
        val instant = local.toInstant(offsets.single())
        if (instant <= request.timestamp) return null
        return ResolvedTime(instant, local, zone)
    }

    fun resolveDate(request: AgentRequest, day: String?, zone: ZoneId): LocalDate? = when {
        day.equals("today", true) || day.equals("aaj", true) -> request.timestamp.atZone(zone).toLocalDate()
        day.equals("tomorrow", true) || day.equals("kal", true) ->
            request.timestamp.atZone(zone).toLocalDate().plusDays(1)
        day != null -> DayOfWeek.values().firstOrNull { it.name.equals(day, true) }
            ?.let { request.timestamp.atZone(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(it)) }
            ?: runCatching { LocalDate.parse(day) }.getOrNull()
        else -> request.timestamp.atZone(zone).toLocalDate()
    }

    /** Follow-ups accept a bare hour as 24-hour clock time, am/pm, and Hinglish expressions. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun followUpTime(value: String): String? {
        val lower = value.trim().lowercase(java.util.Locale.ROOT)
        val isMorning = lower.contains("subah") || lower.contains("morning")
        val isEvening = lower.contains("shaam") || lower.contains("raat") || lower.contains("evening") ||
            lower.contains("dopahar") || lower.contains("night") || lower.contains("afternoon")

        val cleaned = lower.replaceFirst(Regex("^at\\s+"), "")
            .replace(Regex("(?i)\\s*baje\\b"), "")
            .replace(Regex("(?i)\\b(?:subah|shaam|raat|dopahar|morning|evening|afternoon|night)\\b"), "")
            .trim()

        val bare = BARE_FOLLOW_UP_TIME.matchEntire(cleaned)
        if (bare != null) {
            val hour = bare.groupValues[1].toInt()
            val minute = bare.groupValues[2].ifEmpty { "00" }.toInt()
            if (hour !in 0..MAX_HOUR || minute !in 0..MAX_MINUTE) return null
            val period = if (isMorning) "am" else if (isEvening) "pm" else ""
            val resolvedHour = when {
                period.isNotEmpty() && hour in 1..TWELVE -> (hour % TWELVE) + if (period == "pm") TWELVE else 0
                else -> hour
            }
            return "%02d:%02d".format(java.util.Locale.ROOT, resolvedHour, minute)
        }

        val withPeriod = TIME.matchEntire(cleaned)
        if (withPeriod != null) {
            val hour = withPeriod.groupValues[1].toInt()
            val minute = withPeriod.groupValues[2].ifEmpty { "0" }.toInt()
            val period = withPeriod.groupValues[PERIOD_GROUP].ifEmpty {
                if (isMorning) "am" else if (isEvening) "pm" else ""
            }
            if (minute !in 0..MAX_MINUTE) return null
            val resolvedHour = when {
                period.isNotEmpty() && hour in 1..TWELVE -> (hour % TWELVE) + if (period == "pm") TWELVE else 0
                else -> hour
            }
            return "%02d:%02d".format(java.util.Locale.ROOT, resolvedHour, minute)
        }

        return cleaned.takeIf { time(it) != null }
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
    private val BARE_FOLLOW_UP_TIME = Regex("(\\d{1,2})(?::(\\d{2}))?")
    private const val TWELVE = 12
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val TITLE_GROUP = 3
    private const val PERIOD_GROUP = 3
}
