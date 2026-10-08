package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DerivedInsightDao : FoundationAccess<DerivedInsightEntity> {
    @Query("SELECT * FROM derived_insights ORDER BY computed_at DESC, id")
    override fun observe(): Flow<List<DerivedInsightEntity>>
    @Query("SELECT * FROM derived_insights WHERE id = :id")
    override suspend fun find(id: String): DerivedInsightEntity?
    @Insert override suspend fun insert(row: DerivedInsightEntity)
    override suspend fun update(row: DerivedInsightEntity) { error("Append-only record.") }
    @Query("DELETE FROM derived_insights WHERE id = :id AND :revision = 0")
    override suspend fun delete(id: String, revision: Long): Int
}
