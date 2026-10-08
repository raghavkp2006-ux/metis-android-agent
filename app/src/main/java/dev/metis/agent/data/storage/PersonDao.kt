package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao : FoundationAccess<PersonEntity> {
    @Query("SELECT * FROM persons ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM persons WHERE id = :id")
    override suspend fun find(id: String): PersonEntity?
    @Insert override suspend fun insert(row: PersonEntity)
    @Update override suspend fun update(row: PersonEntity)
    @Query("DELETE FROM persons WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
