package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemoryRepository
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.MemoryType
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleRepository
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.domain.storage.TaskRepository
import dev.metis.agent.domain.storage.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalPersonalRepository(
    internal val database: PersonalDatabase,
    cipher: FieldCipher,
    private val now: () -> Long = System::currentTimeMillis,
) : TaskRepository, ScheduleRepository, MemoryRepository {
    private val dao = database.records()
    private val codec = RecordCodec(cipher)
    val dependencies = LocalTaskDependencyRepository(database, cipher, now)
    val preferences = LocalPreferenceRepository(database, cipher, now)
    val planning = LocalPlanningRepository(database, cipher, now)
    val foundation = FoundationRepositories(database, cipher)
    val outcomes = FoundationOutcomes(database, foundation)

    override suspend fun searchMemories(query: MemorySearchQuery) = InMemoryMemorySearch(dao, codec).search(query)

    override fun observeTasks() = dao.observeTasks().map { rows -> rows.map { codec.decode(it) } }
        .flowOn(Dispatchers.IO)
    override fun observeSchedules() = dao.observeSchedules().map { rows -> rows.map { codec.decode(it) } }
        .flowOn(Dispatchers.IO)
    override fun observeMemories() = dao.observeMemories().map { rows -> rows.map { codec.decode(it) } }
        .flowOn(Dispatchers.IO)

    override suspend fun saveTask(task: SavedTask) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(dao, codec)
            val old = dao.task(task.metadata.id)
            task.projectId?.let { requireNotNull(database.planning().project(it)) }
            task.goalId?.let { id ->
                val goal = requireNotNull(database.planning().goal(id))
                require(task.projectId == null || goal.projectId == null || task.projectId == goal.projectId)
            }
            val metadata = nextMetadata(task.metadata, old?.metadata, now())
            val row = TaskEntity(
                metadata, codec.encrypt(task.title, "tasks", metadata.id, "title"),
                task.notes?.let { codec.encrypt(it, "tasks", metadata.id, "notes") },
                task.dueAt, task.dueZoneId, task.estimatedSeconds, task.priority, task.status.name, task.completedAt,
                task.projectId, task.goalId,
                task.recurrenceRule, task.recurrenceZoneId,
            )
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun saveSchedule(schedule: SavedSchedule) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(dao, codec)
            val old = dao.schedule(schedule.metadata.id)
            schedule.routineId?.let { requireNotNull(database.routine().find(it)) }
            val metadata = nextMetadata(schedule.metadata, old?.metadata, now())
            val row = ScheduleEntity(
                metadata, schedule.planId, schedule.taskId,
                codec.encrypt(schedule.title, "schedule_blocks", metadata.id, "title"),
                schedule.startAt, schedule.endAt, schedule.zoneId, schedule.status.name,
                codec.encrypt(schedule.reason, "schedule_blocks", metadata.id, "reason"),
                schedule.routineId,
                schedule.scoreComponentsJson?.let { codec.encryptJson(it, "schedule_blocks", metadata.id,
                    "score_components_json") },
            )
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun saveMemory(memory: SavedMemory) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(dao, codec)
            val old = dao.memory(memory.metadata.id)
            validateFoundationReference(database, memory.entityType?.name, memory.entityId)
            memory.sourceEventId?.let { requireNotNull(database.event().find(it)) }
            val metadata = nextMetadata(memory.metadata, old?.metadata, now())
            val row = MemoryEntity(
                metadata, memory.type.name, memory.origin.name,
                codec.encrypt(memory.content, "memories", metadata.id, "content"),
                memory.importance, memory.confidence, memory.expiresAt, memory.entityType?.name, memory.entityId,
                memory.sourceEventId,
            )
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun deleteTask(id: String, revision: Long) = delete(database, codec) {
        FoundationPrivacy.detach(database, codec, "TASK", id)
        dao.deleteTask(id, revision)
    }
    override suspend fun deleteSchedule(id: String, revision: Long) = delete(database, codec) {
        FoundationPrivacy.detach(database, codec, "SCHEDULE", id)
        dao.deleteSchedule(id, revision)
    }
    override suspend fun deleteMemory(id: String, revision: Long) = delete(database, codec) {
        FoundationPrivacy.detach(database, codec, "MEMORY", id)
        dao.deleteMemory(id, revision)
    }
}

private suspend fun delete(
    database: PersonalDatabase, codec: RecordCodec, operation: suspend () -> Int,
) = withContext(Dispatchers.IO) {
    database.withTransaction {
        requireReadableKey(database.records(), codec)
        if (operation() != 1) throw RevisionConflictException()
    }
}

// Prove existing encrypted data is readable before any write can generate a new key.
// A lost key never causes replacement of the key for a populated database.
internal suspend fun requireReadableKey(dao: RecordDao, codec: RecordCodec) {
    val field = dao.firstTask()?.let { KeyProbe(it.title, "tasks", it.metadata.id, "title") }
        ?: dao.firstSchedule()?.let { KeyProbe(it.title, "schedule_blocks", it.metadata.id, "title") }
        ?: dao.firstMemory()?.let { KeyProbe(it.content, "memories", it.metadata.id, "content") }
        ?: dao.firstPreference()?.let { KeyProbe(it.typedValue, "preferences", it.metadata.id, "typed_value") }
        ?: dao.firstProject()?.let { KeyProbe(it.title, "projects", it.metadata.id, "title") }
        ?: dao.firstGoal()?.let { KeyProbe(it.title, "goals", it.metadata.id, "title") }
        ?: foundationProbe(dao)
    field?.let { codec.decrypt(it.value, it.table, it.id, it.column) }
}

private data class KeyProbe(val value: ByteArray, val table: String, val id: String, val column: String)

private suspend fun foundationProbe(dao: RecordDao) =
    dao.firstFoundationCipher()?.let { KeyProbe(it.value, it.tableName, it.id, it.fieldName) }

internal fun nextMetadata(input: RecordMetadata, old: StoredMetadata?, now: Long): StoredMetadata {
    if (old == null) {
        if (input.revision != 0L) throw RevisionConflictException()
        return StoredMetadata(input.id, input.createdAt, input.updatedAt, 0)
    }
    if (input.revision != old.revision || input.createdAt != old.createdAt) throw RevisionConflictException()
    return old.copy(updatedAt = maxOf(now, old.updatedAt), revision = Math.addExact(old.revision, 1))
}

internal class RecordCodec(private val cipher: FieldCipher) {
    fun encrypt(value: String, table: String, id: String, field: String) =
        cipher.encrypt(value, "$table/$id/$field")

    fun decrypt(value: ByteArray, table: String, id: String, field: String) =
        cipher.decrypt(value, "$table/$id/$field")

    private fun StoredMetadata.decode() = RecordMetadata(id, createdAt, updatedAt, revision)

    fun decode(row: TaskEntity) = with(row) { SavedTask(
        title = decrypt(title, "tasks", metadata.id, "title"), metadata = metadata.decode(),
        notes = notes?.let { decrypt(it, "tasks", metadata.id, "notes") }, dueAt = dueAt, dueZoneId = dueZoneId,
        estimatedSeconds = estimatedSeconds, priority = priority, status = TaskStatus.valueOf(status),
        completedAt = completedAt,
        projectId = projectId, goalId = goalId,
        recurrenceRule = recurrenceRule, recurrenceZoneId = recurrenceZoneId,
    ) }

    fun decode(row: ScheduleEntity) = with(row) { SavedSchedule(
        title = decrypt(title, "schedule_blocks", metadata.id, "title"), metadata = metadata.decode(),
        planId = planId, taskId = taskId, startAt = startAt, endAt = endAt, zoneId = zoneId,
        status = ScheduleStatus.valueOf(status), reason = decrypt(reason, "schedule_blocks", metadata.id, "reason"),
        routineId = routineId,
        scoreComponentsJson = scoreComponentsJson?.let { decryptJson(it, "schedule_blocks", metadata.id,
            "score_components_json") },
    ) }

    fun decode(row: MemoryEntity) = with(row) { SavedMemory(
        content = decrypt(content, "memories", metadata.id, "content"), metadata = metadata.decode(),
        type = MemoryType.valueOf(type), origin = MemoryOrigin.valueOf(origin), importance = importance,
        confidence = confidence, expiresAt = expiresAt,
        entityType = entityType?.let { MemoryEntityType.valueOf(it) }, entityId = entityId,
        sourceEventId = sourceEventId,
    ) }
}
