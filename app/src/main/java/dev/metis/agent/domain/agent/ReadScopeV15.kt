package dev.metis.agent.domain.agent

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Typed authority reconstructed from the complete original request, never classifier output. */
sealed interface ReadScopeV15 {
    data class Tasks(val subject: String = "", val status: String = "OPEN", val date: ReadDateV15? = null,
                     val requestedField: String = "") : ReadScopeV15
    data class Memories(val subject: String, val date: ReadDateV15? = null,
                        val requestedField: String = "", val negative: Boolean = false) : ReadScopeV15
    data class Promises(val person: String, val subject: String = "", val status: String = "ANY",
                        val date: ReadDateV15? = null, val negative: Boolean = false) : ReadScopeV15
    data class Calendar(val subject: String = "", val person: String = "", val date: ReadDateV15? = null,
                        val meetings: Boolean = false, val legacy: CalendarReadScope? = null,
                        val appointments: Boolean = false) : ReadScopeV15
}

/** Local date semantics stay symbolic until one request clock and zone are captured. */
data class ReadDateV15(val field: Field, val expression: String) {
    enum class Field { DUE, COMPLETED, CREATED, START }
    init { require(valid(expression)) }

    fun window(now: Instant, zone: ZoneId): Pair<Long, Long> {
        val today = now.atZone(zone).toLocalDate()
        val start = when (expression.lowercase()) {
            "today" -> today
            "tomorrow" -> today.plusDays(1)
            "this week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            else -> LocalDate.parse(expression)
        }
        val end = start.plusDays(if (expression.equals("this week", true)) DAYS_PER_WEEK else 1)
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to end.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    companion object {
        private const val DAYS_PER_WEEK = 7L
        fun valid(value: String): Boolean = value.lowercase() in setOf("today", "tomorrow", "this week") ||
            (Regex("\\d{4}-\\d{2}-\\d{2}").matches(value) && runCatching { LocalDate.parse(value) }.isSuccess)
    }
}

data class ReadPlanV15(val parsed: LanguageParse, val scope: ReadScopeV15)
