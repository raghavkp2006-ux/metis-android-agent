package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao : FoundationAccess<HabitEntity> {
    @Query("SELECT * FROM habits ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<HabitEntity>>
    @Query("SELECT * FROM habits WHERE id = :id")
    override suspend fun find(id: String): HabitEntity?
    @Insert override suspend fun insert(row: HabitEntity)
    @Update override suspend fun update(row: HabitEntity)
    @Query("DELETE FROM habits WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
