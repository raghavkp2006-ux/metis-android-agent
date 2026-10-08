package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** ABORT inserts and revision checks preserve identity; never use REPLACE with SET NULL links. */
@Dao
interface RecordDao : TaskQueries, ScheduleQueries, MemoryQueries, MemorySearchQueries {
    @Query("""
        SELECT (SELECT COUNT(*) FROM tasks) + (SELECT COUNT(*) FROM schedule_blocks)
        + (SELECT COUNT(*) FROM memories) + (SELECT COUNT(*) FROM preferences)
        + (SELECT COUNT(*) FROM projects) + (SELECT COUNT(*) FROM goals)
    """)
    suspend fun recordCount(): Int

    @Query("SELECT * FROM preferences LIMIT 1")
    suspend fun firstPreference(): PreferenceEntity?

    @Query("SELECT * FROM projects LIMIT 1") suspend fun firstProject(): ProjectEntity?
    @Query("SELECT * FROM goals LIMIT 1") suspend fun firstGoal(): GoalEntity?
    @Query("SELECT COUNT(*) FROM tasks WHERE goal_id = :id AND project_id IS NOT NULL AND project_id != :projectId")
    suspend fun conflictingGoalTasks(id: String, projectId: String): Int
}

interface TaskQueries {
    @Query("SELECT * FROM tasks LIMIT 1")
    suspend fun firstTask(): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY status, due_at IS NULL, due_at, created_at, id")
    fun observeTasks(): Flow<List<TaskEntity>>


    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: String): TaskEntity?


    @Insert suspend fun insert(task: TaskEntity)
    @Update suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id AND revision = :revision")
    suspend fun deleteTask(id: String, revision: Long): Int
}

interface ScheduleQueries {
    @Query("SELECT * FROM schedule_blocks LIMIT 1")
    suspend fun firstSchedule(): ScheduleEntity?
    @Query("SELECT * FROM schedule_blocks ORDER BY start_at, end_at, id")
    fun observeSchedules(): Flow<List<ScheduleEntity>>
    @Query("SELECT * FROM schedule_blocks WHERE id = :id")
    suspend fun schedule(id: String): ScheduleEntity?
    @Insert suspend fun insert(schedule: ScheduleEntity)
    @Update suspend fun update(schedule: ScheduleEntity)

    @Query("DELETE FROM schedule_blocks WHERE id = :id AND revision = :revision")
    suspend fun deleteSchedule(id: String, revision: Long): Int
}

interface MemoryQueries {
    @Query("SELECT * FROM memories LIMIT 1")
    suspend fun firstMemory(): MemoryEntity?
    @Query("SELECT * FROM memories ORDER BY updated_at DESC, id")
    fun observeMemories(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun memory(id: String): MemoryEntity?
    @Insert suspend fun insert(memory: MemoryEntity)
    @Update suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id AND revision = :revision")
    suspend fun deleteMemory(id: String, revision: Long): Int

    @Query("DELETE FROM memories WHERE entity_type = :type AND entity_id = :id")
    suspend fun deleteLinkedMemories(type: String, id: String)
}

interface MemorySearchQueries {
    @RawQuery
    suspend fun memoryCandidates(query: SupportSQLiteQuery): List<MemoryEntity>
}
