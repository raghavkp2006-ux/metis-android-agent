package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.CalendarReadScope
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.TaskStatus
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** Local saved records only. Empty results never stand in for a key/storage failure. */
class LocalAgentReads private constructor(
    private val repository: LocalPersonalRepository?,
    private val savedData: GraphSavedDataV18?,
) : AgentReadPort {
    constructor(repository: LocalPersonalRepository) : this(repository, null)

    internal constructor(savedData: GraphSavedDataV18) : this(null, savedData)

    private val effectiveData: GraphSavedDataV18 get() = savedData ?: LocalGraphSavedDataV18(repository!!)

    override suspend fun tasks(scope: ReadScopeV15.Tasks, now: Long, zone: ZoneId) =
        LocalScopedReadsV15(repository, effectiveData).tasks(scope, now, zone)
    override suspend fun memories(scope: ReadScopeV15.Memories, now: Long, zone: ZoneId) =
        LocalScopedReadsV15(repository, effectiveData).memories(scope, now, zone)
    override suspend fun promises(scope: ReadScopeV15.Promises, now: Long, zone: ZoneId) =
        LocalScopedReadsV15(repository, effectiveData).promises(scope, now, zone)
    override suspend fun calendar(scope: ReadScopeV15.Calendar, now: Long, zone: ZoneId) =
        LocalScopedReadsV15(repository, effectiveData).calendar(scope, now, zone)
    override suspend fun projectStatus(scope: ReadScopeV15.ProjectStatus, now: Long, zone: ZoneId) =
        LocalScopedReadsV15(repository, effectiveData).projectStatus(scope, now, zone)
    override suspend fun calendar(scope: CalendarReadScope, now: Long, zone: ZoneId): SpecialistResult =
        LocalCalendarReads(repository!!).answer(scope, now, zone)
    override suspend fun tasks(): String {
        val tasks = effectiveData.tasks().filter { it.status == TaskStatus.OPEN }
        return render("Open saved tasks", tasks.map { it.title }, false)
    }

    override suspend fun memories(query: String, at: Long): String {
        val result = repository!!.searchMemories(MemorySearchQuery(text = query, origin = MemoryOrigin.EXPLICIT,
            at = at, limit = MAX_ROWS))
        return render("Matching explicit saved memories", result.memories.map { it.content },
            result.candidatesTruncated || result.matchesTruncated)
    }

    override suspend fun promises(person: String): SpecialistResult {
        val people = effectiveData.people().filter { it.displayName.equals(person, true) }
        if (people.size != 1) return SpecialistResult.Clarification(
            if (people.isEmpty()) "No saved person matches that name. Specify a saved person." else
                "Several saved people have that name. Resolve the person in saved records first.", setOf("person"))
        val personId = people.single().metadata.id
        val promises = effectiveData.promises().filter { it.personId == personId }
        val tasksById = effectiveData.tasks().associateBy { it.metadata.id }
        val projectsById = effectiveData.projects().associateBy { it.metadata.id }
        return SpecialistResult.Answer(render("Saved promises and status",
            promises.map { promise ->
                val task = promise.taskId?.let { tasksById[it] }
                val project = task?.projectId?.let { projectsById[it] }
                val taskPart = if (task != null) "; linked task: ${task.title}" else ""
                val projectPart = if (project != null) "; project: ${project.title}" else ""
                "${promise.status}: ${promise.content}$taskPart$projectPart"
            }, false))
    }

    private fun render(label: String, rows: List<String>, incomplete: Boolean): String {
        if (rows.isEmpty()) return if (incomplete) {
            "$label: no match within the search bound; results may be incomplete."
        } else "$label: none found on this device."
        val shown = rows.take(MAX_ROWS).joinToString("\n") { "• ${it.take(MAX_ROW_LENGTH)}" }
        val truncated = incomplete || rows.size > MAX_ROWS || rows.any { it.length > MAX_ROW_LENGTH }
        return "$label:\n$shown" + if (truncated) "\nShowing a bounded excerpt; more content may exist." else ""
    }

    private companion object {
        const val MAX_ROWS = 5
        const val MAX_ROW_LENGTH = 500
    }
}
