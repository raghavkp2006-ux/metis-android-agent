package dev.metis.agent.domain.storage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Inclusive query window. 400 days covers the 53-week graph without an unbounded history read. */
data class DisciplineDateRange(val from: LocalDate, val through: LocalDate) {
    init {
        DisciplineDates.requireCanonical(from)
        DisciplineDates.requireCanonical(through)
        require(ChronoUnit.DAYS.between(from, through) in 0 until MAX_DAYS)
    }

    companion object { const val MAX_DAYS = 400L }
}

object DisciplineDates {
    private val pattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private const val BACKFILL_DAYS = 7L
    private const val MAX_YEAR = 9999

    fun parse(value: String): LocalDate {
        require(pattern.matches(value)) { "Expected yyyy-MM-dd" }
        return LocalDate.parse(value).also(::requireCanonical)
    }

    fun requireCanonical(date: LocalDate) { require(date.year in 1..MAX_YEAR) }

    fun requireWritable(date: LocalDate, at: Instant, zone: ZoneId) {
        requireCanonical(date)
        val today = at.atZone(zone).toLocalDate()
        require(!date.isBefore(today.minusDays(BACKFILL_DAYS)) && !date.isAfter(today)) {
            "Check-ins must be today or within the previous seven local days."
        }
    }
}
