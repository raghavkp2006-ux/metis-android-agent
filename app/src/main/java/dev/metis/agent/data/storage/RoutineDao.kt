package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao : FoundationAccess<RoutineEntity> {
    @Query("SELECT * FROM routines ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<RoutineEntity>>
    @Query("SELECT * FROM routines WHERE id = :id")
    override suspend fun find(id: String): RoutineEntity?
    @Insert override suspend fun insert(row: RoutineEntity)
    @Update override suspend fun update(row: RoutineEntity)
    @Query("DELETE FROM routines WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
