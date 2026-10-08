package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity

@Entity(
    tableName = "routines", primaryKeys = ["id"],
)
data class RoutineEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "name") val name: ByteArray,
    @ColumnInfo(name = "recurrence_rule") val recurrenceRule: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "definition") val definition: ByteArray,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
