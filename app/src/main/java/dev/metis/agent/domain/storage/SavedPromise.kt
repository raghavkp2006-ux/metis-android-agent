package dev.metis.agent.domain.storage

data class SavedPromise(
    val personId: String,
    val content: String,
    val status: String,
    val taskId: String? = null,
    val dueAt: Long? = null,
    val sourceEventId: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationId(personId)
        validateFoundationText(content)
        require(status in setOf("OPEN", "FULFILLED", "CANCELLED"))
        taskId?.let { validateFoundationId(it) }
        sourceEventId?.let { validateFoundationId(it) }
    }
}
