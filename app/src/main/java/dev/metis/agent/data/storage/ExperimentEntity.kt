package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity

@Entity(
    tableName = "experiments", primaryKeys = ["id"],
)
data class ExperimentEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "name") val name: ByteArray,
    @ColumnInfo(name = "hypothesis") val hypothesis: ByteArray,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "consented_at") val consentedAt: Long,
    @ColumnInfo(name = "definition") val definition: ByteArray,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
