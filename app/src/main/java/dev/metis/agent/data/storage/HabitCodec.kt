package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedHabit

internal class HabitCodec(private val codec: RecordCodec) : FoundationCodec<SavedHabit, HabitEntity> {
    override fun encode(record: SavedHabit, metadata: StoredMetadata) = HabitEntity(
        metadata,
        codec.encrypt(record.name, "habits", metadata.id, "name"),
        codec.encryptJson(record.definition, "habits", metadata.id, "definition"),
        record.sampleCount,
        record.confidence,
        record.observationStart,
        record.observationEnd,
        record.methodVersion,
        record.consentRequired,
    )

    override fun decode(row: HabitEntity): SavedHabit {
        val metadata = row.recordMetadata()
        return SavedHabit(
            name = codec.decrypt(row.name, "habits", metadata.id, "name"),
            definition = codec.decryptJson(row.definition, "habits", metadata.id, "definition"),
            sampleCount = row.sampleCount,
            confidence = row.confidence,
            observationStart = row.observationStart,
            observationEnd = row.observationEnd,
            methodVersion = row.methodVersion,
            consentRequired = row.consentRequired,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
