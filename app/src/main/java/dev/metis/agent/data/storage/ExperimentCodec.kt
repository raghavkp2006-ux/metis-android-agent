package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedExperiment

internal class ExperimentCodec(private val codec: RecordCodec) : FoundationCodec<SavedExperiment, ExperimentEntity> {
    override fun encode(record: SavedExperiment, metadata: StoredMetadata) = ExperimentEntity(
        metadata,
        codec.encrypt(record.name, "experiments", metadata.id, "name"),
        codec.encrypt(record.hypothesis, "experiments", metadata.id, "hypothesis"),
        record.startedAt,
        record.consentedAt,
        codec.encryptJson(record.definition, "experiments", metadata.id, "definition"),
        record.status,
        record.endedAt,
    )

    override fun decode(row: ExperimentEntity): SavedExperiment {
        val metadata = row.recordMetadata()
        return SavedExperiment(
            name = codec.decrypt(row.name, "experiments", metadata.id, "name"),
            hypothesis = codec.decrypt(row.hypothesis, "experiments", metadata.id, "hypothesis"),
            startedAt = row.startedAt,
            consentedAt = row.consentedAt,
            definition = codec.decryptJson(row.definition, "experiments", metadata.id, "definition"),
            status = row.status,
            endedAt = row.endedAt,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
