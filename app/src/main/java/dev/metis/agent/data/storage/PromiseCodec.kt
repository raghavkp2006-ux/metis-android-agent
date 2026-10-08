package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedPromise

internal class PromiseCodec(private val codec: RecordCodec) : FoundationCodec<SavedPromise, PromiseEntity> {
    override fun encode(record: SavedPromise, metadata: StoredMetadata) = PromiseEntity(
        metadata,
        record.personId,
        codec.encrypt(record.content, "promises", metadata.id, "content"),
        record.status,
        record.taskId,
        record.dueAt,
        record.sourceEventId,
    )

    override fun decode(row: PromiseEntity): SavedPromise {
        val metadata = row.recordMetadata()
        return SavedPromise(
            personId = row.personId,
            content = codec.decrypt(row.content, "promises", metadata.id, "content"),
            status = row.status,
            taskId = row.taskId,
            dueAt = row.dueAt,
            sourceEventId = row.sourceEventId,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
