package dev.metis.agent.domain.storage

data class SavedRecommendation(
    val generatedAt: Long,
    val expiresAt: Long,
    val score: Float,
    val scoreComponentsJson: String,
    val reason: String,
    val evidence: String,
    val status: String,
    val entityType: String? = null,
    val entityId: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        require(score.isFinite() && score in 0f..1f)
        validateFoundationText(scoreComponentsJson)
        validateFoundationText(reason)
        validateFoundationText(evidence)
        require(status in setOf("SHOWN", "ACCEPTED", "REJECTED", "EXPIRED"))
        entityType?.let { require(it in FoundationCatalog.ENTITY_TYPES) }
        entityId?.let { validateFoundationId(it) }
        require((entityType == null) == (entityId == null))
        require(expiresAt > generatedAt)
    }
}
