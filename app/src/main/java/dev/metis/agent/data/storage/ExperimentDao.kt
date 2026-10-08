package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExperimentDao : FoundationAccess<ExperimentEntity> {
    @Query("SELECT * FROM experiments ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<ExperimentEntity>>
    @Query("SELECT * FROM experiments WHERE id = :id")
    override suspend fun find(id: String): ExperimentEntity?
    @Insert override suspend fun insert(row: ExperimentEntity)
    @Update override suspend fun update(row: ExperimentEntity)
    @Query("DELETE FROM experiments WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
