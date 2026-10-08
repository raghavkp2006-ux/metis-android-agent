package dev.metis.agent.domain.storage

data class SavedHabit(
    val name: String,
    val definition: String,
    val sampleCount: Int,
    val confidence: Float,
    val observationStart: Long,
    val observationEnd: Long,
    val methodVersion: String,
    val consentRequired: Boolean,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationText(name)
        validateFoundationText(definition)
        require(sampleCount >= 2)
        require(confidence.isFinite() && confidence in 0f..1f)
        validateFoundationTag(methodVersion)
        require(consentRequired)
        require(observationEnd >= observationStart)
    }
}
