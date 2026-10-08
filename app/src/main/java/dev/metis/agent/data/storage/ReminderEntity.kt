package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "reminders", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"],
            onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["idempotency_key"], unique = true), Index(value = ["scheduling_state",
        "trigger_at"]), Index(value = ["task_id"]), Index(value = ["person_id"])],
)
data class ReminderEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "title") val title: ByteArray,
    @ColumnInfo(name = "trigger_at") val triggerAt: Long,
    @ColumnInfo(name = "local_date_time") val localDateTime: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "precision") val precision: String,
    @ColumnInfo(name = "scheduling_state") val schedulingState: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "person_id") val personId: String?,
    @ColumnInfo(name = "platform_token") val platformToken: String?,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long?,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
