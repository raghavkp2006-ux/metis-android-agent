package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PromiseDao : FoundationAccess<PromiseEntity> {
    @Query("SELECT * FROM promises ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<PromiseEntity>>
    @Query("SELECT * FROM promises WHERE id = :id")
    override suspend fun find(id: String): PromiseEntity?
    @Insert override suspend fun insert(row: PromiseEntity)
    @Update override suspend fun update(row: PromiseEntity)
    @Query("DELETE FROM promises WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
