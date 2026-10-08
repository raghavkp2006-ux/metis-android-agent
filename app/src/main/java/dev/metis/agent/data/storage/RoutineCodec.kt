package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedRoutine

internal class RoutineCodec(private val codec: RecordCodec) : FoundationCodec<SavedRoutine, RoutineEntity> {
    override fun encode(record: SavedRoutine, metadata: StoredMetadata) = RoutineEntity(
        metadata,
        codec.encrypt(record.name, "routines", metadata.id, "name"),
        record.recurrenceRule,
        record.zoneId,
        codec.encryptJson(record.definition, "routines", metadata.id, "definition"),
        record.enabled,
    )

    override fun decode(row: RoutineEntity): SavedRoutine {
        val metadata = row.recordMetadata()
        return SavedRoutine(
            name = codec.decrypt(row.name, "routines", metadata.id, "name"),
            recurrenceRule = row.recurrenceRule,
            zoneId = row.zoneId,
            definition = codec.decryptJson(row.definition, "routines", metadata.id, "definition"),
            enabled = row.enabled,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
