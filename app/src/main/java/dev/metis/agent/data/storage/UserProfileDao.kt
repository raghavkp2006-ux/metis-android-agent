package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao : FoundationAccess<UserProfileEntity> {
    @Query("SELECT * FROM user_profile ORDER BY updated_at DESC, id")
    override fun observe(): Flow<List<UserProfileEntity>>
    @Query("SELECT * FROM user_profile WHERE id = :id")
    override suspend fun find(id: String): UserProfileEntity?
    @Insert override suspend fun insert(row: UserProfileEntity)
    @Update override suspend fun update(row: UserProfileEntity)
    @Query("DELETE FROM user_profile WHERE id = :id AND revision = :revision")
    override suspend fun delete(id: String, revision: Long): Int
}
