package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedDerivedInsight

internal class DerivedInsightCodec(private val codec: RecordCodec) : FoundationCodec<SavedDerivedInsight,
    DerivedInsightEntity> {
    override fun encode(record: SavedDerivedInsight, metadata: StoredMetadata) = DerivedInsightEntity(
        metadata.id,
        record.kind,
        record.computedAt,
        record.observationStart,
        record.observationEnd,
        record.sampleCount,
        record.methodVersion,
        record.confidence,
        record.statisticsJson?.let { codec.encryptJson(it, "derived_insights", metadata.id, "statistics_json") },
        record.sourceWatermark,
        record.entityType,
        record.entityId,
    )

    override fun decode(row: DerivedInsightEntity): SavedDerivedInsight {
        val metadata = row.recordMetadata()
        return SavedDerivedInsight(
            kind = row.kind,
            computedAt = row.computedAt,
            observationStart = row.observationStart,
            observationEnd = row.observationEnd,
            sampleCount = row.sampleCount,
            methodVersion = row.methodVersion,
            confidence = row.confidence,
            statisticsJson = row.statisticsJson?.let { codec.decryptJson(it, "derived_insights", metadata.id,
                "statistics_json") },
            sourceWatermark = row.sourceWatermark,
            entityType = row.entityType,
            entityId = row.entityId,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
