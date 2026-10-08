package dev.metis.agent.domain.storage

data class SavedActionRun(
    val requestId: String,
    val proposalId: String,
    val idempotencyKey: String,
    val actionType: String,
    val payload: String,
    val risk: String,
    val status: String,
    val startedAt: Long,
    val verification: String,
    val finishedAt: Long? = null,
    val receipt: String? = null,
    val safeErrorCode: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationId(requestId)
        validateFoundationId(proposalId)
        validateFoundationId(idempotencyKey)
        require(actionType in FoundationCatalog.ACTION_TYPES)
        validateFoundationText(payload)
        require(risk in setOf("LOW", "MEDIUM", "HIGH", "CRITICAL"))
        require(status in setOf("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "HANDED_OFF", "UNKNOWN"))
        require(verification in setOf("VERIFIED_LOCAL", "VERIFIED_PLATFORM", "HANDOFF_ONLY", "UNVERIFIED"))
        receipt?.let { validateFoundationText(it) }
        safeErrorCode?.let { validateFoundationTag(it) }
        entityType?.let { require(it in FoundationCatalog.ENTITY_TYPES) }
        entityId?.let { validateFoundationId(it) }
        require((entityType == null) == (entityId == null))
        require(finishedAt == null || finishedAt >= startedAt)
        require(status != "SUCCEEDED" || (verification in setOf("VERIFIED_LOCAL", "VERIFIED_PLATFORM") &&
            receipt != null))
        require(status != "HANDED_OFF" || (verification == "HANDOFF_ONLY" && receipt != null))
        require(status !in setOf("SUCCEEDED", "FAILED", "CANCELLED", "HANDED_OFF") || finishedAt != null)
        require(status !in setOf("PENDING", "RUNNING") || verification == "UNVERIFIED")
    }
}
