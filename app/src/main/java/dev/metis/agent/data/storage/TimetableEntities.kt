package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timetable_blocks",
    indices = [
        Index("weekday"),
        Index("fixed"),
    ],
)
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    val weekday: Int,
    @ColumnInfo(name = "start_min") val startMin: Int,
    @ColumnInfo(name = "end_min") val endMin: Int,
    val title: ByteArray,
    val kind: String,
    val fixed: Boolean,
    val tag: ByteArray?,
)

@Entity(
    tableName = "routine_rules",
    indices = [
        Index("key"),
    ],
)
data class RoutineRuleEntity(
    @PrimaryKey val id: String,
    val key: String,
    @ColumnInfo(name = "window_start_min") val windowStartMin: Int,
    @ColumnInfo(name = "window_end_min") val windowEndMin: Int,
    @ColumnInfo(name = "weekdays_mask") val weekdaysMask: Int,
    val hardness: String,
    val note: ByteArray?,
)

@Entity(
    tableName = "timetable_blocks_backup",
    indices = [
        Index("weekday"),
        Index("fixed"),
    ],
)
data class ScheduleBlockBackupEntity(
    @PrimaryKey val id: String,
    val weekday: Int,
    @ColumnInfo(name = "start_min") val startMin: Int,
    @ColumnInfo(name = "end_min") val endMin: Int,
    val title: ByteArray,
    val kind: String,
    val fixed: Boolean,
    val tag: ByteArray?,
)

@Entity(
    tableName = "routine_rules_backup",
    indices = [
        Index("key"),
    ],
)
data class RoutineRuleBackupEntity(
    @PrimaryKey val id: String,
    val key: String,
    @ColumnInfo(name = "window_start_min") val windowStartMin: Int,
    @ColumnInfo(name = "window_end_min") val windowEndMin: Int,
    @ColumnInfo(name = "weekdays_mask") val weekdaysMask: Int,
    val hardness: String,
    val note: ByteArray?,
)

