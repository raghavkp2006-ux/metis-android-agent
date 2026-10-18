package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "habit_definitions", primaryKeys = ["id"],
    indices = [Index(value = ["start_local_date", "archived_at"])])
data class HabitDefinitionEntity(
    @Embedded val metadata: StoredMetadata,
    val name: ByteArray,
    val description: ByteArray?,
    val cadence: Int,
    val active: Boolean,
    @ColumnInfo(name = "start_local_date") val startLocalDate: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String?,
)

@Entity(tableName = "daily_checkins", primaryKeys = ["id"],
    indices = [Index(value = ["local_date"], unique = true)])
data class DailyCheckinEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val mood: ByteArray?,
    val note: ByteArray?,
)

@Entity(tableName = "habit_ticks", primaryKeys = ["id"], foreignKeys = [
    ForeignKey(entity = DailyCheckinEntity::class, parentColumns = ["id"], childColumns = ["checkin_id"],
        onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = HabitDefinitionEntity::class, parentColumns = ["id"], childColumns = ["habit_id"],
        onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["checkin_id", "habit_id"], unique = true), Index("habit_id")])
data class HabitTickEntity(
    val id: String,
    @ColumnInfo(name = "checkin_id") val checkinId: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    val completed: Boolean,
)
