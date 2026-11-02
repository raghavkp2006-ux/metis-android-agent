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
        if (match.groups.size >= 5) {
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

        var dayString: String? = null
        var timeGroup: MatchGroup? = null
        for (i in 1 until match.groups.size) {
            val g = match.groups[i] ?: continue
            val v = g.value.trim()
            if (v.equals("today", true) || v.equals("tomorrow", true) || v.equals("kal", true) || v.equals("aaj", true)) {
                dayString = if (v.equals("kal", true)) "tomorrow" else if (v.equals("aaj", true)) "today" else v
            } else if (Regex("\\d").containsMatchIn(v) && timeGroup == null) {
                timeGroup = g
            }
        }
        if (timeGroup == null) return emptyList()
        val start = timeGroup.range.first
        val end = timeGroup.range.last + 1
        val rawTime = request.text.substring(start, end)
        val resolved = resolve(request, dayString, timeGroup.value, zone)
        return listOf(ExtractedEntity(EntityKind.TIME, rawTime, start, end, 1.0, resolved?.let { NormalizedValue.Time(it) }))
    }

    @Suppress("ReturnCount")
    fun resolve(request: AgentRequest, day: String?, timeStr: String, zone: ZoneId): ResolvedTime? {
        val localTime = parseTime(timeStr, request.text) ?: return null
        val requestedDate = when {
            day.equals("tomorrow", true) || day.equals("kal", true) -> request.timestamp.atZone(zone).toLocalDate().plusDays(1)
            day.equals("today", true) || day.equals("aaj", true) -> request.timestamp.atZone(zone).toLocalDate()
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
        if (instant <= request.timestamp && (day.equals("today", true) || day.equals("aaj", true))) return null
        return ResolvedTime(instant, local, zone)
    }

    @Suppress("ReturnCount")
    private fun parseTime(value: String, contextText: String = ""): LocalTime? {
        val cleaned = value.trim().replace(Regex("(?i)\\s*baje\\b"), "")
        val match = TIME_REGEX.matchEntire(cleaned) ?: return null
        val hour = match.groupValues[DAY_BEFORE_GROUP].toInt()
        val hasExplicitMinute = match.groupValues[TIME_GROUP].isNotEmpty()
        val minute = match.groupValues[TIME_GROUP].ifEmpty { "0" }.toInt()
        var period = match.groupValues[PERIOD_GROUP].lowercase(Locale.ROOT)
        if (minute !in 0..MAX_MINUTE) return null

        if (period.isEmpty()) {
            val lower = contextText.lowercase(Locale.ROOT)
            if (lower.contains("subah") || lower.contains("morning")) {
                period = "am"
            } else if (lower.contains("shaam") || lower.contains("raat") || lower.contains("dopahar") ||
                lower.contains("evening") || lower.contains("night") || lower.contains("afternoon")) {
                period = "pm"
            }
        }

        val resolvedHour = when {
            period.isNotEmpty() && hour in 1..TWELVE -> (hour % TWELVE) + if (period == "pm") TWELVE else 0
            period.isEmpty() && hour in 13..MAX_HOUR -> hour
            period.isEmpty() && hour == 0 -> 0
            period.isEmpty() && match.groupValues[DAY_BEFORE_GROUP].length == 2 &&
                hasExplicitMinute && hour in 0..MAX_HOUR &&
                (hour == 0 || match.groupValues[DAY_BEFORE_GROUP].startsWith("0")) -> hour
            period.isEmpty() && hasExplicitMinute && hour in 1..TWELVE -> hour
            else -> return null
        }
        return LocalTime.of(resolvedHour, minute)
    }
}
