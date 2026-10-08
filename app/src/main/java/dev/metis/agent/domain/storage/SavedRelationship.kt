package dev.metis.agent.domain.storage

data class SavedRelationship(
    val personId: String,
    val kind: String,
    val description: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationId(personId)
        require(kind in setOf("FAMILY", "FRIEND", "PARTNER", "COLLEAGUE", "OTHER"))
        description?.let { validateFoundationText(it) }
    }
}
