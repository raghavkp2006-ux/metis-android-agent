package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedEvent

internal class EventCodec(private val codec: RecordCodec) : FoundationCodec<SavedEvent, EventEntity> {
    override fun encode(record: SavedEvent, metadata: StoredMetadata) = EventEntity(
        metadata.id,
        record.type,
        record.source,
        record.timestamp,
        record.importance,
        record.schemaVersion,
        record.entityType,
        record.entityId,
        record.payloadMetadata?.let { codec.encryptJson(it, "events", metadata.id, "metadata") },
        record.requestId,
        record.actionId,
    )

    override fun decode(row: EventEntity): SavedEvent {
        val metadata = row.recordMetadata()
        return SavedEvent(
            type = row.type,
            source = row.source,
            timestamp = row.timestamp,
            importance = row.importance,
            schemaVersion = row.schemaVersion,
            entityType = row.entityType,
            entityId = row.entityId,
            payloadMetadata = row.payloadMetadata?.let { codec.decryptJson(it, "events", metadata.id, "metadata") },
            requestId = row.requestId,
            actionId = row.actionId,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
