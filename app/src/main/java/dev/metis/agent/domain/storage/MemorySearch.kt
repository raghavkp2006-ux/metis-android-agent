package dev.metis.agent.domain.storage

import java.util.UUID

enum class MemoryEntityType {
    TASK,
    SCHEDULE,
    PROJECT,
    GOAL,
    PERSON,
    ROUTINE,
    PREFERENCE,
    MEMORY,
    TASK_DEPENDENCY,
    USER_PROFILE,
    RELATIONSHIP,
    REMINDER,
    FOCUS_SESSION,
    EVENT,
    PROMISE,
    HABIT,
    ACTION_RUN,
    ACTION_AUDIT,
    AGENT_SESSION,
    RECOMMENDATION,
    EXPERIMENT,
    DERIVED_INSIGHT,
}

data class MemorySearchQuery(
    val text: String = "",
    val type: MemoryType? = null,
    val origin: MemoryOrigin? = null,
    val entityType: MemoryEntityType? = null,
    val entityId: String? = null,
    val at: Long = System.currentTimeMillis(),
    val limit: Int = 50,
    val candidateLimit: Int = DEFAULT_CANDIDATES,
    val relatedEntities: Set<MemoryReference> = emptySet(),
    val ranking: MemoryRankingPolicy = MemoryRankingPolicy(),
) {
    init {
        require((text.isEmpty() || text.isNotBlank()) && text.length <= MAX_SEARCH_LENGTH)
        require(relatedEntities.size <= MAX_RELATED_ENTITIES)
        require(limit in 1..MAX_SEARCH_RESULTS)
        require(candidateLimit in 1..MAX_CANDIDATES)
        require((entityType == null) == (entityId == null))
        entityId?.let { require(UUID.fromString(it).toString() == it) }
    }

    companion object {
        const val MAX_SEARCH_LENGTH = 200
        const val MAX_SEARCH_RESULTS = 100
        const val MAX_CANDIDATES = 200
        const val DEFAULT_CANDIDATES = 20
        const val MAX_RELATED_ENTITIES = 20
    }
}

data class MemorySearchResult(
    val memories: List<SavedMemory>,
    val candidateCount: Int,
    val candidatesTruncated: Boolean,
    val matchesTruncated: Boolean,
    val candidateLimit: Int = MemorySearchQuery.DEFAULT_CANDIDATES,
    val scores: List<MemoryScore> = emptyList(),
)

/** Literal Unicode words combined with AND. User text cannot introduce FTS operators or SQL. */
object MemorySearchText {
    fun expression(text: String): String? = Regex("[\\p{L}\\p{N}\\p{M}]+")
        .findAll(text).map { "\"${it.value}\"" }.joinToString(" ").ifEmpty { null }
}
