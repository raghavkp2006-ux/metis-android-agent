package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedTaskDependency
import dev.metis.agent.domain.storage.TaskDependencyGraph
import dev.metis.agent.domain.storage.TaskDependencyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalTaskDependencyRepository(
    private val database: PersonalDatabase,
    cipher: FieldCipher,
    private val now: () -> Long = System::currentTimeMillis,
) : TaskDependencyRepository {
    private val dao = database.dependencies()
    private val codec = RecordCodec(cipher)

    override fun observeDependencies() = dao.observe().map { rows ->
        requireReadableKey(database.records(), codec)
        rows.map { it.decode() }
    }.flowOn(Dispatchers.IO)

    override suspend fun saveDependency(dependency: SavedTaskDependency) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), codec)
            val old = dao.dependency(dependency.metadata.id)
            val metadata = nextMetadata(dependency.metadata, old?.metadata, now())
            requireNotNull(database.records().task(dependency.taskId))
            requireNotNull(database.records().task(dependency.dependsOnTaskId))
            val otherEdges = dao.all().filter { it.metadata.id != dependency.metadata.id }.map { it.decode() }
            require(!TaskDependencyGraph.wouldCreateCycle(dependency.taskId, dependency.dependsOnTaskId, otherEdges)) {
                "Task dependencies must be acyclic."
            }
            val row = TaskDependencyEntity(metadata, dependency.taskId, dependency.dependsOnTaskId)
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun deleteDependency(id: String, revision: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), codec)
            if (dao.delete(id, revision) != 1) throw RevisionConflictException()
        }
    }
}

private fun TaskDependencyEntity.decode() = SavedTaskDependency(
    taskId, dependsOnTaskId, RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
)
