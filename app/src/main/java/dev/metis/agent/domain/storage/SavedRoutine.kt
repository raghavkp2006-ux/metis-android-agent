package dev.metis.agent.domain.storage

data class SavedRoutine(
    val name: String,
    val recurrenceRule: String,
    val zoneId: String,
    val definition: String,
    val enabled: Boolean,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationText(name)
        validateFoundationText(recurrenceRule)
        java.time.ZoneId.of(zoneId)
        validateFoundationText(definition)
    }
}
