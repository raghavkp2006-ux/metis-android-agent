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
interface RecordDao : TaskQueries, ScheduleQueries, MemoryQueries, MemorySearchQueries, MemoryRetentionQueries {
    @Query("""
        SELECT (SELECT COUNT(*) FROM tasks) + (SELECT COUNT(*) FROM schedule_blocks)
        + (SELECT COUNT(*) FROM memories) + (SELECT COUNT(*) FROM preferences)
        + (SELECT COUNT(*) FROM projects) + (SELECT COUNT(*) FROM goals)
        + (SELECT COUNT(*) FROM persons)
        + (SELECT COUNT(*) FROM relationships)
        + (SELECT COUNT(*) FROM user_profile)
        + (SELECT COUNT(*) FROM reminders)
        + (SELECT COUNT(*) FROM focus_sessions)
        + (SELECT COUNT(*) FROM events)
        + (SELECT COUNT(*) FROM promises)
        + (SELECT COUNT(*) FROM routines)
        + (SELECT COUNT(*) FROM action_runs)
        + (SELECT COUNT(*) FROM action_audit)
        + (SELECT COUNT(*) FROM agent_sessions)
        + (SELECT COUNT(*) FROM recommendations)
        + (SELECT COUNT(*) FROM experiments)
        + (SELECT COUNT(*) FROM habits)
        + (SELECT COUNT(*) FROM derived_insights)
    """)
    suspend fun recordCount(): Int

    @Query("SELECT * FROM preferences LIMIT 1")
    suspend fun firstPreference(): PreferenceEntity?

    @Query("SELECT * FROM projects LIMIT 1") suspend fun firstProject(): ProjectEntity?
    @Query("SELECT * FROM goals LIMIT 1") suspend fun firstGoal(): GoalEntity?
    @Query("SELECT COUNT(*) FROM tasks WHERE goal_id = :id AND project_id IS NOT NULL AND project_id != :projectId")
    suspend fun conflictingGoalTasks(id: String, projectId: String): Int

    @Query("""
        SELECT * FROM (SELECT id, display_name AS value, 'persons' AS tableName, 'display_name' AS fieldName
            FROM persons WHERE display_name IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, description AS value, 'relationships' AS tableName, 'description' AS fieldName
            FROM relationships WHERE description IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, display_name AS value, 'user_profile' AS tableName, 'display_name' AS fieldName
            FROM user_profile WHERE display_name IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, title AS value, 'reminders' AS tableName, 'title' AS fieldName
            FROM reminders WHERE title IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, metadata AS value, 'events' AS tableName, 'metadata' AS fieldName
            FROM events WHERE metadata IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, content AS value, 'promises' AS tableName, 'content' AS fieldName
            FROM promises WHERE content IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, name AS value, 'routines' AS tableName, 'name' AS fieldName
            FROM routines WHERE name IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, payload AS value, 'action_runs' AS tableName, 'payload' AS fieldName
            FROM action_runs WHERE payload IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, permission_snapshot_json AS value, 'action_audit' AS tableName,
            'permission_snapshot_json' AS fieldName
            FROM action_audit WHERE permission_snapshot_json IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, summary AS value, 'agent_sessions' AS tableName, 'summary' AS fieldName
            FROM agent_sessions WHERE summary IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, score_components_json AS value, 'recommendations' AS tableName,
            'score_components_json' AS fieldName
            FROM recommendations WHERE score_components_json IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, name AS value, 'experiments' AS tableName, 'name' AS fieldName
            FROM experiments WHERE name IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, name AS value, 'habits' AS tableName, 'name' AS fieldName
            FROM habits WHERE name IS NOT NULL LIMIT 1)
        UNION ALL
        SELECT * FROM (SELECT id, statistics_json AS value,
            'derived_insights' AS tableName, 'statistics_json' AS fieldName
            FROM derived_insights WHERE statistics_json IS NOT NULL LIMIT 1)
        LIMIT 1
    """)
    suspend fun firstFoundationCipher(): FoundationKeyProbe?
}

data class FoundationKeyProbe(val id: String, val value: ByteArray, val tableName: String, val fieldName: String)

interface TaskQueries {
    @Query("SELECT * FROM tasks WHERE status = 'OPEN' ORDER BY id")
    suspend fun openTasks(): List<TaskEntity>
    @Query("""
        SELECT COUNT(*) FROM task_dependencies d JOIN tasks t ON t.id = d.depends_on_task_id
        WHERE d.task_id = :id AND t.status != 'COMPLETED'
    """)
    suspend fun incompletePrerequisites(id: String): Int
    @Query("""
        SELECT COUNT(*) FROM task_dependencies d JOIN tasks t ON t.id = d.task_id
        WHERE d.depends_on_task_id = :id AND t.status = 'COMPLETED'
    """)
    suspend fun completedDependents(id: String): Int
    @Query("""
        SELECT (SELECT COUNT(*) FROM schedule_blocks WHERE task_id = :id)
        + (SELECT COUNT(*) FROM task_dependencies WHERE task_id = :id OR depends_on_task_id = :id)
        + (SELECT COUNT(*) FROM memories WHERE entity_type = 'TASK' AND entity_id = :id)
        + (SELECT COUNT(*) FROM reminders WHERE task_id = :id)
        + (SELECT COUNT(*) FROM promises WHERE task_id = :id)
        + (SELECT COUNT(*) FROM focus_sessions WHERE task_id = :id)
        + (SELECT COUNT(*) FROM events WHERE entity_type = 'TASK' AND entity_id = :id)
        + (SELECT COUNT(*) FROM recommendations WHERE entity_type = 'TASK' AND entity_id = :id)
        + (SELECT COUNT(*) FROM derived_insights WHERE entity_type = 'TASK' AND entity_id = :id)
        + (SELECT COUNT(*) FROM action_runs WHERE entity_type = 'TASK' AND entity_id = :id AND id != :actionId)
    """)
    suspend fun taskUndoLinks(id: String, actionId: String): Int
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
