package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "promises", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["source_event_id"],
            onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["person_id", "status", "due_at"]), Index(value = ["task_id"]),
        Index(value = ["source_event_id"])],
)
data class PromiseEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "content") val content: ByteArray,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "source_event_id") val sourceEventId: String?,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
