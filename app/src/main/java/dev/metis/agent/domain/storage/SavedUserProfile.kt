package dev.metis.agent.domain.storage

data class SavedUserProfile(
    val displayName: String,
    val zoneId: String,
    val locale: String,
    val autonomyLevel: Int,
    val behavioralAnalysisConsent: Boolean,
    val active: Boolean,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationText(displayName)
        java.time.ZoneId.of(zoneId)
        validateFoundationText(locale)
        java.util.Locale.Builder().setLanguageTag(locale)
        require(autonomyLevel in 0..MAX_AUTONOMY_LEVEL)
        require(!behavioralAnalysisConsent)
        require(active)
    }
}
