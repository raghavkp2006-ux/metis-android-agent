package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface ScheduleDao {
    @Query("SELECT * FROM timetable_blocks ORDER BY weekday ASC, start_min ASC")
    suspend fun queryAllBlocks(): List<ScheduleBlockEntity>

    @Query("SELECT * FROM timetable_blocks WHERE weekday = :weekday ORDER BY start_min ASC")
    suspend fun queryBlocksForWeekday(weekday: Int): List<ScheduleBlockEntity>

    @Query("SELECT * FROM timetable_blocks ORDER BY weekday ASC, start_min ASC")
    fun observeAllBlocks(): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM routine_rules ORDER BY window_start_min ASC")
    suspend fun queryAllRules(): List<RoutineRuleEntity>

    @Query("SELECT * FROM routine_rules ORDER BY window_start_min ASC")
    fun observeAllRules(): Flow<List<RoutineRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<ScheduleBlockEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RoutineRuleEntity>)

    @Query("DELETE FROM timetable_blocks")
    suspend fun deleteAllBlocks()

    @Query("DELETE FROM routine_rules")
    suspend fun deleteAllRules()

    @Query("SELECT COUNT(*) FROM timetable_blocks")
    suspend fun blockCount(): Int

    @Query("SELECT COUNT(*) FROM routine_rules")
    suspend fun ruleCount(): Int

    // Backup queries and mutations
    @Query("SELECT * FROM timetable_blocks_backup ORDER BY weekday ASC, start_min ASC")
    suspend fun queryAllBackupBlocks(): List<ScheduleBlockBackupEntity>

    @Query("SELECT * FROM routine_rules_backup ORDER BY window_start_min ASC")
    suspend fun queryAllBackupRules(): List<RoutineRuleBackupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupBlocks(blocks: List<ScheduleBlockBackupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupRules(rules: List<RoutineRuleBackupEntity>)

    @Query("DELETE FROM timetable_blocks_backup")
    suspend fun deleteAllBackupBlocks()

    @Query("DELETE FROM routine_rules_backup")
    suspend fun deleteAllBackupRules()

    @Query("SELECT COUNT(*) FROM timetable_blocks_backup")
    suspend fun backupBlockCount(): Int

    @Query("SELECT COUNT(*) FROM routine_rules_backup")
    suspend fun backupRuleCount(): Int

    @Transaction
    suspend fun atomicReplace(
        newBlocks: List<ScheduleBlockEntity>,
        newRules: List<RoutineRuleEntity>,
        backupBlocks: List<ScheduleBlockBackupEntity>,
        backupRules: List<RoutineRuleBackupEntity>,
    ) {
        deleteAllBackupBlocks()
        deleteAllBackupRules()

        if (backupBlocks.isNotEmpty()) {
            insertBackupBlocks(backupBlocks)
        }
        if (backupRules.isNotEmpty()) {
            insertBackupRules(backupRules)
        }

        deleteAllBlocks()
        deleteAllRules()

        if (newBlocks.isNotEmpty()) {
            insertBlocks(newBlocks)
        }
        if (newRules.isNotEmpty()) {
            insertRules(newRules)
        }
    }

    @Transaction
    suspend fun atomicRestore(
        restoredBlocks: List<ScheduleBlockEntity>,
        restoredRules: List<RoutineRuleEntity>,
    ) {
        deleteAllBlocks()
        deleteAllRules()

        if (restoredBlocks.isNotEmpty()) {
            insertBlocks(restoredBlocks)
        }
        if (restoredRules.isNotEmpty()) {
            insertRules(restoredRules)
        }

        deleteAllBackupBlocks()
        deleteAllBackupRules()
    }
}
