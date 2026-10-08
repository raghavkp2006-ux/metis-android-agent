package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "agent_sessions", primaryKeys = ["id"],
)
data class AgentSessionEntity(
    val id: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "summary") val summary: ByteArray?,
) : FoundationEntity {
    override fun recordMetadata() = StoredMetadata(id, startedAt, startedAt, 0)
}
