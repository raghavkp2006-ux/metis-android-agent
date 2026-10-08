package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "recommendations", primaryKeys = ["id"],
    indices = [Index(value = ["status", "expires_at"]), Index(value = ["entity_type", "entity_id"])],
)
data class RecommendationEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "score") val score: Float,
    @ColumnInfo(name = "score_components_json") val scoreComponentsJson: ByteArray,
    @ColumnInfo(name = "reason") val reason: ByteArray,
    @ColumnInfo(name = "evidence") val evidence: ByteArray,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "entity_type") val entityType: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
