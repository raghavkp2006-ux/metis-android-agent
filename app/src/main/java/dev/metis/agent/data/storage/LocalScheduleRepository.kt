package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RoutineRule
import dev.metis.agent.domain.storage.ScheduleBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TimetableRepository {
    suspend fun getBlocks(): List<ScheduleBlock>
    suspend fun getBlocksForWeekday(weekday: Int): List<ScheduleBlock>
    suspend fun getRoutineRules(): List<RoutineRule>
    fun observeBlocks(): Flow<List<ScheduleBlock>>
    fun observeRules(): Flow<List<RoutineRule>>
    suspend fun replaceSchedule(blocks: List<ScheduleBlock>, rules: List<RoutineRule>)
    suspend fun undoReplace(): Boolean
    suspend fun hasSchedule(): Boolean
    suspend fun canUndo(): Boolean
}

class LocalTimetableRepository internal constructor(
    private val database: PersonalDatabase,
    cipher: FieldCipher,
) : TimetableRepository {
    private val codec = ScheduleCodec(cipher)
    private val dao = database.schedule()

    override suspend fun getBlocks(): List<ScheduleBlock> =
        dao.queryAllBlocks().map { codec.decodeBlock(it) }

    override suspend fun getBlocksForWeekday(weekday: Int): List<ScheduleBlock> =
        dao.queryBlocksForWeekday(weekday).map { codec.decodeBlock(it) }

    override suspend fun getRoutineRules(): List<RoutineRule> =
        dao.queryAllRules().map { codec.decodeRule(it) }

    override fun observeBlocks(): Flow<List<ScheduleBlock>> =
        dao.observeAllBlocks().map { list -> list.map { codec.decodeBlock(it) } }

    override fun observeRules(): Flow<List<RoutineRule>> =
        dao.observeAllRules().map { list -> list.map { codec.decodeRule(it) } }

    override suspend fun replaceSchedule(blocks: List<ScheduleBlock>, rules: List<RoutineRule>) {
        val currentBlocks = dao.queryAllBlocks()
        val currentRules = dao.queryAllRules()

        val backupBlocks = currentBlocks.map { codec.toBackup(it) }
        val backupRules = currentRules.map { codec.toBackup(it) }

        val newBlockEntities = blocks.map { codec.encodeBlock(it) }
        val newRuleEntities = rules.map { codec.encodeRule(it) }

        dao.atomicReplace(newBlockEntities, newRuleEntities, backupBlocks, backupRules)
    }

    override suspend fun undoReplace(): Boolean {
        val backupBlocks = dao.queryAllBackupBlocks()
        val backupRules = dao.queryAllBackupRules()

        if (backupBlocks.isEmpty() && backupRules.isEmpty()) {
            return false
        }

        val restoredBlocks = backupBlocks.map { codec.fromBackup(it) }
        val restoredRules = backupRules.map { codec.fromBackup(it) }

        dao.atomicRestore(restoredBlocks, restoredRules)
        return true
    }

    override suspend fun hasSchedule(): Boolean =
        dao.blockCount() > 0 || dao.ruleCount() > 0

    override suspend fun canUndo(): Boolean =
        dao.backupBlockCount() > 0 || dao.backupRuleCount() > 0
}
