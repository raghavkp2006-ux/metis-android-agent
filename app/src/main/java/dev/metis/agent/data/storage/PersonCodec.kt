package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedPerson

internal class PersonCodec(private val codec: RecordCodec) : FoundationCodec<SavedPerson, PersonEntity> {
    override fun encode(record: SavedPerson, metadata: StoredMetadata) = PersonEntity(
        metadata,
        codec.encrypt(record.displayName, "persons", metadata.id, "display_name"),
        record.phone?.let { codec.encrypt(it, "persons", metadata.id, "phone") },
        record.email?.let { codec.encrypt(it, "persons", metadata.id, "email") },
        record.contactLookupKey?.let { codec.encrypt(it, "persons", metadata.id, "contact_lookup_key") },
    )

    override fun decode(row: PersonEntity): SavedPerson {
        val metadata = row.recordMetadata()
        return SavedPerson(
            displayName = codec.decrypt(row.displayName, "persons", metadata.id, "display_name"),
            phone = row.phone?.let { codec.decrypt(it, "persons", metadata.id, "phone") },
            email = row.email?.let { codec.decrypt(it, "persons", metadata.id, "email") },
            contactLookupKey = row.contactLookupKey?.let { codec.decrypt(it, "persons", metadata.id,
                "contact_lookup_key") },
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
