package dev.metis.agent.domain.storage

data class SavedFocusSession(
    val startedAt: Long,
    val plannedSeconds: Long,
    val outcome: String,
    val taskId: String? = null,
    val endedAt: Long? = null,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        require(plannedSeconds > 0)
        require(outcome in setOf("RUNNING", "COMPLETED", "CANCELLED", "INTERRUPTED"))
        taskId?.let { validateFoundationId(it) }
        require(endedAt == null || endedAt >= startedAt)
        require((outcome == "RUNNING") == (endedAt == null))
    }
}
