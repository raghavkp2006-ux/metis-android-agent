package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao : FoundationAccess<EventEntity> {
    @Query("SELECT * FROM events ORDER BY timestamp DESC, id")
    override fun observe(): Flow<List<EventEntity>>
    @Query("SELECT * FROM events WHERE id = :id")
    override suspend fun find(id: String): EventEntity?
    @Insert override suspend fun insert(row: EventEntity)
    override suspend fun update(row: EventEntity) { error("Append-only record.") }
    @Query("DELETE FROM events WHERE id = :id AND :revision = 0")
    override suspend fun delete(id: String, revision: Long): Int
}
