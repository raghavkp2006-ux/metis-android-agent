package dev.metis.agent.domain.storage

data class SavedExperiment(
    val name: String,
    val hypothesis: String,
    val startedAt: Long,
    val consentedAt: Long,
    val definition: String,
    val status: String,
    val endedAt: Long? = null,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationText(name)
        validateFoundationText(hypothesis)
        validateFoundationText(definition)
        require(status in setOf("ACTIVE", "COMPLETED", "CANCELLED"))
        require(endedAt == null || endedAt >= startedAt)
        require(consentedAt <= startedAt)
    }
}
