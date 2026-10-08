package dev.metis.agent.domain.storage

data class SavedEvent(
    val type: String,
    val source: String,
    val timestamp: Long,
    val importance: Float,
    val schemaVersion: Int,
    val entityType: String? = null,
    val entityId: String? = null,
    val payloadMetadata: String? = null,
    val requestId: String? = null,
    val actionId: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(createdAt = timestamp),
) : FoundationRecord {
    init {
        require(type in FoundationCatalog.EVENT_TYPES)
        require(source in setOf("USER", "AGENT", "ANDROID", "WORKER"))
        require(importance.isFinite() && importance in 0f..1f)
        require(schemaVersion == 1)
        entityType?.let { require(it in FoundationCatalog.ENTITY_TYPES) }
        entityId?.let { validateFoundationId(it) }
        payloadMetadata?.let { validateFoundationText(it) }
        requestId?.let { validateFoundationId(it) }
        actionId?.let { validateFoundationId(it) }
        require((entityType == null) == (entityId == null))
        require(metadata.createdAt == timestamp && metadata.updatedAt == timestamp && metadata.revision == 0L)
    }
}
