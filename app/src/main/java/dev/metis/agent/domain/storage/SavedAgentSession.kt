package dev.metis.agent.domain.storage

data class SavedAgentSession(
    val startedAt: Long,
    val endedAt: Long? = null,
    val summary: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(createdAt = startedAt),
) : FoundationRecord {
    init {
        summary?.let { validateFoundationText(it) }
        require(endedAt == null || endedAt >= startedAt)
        require(metadata.createdAt == startedAt && metadata.updatedAt == startedAt && metadata.revision == 0L)
    }
}
