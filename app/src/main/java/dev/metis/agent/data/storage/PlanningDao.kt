package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanningDao {
    @Query("SELECT * FROM projects ORDER BY status, created_at, id")
    fun observeProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM goals ORDER BY status, target_at IS NULL, target_at, created_at, id")
    fun observeGoals(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun project(id: String): ProjectEntity?
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: String): GoalEntity?
    @Insert suspend fun insert(project: ProjectEntity)
    @Insert suspend fun insert(goal: GoalEntity)
    @Update suspend fun update(project: ProjectEntity)
    @Update suspend fun update(goal: GoalEntity)
    @Query("DELETE FROM projects WHERE id = :id AND revision = :revision")
    suspend fun deleteProject(id: String, revision: Long): Int
    @Query("DELETE FROM goals WHERE id = :id AND revision = :revision")
    suspend fun deleteGoal(id: String, revision: Long): Int
}
