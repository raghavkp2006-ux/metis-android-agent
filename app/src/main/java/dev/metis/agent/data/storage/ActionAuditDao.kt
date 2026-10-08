package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionAuditDao : FoundationAccess<ActionAuditEntity> {
    @Query("SELECT * FROM action_audit ORDER BY timestamp DESC, id")
    override fun observe(): Flow<List<ActionAuditEntity>>
    @Query("SELECT * FROM action_audit WHERE id = :id")
    override suspend fun find(id: String): ActionAuditEntity?
    @Insert override suspend fun insert(row: ActionAuditEntity)
    override suspend fun update(row: ActionAuditEntity) { error("Append-only record.") }
    @Query("DELETE FROM action_audit WHERE id = :id AND :revision = 0")
    override suspend fun delete(id: String, revision: Long): Int
}
