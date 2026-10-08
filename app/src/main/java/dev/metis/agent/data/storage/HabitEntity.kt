package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity

@Entity(
    tableName = "habits", primaryKeys = ["id"],
)
data class HabitEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "name") val name: ByteArray,
    @ColumnInfo(name = "definition") val definition: ByteArray,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    @ColumnInfo(name = "confidence") val confidence: Float,
    @ColumnInfo(name = "observation_start") val observationStart: Long,
    @ColumnInfo(name = "observation_end") val observationEnd: Long,
    @ColumnInfo(name = "method_version") val methodVersion: String,
    @ColumnInfo(name = "consent_required") val consentRequired: Boolean,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
