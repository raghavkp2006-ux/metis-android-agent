package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationDao : FoundationAccess<RecommendationEntity> {
    @Query("SELECT * FROM recommendations ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<RecommendationEntity>>
    @Query("SELECT * FROM recommendations WHERE id = :id")
    override suspend fun find(id: String): RecommendationEntity?
    @Insert override suspend fun insert(row: RecommendationEntity)
    @Update override suspend fun update(row: RecommendationEntity)
    @Query("DELETE FROM recommendations WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
