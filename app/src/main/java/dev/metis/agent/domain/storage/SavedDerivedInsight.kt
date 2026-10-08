package dev.metis.agent.domain.storage

data class SavedDerivedInsight(
    val kind: String,
    val computedAt: Long,
    val observationStart: Long,
    val observationEnd: Long,
    val sampleCount: Int,
    val methodVersion: String,
    val confidence: Float,
    val statisticsJson: String? = null,
    val sourceWatermark: Long,
    val entityType: String? = null,
    val entityId: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(createdAt = computedAt),
) : FoundationRecord {
    init {
        validateFoundationTag(kind)
        require(sampleCount >= 2)
        validateFoundationTag(methodVersion)
        require(confidence.isFinite() && confidence in 0f..1f)
        statisticsJson?.let { validateFoundationText(it) }
        entityType?.let { require(it in FoundationCatalog.ENTITY_TYPES) }
        entityId?.let { validateFoundationId(it) }
        require((entityType == null) == (entityId == null))
        require(observationEnd >= observationStart)
        require(metadata.createdAt == computedAt && metadata.updatedAt == computedAt && metadata.revision == 0L)
    }
}
