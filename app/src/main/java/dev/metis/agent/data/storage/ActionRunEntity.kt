package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "action_runs", primaryKeys = ["id"],
    indices = [Index(value = ["idempotency_key"], unique = true), Index(value = ["status",
        "started_at"]), Index(value = ["entity_type", "entity_id"])],
)
data class ActionRunEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "proposal_id") val proposalId: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "payload") val payload: ByteArray,
    @ColumnInfo(name = "risk") val risk: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "verification") val verification: String,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    @ColumnInfo(name = "receipt") val receipt: ByteArray?,
    @ColumnInfo(name = "safe_error_code") val safeErrorCode: String?,
    @ColumnInfo(name = "entity_type") val entityType: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
