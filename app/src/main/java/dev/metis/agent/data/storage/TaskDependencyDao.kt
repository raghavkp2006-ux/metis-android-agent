package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDependencyDao {
    @Query("SELECT * FROM task_dependencies ORDER BY task_id, depends_on_task_id, id")
    fun observe(): Flow<List<TaskDependencyEntity>>
    @Query("SELECT * FROM task_dependencies")
    suspend fun all(): List<TaskDependencyEntity>
    @Query("SELECT * FROM task_dependencies WHERE id = :id")
    suspend fun dependency(id: String): TaskDependencyEntity?
    @Insert suspend fun insert(dependency: TaskDependencyEntity)
    @Update suspend fun update(dependency: TaskDependencyEntity)
    @Query("DELETE FROM task_dependencies WHERE id = :id AND revision = :revision")
    suspend fun delete(id: String, revision: Long): Int
}
