package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "action_audit", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = ActionRunEntity::class, parentColumns = ["id"], childColumns = ["action_run_id"],
            onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["action_run_id"])],
)
data class ActionAuditEntity(
    val id: String,
    @ColumnInfo(name = "action_run_id") val actionRunId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "policy_version") val policyVersion: String,
    @ColumnInfo(name = "autonomy_level") val autonomyLevel: Int,
    @ColumnInfo(name = "permission_snapshot_json") val permissionSnapshotJson: ByteArray,
    @ColumnInfo(name = "decision") val decision: String,
    @ColumnInfo(name = "reason") val reason: ByteArray,
    @ColumnInfo(name = "evidence") val evidence: ByteArray,
    @ColumnInfo(name = "confirmation_id") val confirmationId: String?,
) : FoundationEntity {
    override fun recordMetadata() = StoredMetadata(id, timestamp, timestamp, 0)
}
