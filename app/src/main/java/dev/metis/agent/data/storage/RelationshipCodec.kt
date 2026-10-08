package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedRelationship

internal class RelationshipCodec(private val codec: RecordCodec) : FoundationCodec<SavedRelationship,
    RelationshipEntity> {
    override fun encode(record: SavedRelationship, metadata: StoredMetadata) = RelationshipEntity(
        metadata,
        record.personId,
        record.kind,
        record.description?.let { codec.encrypt(it, "relationships", metadata.id, "description") },
    )

    override fun decode(row: RelationshipEntity): SavedRelationship {
        val metadata = row.recordMetadata()
        return SavedRelationship(
            personId = row.personId,
            kind = row.kind,
            description = row.description?.let { codec.decrypt(it, "relationships", metadata.id, "description") },
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
