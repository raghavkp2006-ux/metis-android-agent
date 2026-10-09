package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.TaskStatus
import kotlinx.coroutines.flow.first

/** Local saved records only. Empty results never stand in for a key/storage failure. */
class LocalAgentReads(private val repository: LocalPersonalRepository) : AgentReadPort {
    override suspend fun tasks(): String {
        val tasks = repository.observeTasks().first().filter { it.status == TaskStatus.OPEN }
        return render("Open saved tasks", tasks.map { it.title }, false)
    }

    override suspend fun memories(query: String, at: Long): String {
        val result = repository.searchMemories(MemorySearchQuery(text = query, origin = MemoryOrigin.EXPLICIT,
            at = at, limit = MAX_ROWS))
        return render("Matching explicit saved memories", result.memories.map { it.content },
            result.candidatesTruncated || result.matchesTruncated)
    }

    override suspend fun promises(person: String): SpecialistResult {
        val people = repository.foundation.person.observe().first().filter { it.displayName.equals(person, true) }
        if (people.size != 1) return SpecialistResult.Clarification(
            if (people.isEmpty()) "No saved person matches that name. Specify a saved person." else
                "Several saved people have that name. Resolve the person in saved records first.", setOf("person"))
        val personId = people.single().metadata.id
        val promises = repository.foundation.promise.observe().first().filter { it.personId == personId }
        return SpecialistResult.Answer(render("Saved promises and status",
            promises.map { "${it.status}: ${it.content}" }, false))
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
