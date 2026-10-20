package dev.metis.agent.domain.storage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stored instants define half-open intervals; display zones never change overlap membership. */
data class CalendarInterval(val start: Long, val end: Long) {
    init { require(end > start) { "The event end must follow its start." } }
    fun overlaps(other: CalendarInterval) = start < other.end && other.start < end
}

data class CalendarConflict(
    val id: String, val title: String, val startAt: Long, val endAt: Long, val zoneId: String,
)

sealed interface CalendarSaveResult {
    data object Saved : CalendarSaveResult
    data class NeedsConfirmation(val conflicts: List<CalendarConflict>) : CalendarSaveResult
    data object TooManyCandidates : CalendarSaveResult
}

object CalendarOverlap {
    fun conflicts(event: SavedSchedule, candidates: List<SavedSchedule>): List<CalendarConflict> {
        if (event.status !in ACTIVE_STATUSES) return emptyList()
        val interval = CalendarInterval(event.startAt, event.endAt)
        return candidates.asSequence()
            .filter { it.metadata.id != event.metadata.id &&
                it.status in ACTIVE_STATUSES }
            .filter { interval.overlaps(CalendarInterval(it.startAt, it.endAt)) }
            .map { CalendarConflict(it.metadata.id, it.title, it.startAt, it.endAt, it.zoneId) }
            .sortedWith(compareBy(CalendarConflict::startAt, CalendarConflict::endAt, CalendarConflict::id))
            .toList()
    }

    /** Include adjacent local days so cross-midnight and offset changes reach the candidate query. */
    fun candidateWindow(event: SavedSchedule): CalendarInterval {
        val zone = ZoneId.of(event.zoneId)
        val first = Instant.ofEpochMilli(event.startAt).atZone(zone).toLocalDate().minusDays(1)
        val last = Instant.ofEpochMilli(event.endAt - 1).atZone(zone).toLocalDate().plusDays(2)
        return CalendarInterval(first.atStartOfDay(zone).toInstant().toEpochMilli(),
            last.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun overlapsDay(event: SavedSchedule, day: LocalDate, zone: ZoneId): Boolean {
        val window = CalendarInterval(day.atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        return CalendarInterval(event.startAt, event.endAt).overlaps(window)
    }

    private val ACTIVE_STATUSES = setOf(ScheduleStatus.ACCEPTED, ScheduleStatus.COMPLETED)
}
