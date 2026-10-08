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
            val metadata = nextMetadata(task.metadata, old?.metadata, now())
            val row = TaskEntity(
                metadata, codec.encrypt(task.title, "tasks", metadata.id, "title"),
                task.notes?.let { codec.encrypt(it, "tasks", metadata.id, "notes") },
                task.dueAt, task.dueZoneId, task.estimatedSeconds, task.priority, task.status.name, task.completedAt,
            )
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun saveSchedule(schedule: SavedSchedule) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(dao, codec)
            val old = dao.schedule(schedule.metadata.id)
            val metadata = nextMetadata(schedule.metadata, old?.metadata, now())
            val row = ScheduleEntity(
                metadata, schedule.planId, schedule.taskId,
                codec.encrypt(schedule.title, "schedule_blocks", metadata.id, "title"),
                schedule.startAt, schedule.endAt, schedule.zoneId, schedule.status.name,
                codec.encrypt(schedule.reason, "schedule_blocks", metadata.id, "reason"),
            )
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun saveMemory(memory: SavedMemory) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(dao, codec)
            val old = dao.memory(memory.metadata.id)
            validateMemoryReference(memory, dao)
            val metadata = nextMetadata(memory.metadata, old?.metadata, now())
            val row = MemoryEntity(
                metadata, memory.type.name, memory.origin.name,
                codec.encrypt(memory.content, "memories", metadata.id, "content"),
                memory.importance, memory.confidence, memory.expiresAt, memory.entityType?.name, memory.entityId,
            )
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun deleteTask(id: String, revision: Long) = delete(database, codec) {
        dao.deleteTask(id, revision).also { if (it == 1) dao.deleteLinkedMemories(MemoryEntityType.TASK.name, id) }
    }
    override suspend fun deleteSchedule(id: String, revision: Long) = delete(database, codec) {
        dao.deleteSchedule(id, revision).also {
            if (it == 1) dao.deleteLinkedMemories(MemoryEntityType.SCHEDULE.name, id)
        }
    }
    override suspend fun deleteMemory(id: String, revision: Long) = delete(database, codec) {
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

private suspend fun validateMemoryReference(memory: SavedMemory, dao: RecordDao) {
    when (memory.entityType) {
        MemoryEntityType.TASK -> requireNotNull(dao.task(requireNotNull(memory.entityId)))
        MemoryEntityType.SCHEDULE -> requireNotNull(dao.schedule(requireNotNull(memory.entityId)))
        null -> Unit
    }
}

// Prove existing encrypted data is readable before any write can generate a new key.
// A lost key never causes replacement of the key for a populated database.
internal suspend fun requireReadableKey(dao: RecordDao, codec: RecordCodec) {
    val task = dao.firstTask()
    if (task != null) {
        codec.decrypt(task.title, "tasks", task.metadata.id, "title")
        return
    }
    val schedule = dao.firstSchedule()
    if (schedule != null) {
        codec.decrypt(schedule.title, "schedule_blocks", schedule.metadata.id, "title")
        return
    }
    val memory = dao.firstMemory()
    if (memory != null) {
        codec.decrypt(memory.content, "memories", memory.metadata.id, "content")
    } else {
        dao.firstPreference()?.let { codec.decrypt(it.typedValue, "preferences", it.metadata.id, "typed_value") }
    }
}

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
    ) }

    fun decode(row: ScheduleEntity) = with(row) { SavedSchedule(
        title = decrypt(title, "schedule_blocks", metadata.id, "title"), metadata = metadata.decode(),
        planId = planId, taskId = taskId, startAt = startAt, endAt = endAt, zoneId = zoneId,
        status = ScheduleStatus.valueOf(status), reason = decrypt(reason, "schedule_blocks", metadata.id, "reason"),
    ) }

    fun decode(row: MemoryEntity) = with(row) { SavedMemory(
        content = decrypt(content, "memories", metadata.id, "content"), metadata = metadata.decode(),
        type = MemoryType.valueOf(type), origin = MemoryOrigin.valueOf(origin), importance = importance,
        confidence = confidence, expiresAt = expiresAt,
        entityType = entityType?.let { MemoryEntityType.valueOf(it) }, entityId = entityId,
    ) }
}
