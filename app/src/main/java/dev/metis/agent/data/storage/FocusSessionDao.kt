package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao : FoundationAccess<FocusSessionEntity> {
    @Query("SELECT * FROM focus_sessions ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    override suspend fun find(id: String): FocusSessionEntity?
    @Insert override suspend fun insert(row: FocusSessionEntity)
    @Update override suspend fun update(row: FocusSessionEntity)
    @Query("DELETE FROM focus_sessions WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
