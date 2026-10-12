package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.ReadTextV15
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemorySearchQuery
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class LocalScopedReadsV15(private val repository: LocalPersonalRepository) {
    suspend fun tasks(scope: ReadScopeV15.Tasks, now: Long, zone: ZoneId): SpecialistResult {
        val candidates = repository.scopedReads.tasks(scope, now, zone)
        val matches = candidates.records.filter { ReadTextV15.matches(it.title + " " + it.notes.orEmpty(),
            scope.subject) }
        return render("Matching saved tasks (${scope.status})", matches.map {
            "${it.status}: ${it.title}; due date: ${date(it.dueAt, zone)}; " +
                "completion date: ${date(it.completedAt, zone)}; record ${it.metadata.id}"
        }, candidates.truncated)
    }

    suspend fun promises(scope: ReadScopeV15.Promises, now: Long, zone: ZoneId): SpecialistResult {
        val (personId, ambiguous) = repository.scopedReads.person(scope.person)
        if (personId == null) return SpecialistResult.Clarification(
            if (ambiguous) "The saved recipient cannot be resolved uniquely within the lookup bound. " +
                "Resolve the saved person first."
            else "No saved person matches that name. Specify a saved person.", setOf("person"))
        val candidates = repository.scopedReads.promises(scope, personId, now, zone)
        val matches = candidates.records.filter { ReadTextV15.matches(it.content, scope.subject, scope.negative, true) }
        return render("Matching saved promises to ${scope.person}", matches.map {
            "${it.status}: ${it.content}; due date: ${date(it.dueAt, zone)}; record ${it.metadata.id}"
        }, candidates.truncated)
    }

    suspend fun memories(scope: ReadScopeV15.Memories, now: Long, zone: ZoneId): SpecialistResult {
        if (scope.date == null && scope.requestedField.isEmpty() && !scope.negative) {
            return SpecialistResult.Answer(LocalAgentReads(repository).memories(scope.subject, now))
        }
        val window = scope.date?.window(Instant.ofEpochMilli(now), zone)
        val result = repository.searchMemories(MemorySearchQuery(text = scope.subject, origin = MemoryOrigin.EXPLICIT,
            at = now, limit = MemorySearchQuery.DEFAULT_CANDIDATES, createdFrom = window?.first,
                createdUntil = window?.second))
        val matches = result.memories.filter { ReadTextV15.matches(it.content, scope.subject, scope.negative) }
        return render("Matching explicit saved memories", matches.map {
            val field = if (scope.requestedField.isEmpty()) "" else
                "${scope.requestedField}: ${field(it.content, scope.requestedField)}; "
            "$field${it.content}; record ${it.metadata.id}"
        }, result.candidatesTruncated || result.matchesTruncated)
    }

    suspend fun calendar(scope: ReadScopeV15.Calendar, now: Long, zone: ZoneId): SpecialistResult {
        scope.legacy?.let { return LocalCalendarReads(repository).answer(it, now, zone) }
        val window = scope.date?.window(Instant.ofEpochMilli(now), zone) ?:
            (Long.MIN_VALUE to Long.MAX_VALUE)
        val candidates = repository.calendarCandidates(window.first, window.second, MAX_CALENDAR + 1)
        val matches = candidates.take(MAX_CALENDAR).filter {
            val content = it.title + " " + it.reason
            (!scope.meetings || ReadTextV15.matches(content, "meeting") || ReadTextV15.matches(content, "meetings")) &&
                ReadTextV15.matches(content, scope.subject) &&
                (scope.person.isEmpty() || Regex("(?<![\\p{L}\\p{N}])with " +
                    "${Regex.escape(scope.person)}(?![\\p{L}\\p{N}])",
                    RegexOption.IGNORE_CASE).containsMatchIn(content))
        }
        val dates = if (scope.date == null) "all saved dates" else
            "${date(window.first, zone)} to ${date(window.second, zone)}"
        val label = "Matching accepted local calendar entries ($zone; $dates; other calendars excluded)"
        return render(label, matches.map {
            "${date(it.startAt, zone)} – ${date(it.endAt, zone)}: ${it.title}; record ${it.metadata.id}"
        }, candidates.size > MAX_CALENDAR)
    }

    private fun field(content: String, field: String): String {
        val pattern = if (field == "email address") EMAIL else NUMBER
        return pattern.findAll(content).map { it.value }.distinct().take(MAX_RESULTS).joinToString(", " +
            "").ifEmpty { "unavailable" }
    }

    private fun date(value: Long?, zone: ZoneId) = value?.let {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(it).atZone(zone))
    } ?: "unavailable"

    private fun render(label: String, rows: List<String>, incomplete: Boolean): SpecialistResult.Answer {
        if (rows.isEmpty()) return SpecialistResult.Answer(if (incomplete)
            "$label: no match within the search bound; results may be incomplete." else "$label: none found " +
                "on this device.")
        val truncated = incomplete || rows.size > MAX_RESULTS || rows.any { it.length > MAX_ROW }
        return SpecialistResult.Answer("$label:\n" + rows.take(MAX_RESULTS).joinToString("\n") { "• " +
            "${it.take(MAX_ROW)}" } +
            if (truncated) "\nShowing a bounded excerpt; more saved content may exist." else "")
    }

    companion object {
        private const val MAX_RESULTS = 5
        private const val MAX_ROW = 500
        private const val MAX_CALENDAR = 100
        private val EMAIL = Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val NUMBER = Regex("(?<![\\p{L}\\p{N}])\\+?\\d(?:[\\d ()-]{0,30}\\d)?(?![\\p{L}\\p{N}])")
    }
}
