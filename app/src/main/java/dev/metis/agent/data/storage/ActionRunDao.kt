package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionRunDao : FoundationAccess<ActionRunEntity> {
    @Query("SELECT * FROM action_runs ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<ActionRunEntity>>
    @Query("SELECT * FROM action_runs WHERE id = :id")
    override suspend fun find(id: String): ActionRunEntity?
    @Query("SELECT * FROM action_runs WHERE idempotency_key = :key")
    suspend fun findByKey(key: String): ActionRunEntity?
    @Insert override suspend fun insert(row: ActionRunEntity)
    @Update override suspend fun update(row: ActionRunEntity)
    @Query("DELETE FROM action_runs WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
