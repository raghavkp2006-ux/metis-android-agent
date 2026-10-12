package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.CalendarReadScope
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.SavedSchedule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Read only accepted local schedule blocks; never imply access to other calendars. */
internal class LocalCalendarReads(private val repository: LocalPersonalRepository) {
    suspend fun answer(scope: CalendarReadScope, now: Long, zone: ZoneId): SpecialistResult {
        val (from, until) = scope.window(Instant.ofEpochMilli(now), zone)
        val candidates = repository.calendarCandidates(from, until, MAX_CANDIDATES + 1)
        val boundReached = candidates.size > MAX_CANDIDATES
        val matches = candidates.take(MAX_CANDIDATES).filter { matches(it, scope) }
        val label = if (scope.kind == CalendarReadScope.Kind.AVAILABILITY) "Saved local commitments" else
            "Matching local calendar entries"
        if (matches.isEmpty()) return SpecialistResult.Answer(if (boundReached) {
            "$label: no match within the first $MAX_CANDIDATES accepted blocks; results may be incomplete. " +
                "Other calendars are not included."
        } else "$label: none found on this device. Other calendars are not included.")
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        val lines = matches.take(MAX_RESULTS).joinToString("\n") { event ->
            val start = formatter.format(Instant.ofEpochMilli(event.startAt).atZone(zone))
            val end = formatter.format(Instant.ofEpochMilli(event.endAt).atZone(zone))
            "• $start – $end: ${event.title.take(MAX_TITLE)}"
        }
        val incomplete = boundReached || matches.size > MAX_RESULTS ||
            matches.take(MAX_RESULTS).any { it.title.length > MAX_TITLE }
        return SpecialistResult.Answer("$label ($zone):\n$lines" +
            (if (incomplete) "\nShowing a bounded excerpt; more local content may exist." else "") +
            "\nOther calendars are not included.")
    }

    @Suppress("ReturnCount") // Each requested event type is checked before its literal subject.
    private fun matches(event: SavedSchedule, scope: CalendarReadScope): Boolean {
        if (scope.kind in setOf(CalendarReadScope.Kind.MEETINGS,
                CalendarReadScope.Kind.MEETINGS_WITH_PERSON, CalendarReadScope.Kind.MEETINGS_ABOUT_TOPIC) &&
            !containsPhrase(event, "meeting") && !containsPhrase(event, "meetings")) return false
        if (scope.kind == CalendarReadScope.Kind.APPOINTMENTS && !containsPhrase(event, "appointment") &&
            !containsPhrase(event, "appointments")) return false
        return when (scope.kind) {
            CalendarReadScope.Kind.MEETINGS_WITH_PERSON -> containsPhrase(event, "with ${scope.subject}")
            else -> scope.subject.isEmpty() || containsPhrase(event, scope.subject)
        }
    }

    private fun containsPhrase(event: SavedSchedule, phrase: String): Boolean {
        val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(event.title) || pattern.containsMatchIn(event.reason)
    }

    private companion object {
        const val MAX_CANDIDATES = 100
        const val MAX_RESULTS = 5
        const val MAX_TITLE = 160
    }
}
