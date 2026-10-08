package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentSessionDao : FoundationAccess<AgentSessionEntity> {
    @Query("SELECT * FROM agent_sessions ORDER BY started_at DESC, id")
    override fun observe(): Flow<List<AgentSessionEntity>>
    @Query("SELECT * FROM agent_sessions WHERE id = :id")
    override suspend fun find(id: String): AgentSessionEntity?
    @Insert override suspend fun insert(row: AgentSessionEntity)
    override suspend fun update(row: AgentSessionEntity) { error("Append-only record.") }
    @Query("DELETE FROM agent_sessions WHERE id = :id AND :revision = 0")
    override suspend fun delete(id: String, revision: Long): Int
}
