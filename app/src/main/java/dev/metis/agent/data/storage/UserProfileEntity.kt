package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "user_profile", primaryKeys = ["id"],
    indices = [Index(value = ["active"], unique = true)],
)
data class UserProfileEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "display_name") val displayName: ByteArray,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "locale") val locale: String,
    @ColumnInfo(name = "autonomy_level") val autonomyLevel: Int,
    @ColumnInfo(name = "behavioral_analysis_consent") val behavioralAnalysisConsent: Boolean,
    @ColumnInfo(name = "active") val active: Boolean,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
