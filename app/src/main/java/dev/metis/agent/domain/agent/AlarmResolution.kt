package dev.metis.agent.domain.agent

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

internal object AlarmResolution {
    private val TIME_REGEX = Regex("(\\d{1,2})(?::(\\d{2}))? ?(am|pm)?", RegexOption.IGNORE_CASE)
    private const val DAY_BEFORE_GROUP = 1
    private const val TIME_GROUP = 2
    private const val DAY_AFTER_GROUP = 3
    private const val LABEL_GROUP = 4
    private const val PERIOD_GROUP = 3
    private const val TWELVE = 12
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59

    fun extract(request: AgentRequest, match: MatchResult, zone: ZoneId): List<ExtractedEntity> {
        val dayBefore = match.groups[DAY_BEFORE_GROUP]
        val timeGroup = match.groups[TIME_GROUP] ?: return emptyList()
        val dayAfter = match.groups[DAY_AFTER_GROUP]
        val labelGroup = match.groups[LABEL_GROUP]

        val dayString = dayBefore?.value ?: dayAfter?.value
        val start = dayBefore?.range?.first ?: timeGroup.range.first
        val end = (dayAfter ?: timeGroup).range.last + 1
        val rawTime = request.text.substring(start, end)

        val resolved = resolve(request, dayString, timeGroup.value, zone)
        val entities = mutableListOf<ExtractedEntity>()
        entities.add(
            ExtractedEntity(
                EntityKind.TIME,
                rawTime,
                start,
                end,
                1.0,
                resolved?.let { NormalizedValue.Time(it) }
            )
        )
        labelGroup?.let {
            entities.add(entity(EntityKind.TITLE, it))
        }
        return entities
    }

    @Suppress("ReturnCount")
    fun resolve(request: AgentRequest, day: String?, timeStr: String, zone: ZoneId): ResolvedTime? {
        val localTime = parseTime(timeStr) ?: return null
        val requestedDate = when {
            day.equals("tomorrow", true) -> request.timestamp.atZone(zone).toLocalDate().plusDays(1)
            day.equals("today", true) -> request.timestamp.atZone(zone).toLocalDate()
            day == null -> {
                val today = request.timestamp.atZone(zone).toLocalDate()
                val candidateLocal = LocalDateTime.of(today, localTime)
                val offsets = zone.rules.getValidOffsets(candidateLocal)
                if (offsets.size == 1 && candidateLocal.toInstant(offsets.single()) > request.timestamp) {
                    today
                } else {
                    today.plusDays(1)
                }
            }
            else -> return null
        }
        val local = LocalDateTime.of(requestedDate, localTime)
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.size != 1) return null
        val instant = local.toInstant(offsets.single())
        if (instant <= request.timestamp && day.equals("today", true)) return null
        return ResolvedTime(instant, local, zone)
    }

    @Suppress("ReturnCount")
    private fun parseTime(value: String): LocalTime? {
        val match = TIME_REGEX.matchEntire(value.trim()) ?: return null
        val hour = match.groupValues[DAY_BEFORE_GROUP].toInt()
        val minute = match.groupValues[TIME_GROUP].ifEmpty { "0" }.toInt()
        val period = match.groupValues[PERIOD_GROUP].lowercase(Locale.ROOT)
        if (minute !in 0..MAX_MINUTE) return null
        val resolvedHour = when {
            period.isNotEmpty() && hour in 1..TWELVE -> (hour % TWELVE) + if (period == "pm") TWELVE else 0
            period.isEmpty() && hour in 0..MAX_HOUR -> hour
            else -> return null
        }
        return LocalTime.of(resolvedHour, minute)
    }
}
