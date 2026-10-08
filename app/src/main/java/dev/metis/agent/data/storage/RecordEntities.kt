package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

data class StoredMetadata(
    val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val revision: Long,
)

@Entity(
    tableName = "tasks", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["project_id"],
            onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goal_id"],
            onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["status", "due_at"]), Index(value = ["project_id", "status"]),
        Index(value = ["goal_id", "status"])],
)
data class TaskEntity(
    @Embedded val metadata: StoredMetadata,
    val title: ByteArray,
    val notes: ByteArray?,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "due_zone_id") val dueZoneId: String?,
    @ColumnInfo(name = "estimated_seconds") val estimatedSeconds: Long?,
    val priority: Int,
    val status: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "project_id") val projectId: String? = null,
    @ColumnInfo(name = "goal_id") val goalId: String? = null,
)

@Entity(
    tableName = "schedule_blocks",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["task_id"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("task_id"), Index(value = ["start_at", "end_at"]), Index(value = ["plan_id", "status"])],
)
data class ScheduleEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    val title: ByteArray,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val status: String,
    val reason: ByteArray,
)

@Entity(
    tableName = "memories", primaryKeys = ["id"],
    indices = [Index(value = ["memory_type", "updated_at"]), Index(value = ["entity_type", "entity_id"])],
)
data class MemoryEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "memory_type") val type: String,
    val origin: String,
    val content: ByteArray,
    val importance: Float,
    val confidence: Float,
    @ColumnInfo(name = "expires_at") val expiresAt: Long?,
    @ColumnInfo(name = "entity_type") val entityType: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
)
