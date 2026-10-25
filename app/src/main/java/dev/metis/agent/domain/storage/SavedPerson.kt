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
    override fun toString(): String = "SavedPerson(redacted)"

    companion object {
        /** The only projection of a newly selected provider contact into local storage. */
        fun fromContact(displayName: String, lookupKey: String, metadata: RecordMetadata): SavedPerson =
            SavedPerson(displayName = displayName, contactLookupKey = lookupKey, metadata = metadata)
    }
}
