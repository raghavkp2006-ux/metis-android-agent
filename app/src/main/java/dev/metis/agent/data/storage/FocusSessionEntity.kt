package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "focus_sessions", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["task_id"])],
)
data class FocusSessionEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "planned_seconds") val plannedSeconds: Long,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
