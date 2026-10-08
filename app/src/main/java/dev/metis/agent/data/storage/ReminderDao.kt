package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao : FoundationAccess<ReminderEntity> {
    @Query("SELECT * FROM reminders ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE id = :id")
    override suspend fun find(id: String): ReminderEntity?
    @Insert override suspend fun insert(row: ReminderEntity)
    @Update override suspend fun update(row: ReminderEntity)
    @Query("DELETE FROM reminders WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
