package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "task_dependencies", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["depends_on_task_id"],
            onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["task_id", "depends_on_task_id"], unique = true), Index("depends_on_task_id")],
)
data class TaskDependencyEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "depends_on_task_id") val dependsOnTaskId: String,
)
