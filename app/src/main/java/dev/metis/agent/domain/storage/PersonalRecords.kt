package dev.metis.agent.domain.storage

import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class TaskStatus { OPEN, COMPLETED, CANCELLED }
enum class ScheduleStatus { PROPOSED, ACCEPTED, COMPLETED, CANCELLED }
enum class MemoryType { WORKING, EPISODIC, SEMANTIC, PROCEDURAL, GOAL, RELATIONSHIP }
enum class MemoryOrigin { EXPLICIT, DERIVED }

data class RecordMetadata(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val revision: Long = 0,
) {
    init {
        require(UUID.fromString(id).toString() == id)
        require(updatedAt >= createdAt && revision >= 0)
    }
}

data class SavedTask(
    val title: String,
    val metadata: RecordMetadata = RecordMetadata(),
    val notes: String? = null,
    val dueAt: Long? = null,
    val dueZoneId: String? = null,
    val estimatedSeconds: Long? = null,
    val priority: Int = 0,
    val status: TaskStatus = TaskStatus.OPEN,
    val completedAt: Long? = null,
    val projectId: String? = null,
    val goalId: String? = null,
) {
    init {
        require(title.isNotBlank() && title.length <= MAX_CONTENT_LENGTH)
        require(notes == null || notes.length <= MAX_CONTENT_LENGTH)
        require((dueAt == null) == (dueZoneId == null))
        dueZoneId?.let { ZoneId.of(it) }
        require(estimatedSeconds == null || estimatedSeconds > 0)
        require(priority in 0..MAX_PRIORITY)
        require((status == TaskStatus.COMPLETED) == (completedAt != null))
        projectId?.let { require(UUID.fromString(it).toString() == it) }
        goalId?.let { require(UUID.fromString(it).toString() == it) }
    }
}

data class SavedSchedule(
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val zoneId: String,
    val reason: String,
    val metadata: RecordMetadata = RecordMetadata(),
    val planId: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val status: ScheduleStatus = ScheduleStatus.PROPOSED,
) {
    init {
        require(title.isNotBlank() && title.length <= MAX_CONTENT_LENGTH)
        require(reason.isNotBlank() && reason.length <= MAX_CONTENT_LENGTH)
        require(endAt > startAt)
        ZoneId.of(zoneId)
        UUID.fromString(planId)
        taskId?.let { UUID.fromString(it) }
    }
}

data class SavedMemory(
    val content: String,
    val metadata: RecordMetadata = RecordMetadata(),
    val type: MemoryType = MemoryType.SEMANTIC,
    val origin: MemoryOrigin = MemoryOrigin.EXPLICIT,
    val importance: Float = 0.5f,
    val confidence: Float = 1f,
    val expiresAt: Long? = null,
    val entityType: MemoryEntityType? = null,
    val entityId: String? = null,
) {
    init {
        require(content.isNotBlank() && content.length <= MAX_CONTENT_LENGTH)
        require(importance in 0f..1f && confidence in 0f..1f)
        require((entityType == null) == (entityId == null))
        entityId?.let { require(UUID.fromString(it).toString() == it) }
    }
}

/** Internal persistence only; these interfaces do not authorize agent or Android actions. */
interface TaskRepository {
    fun observeTasks(): Flow<List<SavedTask>>
    suspend fun saveTask(task: SavedTask)
    suspend fun deleteTask(id: String, revision: Long)
}

interface ScheduleRepository {
    fun observeSchedules(): Flow<List<SavedSchedule>>
    suspend fun saveSchedule(schedule: SavedSchedule)
    suspend fun deleteSchedule(id: String, revision: Long)
}

interface MemoryRepository {
    suspend fun searchMemories(query: MemorySearchQuery): MemorySearchResult
    fun observeMemories(): Flow<List<SavedMemory>>
    suspend fun saveMemory(memory: SavedMemory)
    suspend fun deleteMemory(id: String, revision: Long)
}

class RevisionConflictException : IllegalStateException("The record has changed. Reload before saving.")

private const val MAX_CONTENT_LENGTH = 4_000
private const val MAX_PRIORITY = 3
