package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedRecommendation

internal class RecommendationCodec(private val codec: RecordCodec) : FoundationCodec<SavedRecommendation,
    RecommendationEntity> {
    override fun encode(record: SavedRecommendation, metadata: StoredMetadata) = RecommendationEntity(
        metadata,
        record.generatedAt,
        record.expiresAt,
        record.score,
        codec.encryptJson(record.scoreComponentsJson, "recommendations", metadata.id, "score_components_json"),
        codec.encrypt(record.reason, "recommendations", metadata.id, "reason"),
        codec.encryptJson(record.evidence, "recommendations", metadata.id, "evidence"),
        record.status,
        record.entityType,
        record.entityId,
    )

    override fun decode(row: RecommendationEntity): SavedRecommendation {
        val metadata = row.recordMetadata()
        return SavedRecommendation(
            generatedAt = row.generatedAt,
            expiresAt = row.expiresAt,
            score = row.score,
            scoreComponentsJson = codec.decryptJson(row.scoreComponentsJson, "recommendations", metadata.id,
                "score_components_json"),
            reason = codec.decrypt(row.reason, "recommendations", metadata.id, "reason"),
            evidence = codec.decryptJson(row.evidence, "recommendations", metadata.id, "evidence"),
            status = row.status,
            entityType = row.entityType,
            entityId = row.entityId,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
