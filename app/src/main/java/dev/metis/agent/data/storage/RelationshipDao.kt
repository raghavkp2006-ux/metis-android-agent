package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao : FoundationAccess<RelationshipEntity> {
    @Query("SELECT * FROM relationships ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<RelationshipEntity>>
    @Query("SELECT * FROM relationships WHERE id = :id")
    override suspend fun find(id: String): RelationshipEntity?
    @Insert override suspend fun insert(row: RelationshipEntity)
    @Update override suspend fun update(row: RelationshipEntity)
    @Query("DELETE FROM relationships WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
