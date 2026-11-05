package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.ReadTextV15
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemorySearchQuery
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

internal class LocalScopedReadsV15(
    private val repository: LocalPersonalRepository? = null,
    private val savedData: GraphSavedDataV18? = null,
) {
    constructor(repository: LocalPersonalRepository) : this(repository, LocalGraphSavedDataV18(repository))
    constructor(savedData: GraphSavedDataV18) : this(null, savedData)

    private val effectiveData: GraphSavedDataV18 get() = savedData ?: LocalGraphSavedDataV18(repository!!)

    suspend fun tasks(scope: ReadScopeV15.Tasks, now: Long, zone: ZoneId): SpecialistResult {
        val candidates = repository!!.scopedReads.tasks(scope, now, zone)
        val matches = candidates.records.filter { ReadTextV15.matches(it.title + " " + it.notes.orEmpty(),
            scope.subject) }
        return render("Matching saved tasks (${scope.status})", matches.map {
            "${it.status}: ${it.title}; due date: ${date(it.dueAt, zone)}; " +
                "completion date: ${date(it.completedAt, zone)}; record ${it.metadata.id}"
        }, candidates.truncated)
    }

    suspend fun promises(scope: ReadScopeV15.Promises, now: Long, zone: ZoneId): SpecialistResult {
        val (personId, ambiguous) = if (repository != null) {
            repository.scopedReads.person(scope.person)
        } else {
            val people = effectiveData.people().filter { it.displayName.equals(scope.person, true) }
            when (people.size) {
                0 -> null to false
                1 -> people.single().metadata.id to false
                else -> null to true
            }
        }
        if (personId == null) return SpecialistResult.Clarification(
            if (ambiguous) "The saved recipient cannot be resolved uniquely within the lookup bound. " +
                "Resolve the saved person first."
            else "No saved person matches that name. Specify a saved person.", setOf("person"))
        val candidates = if (repository != null) {
            repository.scopedReads.promises(scope, personId, now, zone)
        } else {
            val list = effectiveData.promises().filter { it.personId == personId }
            CandidateSliceV15(list, false)
        }
        val matches = candidates.records.filter { ReadTextV15.matches(it.content, scope.subject, scope.negative, true) }
        val tasksById = effectiveData.tasks().associateBy { it.metadata.id }
        val projectsById = effectiveData.projects().associateBy { it.metadata.id }
        return render("Matching saved promises to ${scope.person}", matches.map { promise ->
            val task = promise.taskId?.let { tasksById[it] }
            val project = task?.projectId?.let { projectsById[it] }
            val taskPart = if (task != null) "; linked task: ${task.title}" else ""
            val projectPart = if (project != null) "; project: ${project.title}" else ""
            "${promise.status}: ${promise.content}; due date: ${date(promise.dueAt, zone)}" +
                "$taskPart$projectPart; record ${promise.metadata.id}"
        }, candidates.truncated)
    }

    suspend fun memories(scope: ReadScopeV15.Memories, now: Long, zone: ZoneId): SpecialistResult {
        if (scope.date == null && scope.requestedField.isEmpty() && !scope.negative) {
            return SpecialistResult.Answer(LocalAgentReads(repository!!).memories(scope.subject, now))
        }
        val window = scope.date?.window(Instant.ofEpochMilli(now), zone)
        val result = repository!!.searchMemories(MemorySearchQuery(text = scope.subject, origin = MemoryOrigin.EXPLICIT,
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
        scope.legacy?.let { return LocalCalendarReads(repository!!).answer(it, now, zone) }
        val window = scope.date?.window(Instant.ofEpochMilli(now), zone) ?:
            (Long.MIN_VALUE to Long.MAX_VALUE)
        val candidates = repository!!.calendarCandidates(window.first, window.second, MAX_CALENDAR + 1)
        val matches = candidates.take(MAX_CALENDAR).filter {
            val content = it.title + " " + it.reason
            calendarKind(content, scope) &&
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

    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
    suspend fun projectStatus(scope: ReadScopeV15.ProjectStatus, now: Long, zone: ZoneId): SpecialistResult {
        val allProjects = effectiveData.projects()
        val candidates = allProjects.map {
            dev.metis.agent.domain.contacts.ContactCandidate(it.metadata.id, it.title)
        }
        val stripped = scope.project
            .replace(Regex("(?i)^project\\s+"), "")
            .replace(Regex("(?i)\\s+project$"), "")
            .trim()
        val project = allProjects.firstOrNull { it.title.equals(scope.project, ignoreCase = true) }
            ?: (if (stripped.isNotEmpty()) allProjects.firstOrNull {
                it.title.equals(stripped, ignoreCase = true)
            } else null)
            ?: run {
                when (val res = dev.metis.agent.domain.contacts.ContactMatcher.resolvePerson(
                    scope.project, candidates)) {
                    is dev.metis.agent.domain.contacts.ContactResolution.Resolved ->
                        allProjects.firstOrNull { it.metadata.id == res.contact.id }
                    is dev.metis.agent.domain.contacts.ContactResolution.Ambiguous ->
                        return SpecialistResult.Clarification(
                            "Multiple projects match '${scope.project}'. Which project did you mean?",
                            setOf("project"))
                    else -> if (stripped.isNotEmpty()) {
                        when (val res2 = dev.metis.agent.domain.contacts.ContactMatcher.resolvePerson(
                            stripped, candidates)) {
                            is dev.metis.agent.domain.contacts.ContactResolution.Resolved ->
                                allProjects.firstOrNull { it.metadata.id == res2.contact.id }
                            is dev.metis.agent.domain.contacts.ContactResolution.Ambiguous ->
                                return SpecialistResult.Clarification(
                                    "Multiple projects match '${scope.project}'. Which project did you mean?",
                                    setOf("project"))
                            else -> null
                        }
                    } else null
                }
            }
        if (project == null) {
            return SpecialistResult.Answer("Unknown project '${scope.project}'. Would you like to create one?")
        }

        val allTasks = effectiveData.tasks()
        val openTasks = allTasks.filter {
            it.projectId == project.metadata.id && it.status == dev.metis.agent.domain.storage.TaskStatus.OPEN
        }
        val projectTaskIds = allTasks.filter { it.projectId == project.metadata.id }.map { it.metadata.id }.toSet()

        val allPromises = effectiveData.promises()
        val openPromises = allPromises.filter {
            it.status == "OPEN" && it.taskId != null && it.taskId in projectTaskIds
        }

        val peopleById = effectiveData.people().associateBy { it.metadata.id }

        if (openTasks.isEmpty() && openPromises.isEmpty()) {
            return SpecialistResult.Answer("Project '${project.title}': nothing pending (unlinked promises excluded).")
        }

        data class PendingItem(val line: String, val dueAt: Long?)
        val items = mutableListOf<PendingItem>()

        for (task in openTasks) {
            val due = task.dueAt
            val reason = when {
                due != null && due < now -> "overdue since ${date(due, zone)}"
                due != null -> "due ${date(due, zone)}"
                else -> "no deadline"
            }
            items.add(PendingItem("• Task: ${task.title} ($reason)", due))
        }

        for (promise in openPromises) {
            val due = promise.dueAt
            val recipient = peopleById[promise.personId]?.displayName ?: "Unknown"
            val reason = when {
                due != null && due < now -> "overdue since ${date(due, zone)}"
                due != null -> "due ${date(due, zone)}"
                else -> "no deadline"
            }
            items.add(PendingItem("• Promise to $recipient: ${promise.content} ($reason)", due))
        }

        val sortedItems = items.sortedWith(
            compareBy<PendingItem> { it.dueAt == null }.thenBy { it.dueAt ?: Long.MAX_VALUE }
        )

        val totalOpenTasks = openTasks.size
        val totalOpenPromises = openPromises.size
        val allDueAts = (openTasks.mapNotNull { it.dueAt } + openPromises.mapNotNull { it.dueAt })
        val nearestDeadline = allDueAts.minOrNull()
        val nearestDeadlineStr = nearestDeadline?.let { date(it, zone) } ?: "none"
        val overdueCount = allDueAts.count { it < now }

        val summary = "Project '${project.title}' status: $totalOpenTasks open task(s), " +
            "$totalOpenPromises open promise(s) (unlinked promises excluded). " +
            "Overdue: $overdueCount. Nearest deadline: $nearestDeadlineStr."
        val body = sortedItems.joinToString("\n") { it.line }
        return SpecialistResult.Answer("$summary\n$body")
    }

    private fun field(content: String, field: String): String {
        val pattern = if (field == "email address") EMAIL else NUMBER
        return pattern.findAll(content).map { it.value }.distinct().take(MAX_RESULTS).joinToString(", " +
            "").ifEmpty { "unavailable" }
    }

    private fun calendarKind(content: String, scope: ReadScopeV15.Calendar): Boolean {
        val meeting = !scope.meetings || ReadTextV15.matches(content, "meeting") ||
            ReadTextV15.matches(content, "meetings")
        val appointment = !scope.appointments || ReadTextV15.matches(content, "appointment") ||
            ReadTextV15.matches(content, "appointments")
        return meeting && appointment
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
