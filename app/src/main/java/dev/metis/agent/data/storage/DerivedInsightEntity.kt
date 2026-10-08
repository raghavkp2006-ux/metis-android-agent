package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "derived_insights", primaryKeys = ["id"],
    indices = [Index(value = ["kind", "computed_at"]), Index(value = ["entity_type", "entity_id"])],
)
data class DerivedInsightEntity(
    val id: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
    @ColumnInfo(name = "observation_start") val observationStart: Long,
    @ColumnInfo(name = "observation_end") val observationEnd: Long,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    @ColumnInfo(name = "method_version") val methodVersion: String,
    @ColumnInfo(name = "confidence") val confidence: Float,
    @ColumnInfo(name = "statistics_json") val statisticsJson: ByteArray?,
    @ColumnInfo(name = "source_watermark") val sourceWatermark: Long,
    @ColumnInfo(name = "entity_type") val entityType: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
) : FoundationEntity {
    override fun recordMetadata() = StoredMetadata(id, computedAt, computedAt, 0)
}
