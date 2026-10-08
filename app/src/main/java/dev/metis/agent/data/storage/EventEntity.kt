package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "events", primaryKeys = ["id"],
    indices = [Index(value = ["timestamp"]), Index(value = ["entity_type", "entity_id"])],
)
data class EventEntity(
    val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "importance") val importance: Float,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "entity_type") val entityType: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
    @ColumnInfo(name = "metadata") val payloadMetadata: ByteArray?,
    @ColumnInfo(name = "request_id") val requestId: String?,
    @ColumnInfo(name = "action_id") val actionId: String?,
) : FoundationEntity {
    override fun recordMetadata() = StoredMetadata(id, timestamp, timestamp, 0)
}
