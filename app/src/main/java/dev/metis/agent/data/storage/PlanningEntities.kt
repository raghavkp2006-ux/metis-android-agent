package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "projects", primaryKeys = ["id"], indices = [Index("status")])
data class ProjectEntity(
    @Embedded val metadata: StoredMetadata,
    val title: ByteArray,
    val description: ByteArray?,
    val status: String,
)

@Entity(
    tableName = "goals", primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["project_id"],
        onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["project_id", "status"]), Index(value = ["status", "target_at"])],
)
data class GoalEntity(
    @Embedded val metadata: StoredMetadata,
    val title: ByteArray,
    val description: ByteArray?,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "target_at") val targetAt: Long?,
    val priority: Int,
    val status: String,
)
