package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences ORDER BY `key`, id")
    fun observe(): Flow<List<PreferenceEntity>>
    @Query("SELECT * FROM preferences WHERE id = :id")
    suspend fun preference(id: String): PreferenceEntity?
    @Insert suspend fun insert(preference: PreferenceEntity)
    @Update suspend fun update(preference: PreferenceEntity)
    @Query("DELETE FROM preferences WHERE id = :id AND revision = :revision")
    suspend fun delete(id: String, revision: Long): Int
}
