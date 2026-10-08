package dev.metis.agent.domain.storage

data class SavedActionAudit(
    val actionRunId: String,
    val timestamp: Long,
    val policyVersion: String,
    val autonomyLevel: Int,
    val permissionSnapshotJson: String,
    val decision: String,
    val reason: String,
    val evidence: String,
    val confirmationId: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(createdAt = timestamp),
) : FoundationRecord {
    init {
        validateFoundationId(actionRunId)
        validateFoundationTag(policyVersion)
        require(autonomyLevel in 0..MAX_AUTONOMY_LEVEL)
        validateFoundationText(permissionSnapshotJson)
        require(decision in setOf("ALLOW", "DENY", "CONFIRM"))
        validateFoundationText(reason)
        validateFoundationText(evidence)
        confirmationId?.let { validateFoundationId(it) }
        require(metadata.createdAt == timestamp && metadata.updatedAt == timestamp && metadata.revision == 0L)
    }
}
