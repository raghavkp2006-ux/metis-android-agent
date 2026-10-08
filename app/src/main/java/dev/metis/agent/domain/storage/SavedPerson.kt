package dev.metis.agent.domain.storage

data class SavedPerson(
    val displayName: String,
    val phone: String? = null,
    val email: String? = null,
    val contactLookupKey: String? = null,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationText(displayName)
        phone?.let { validateFoundationText(it) }
        email?.let { validateFoundationText(it) }
        contactLookupKey?.let { validateFoundationText(it) }
    }
}
