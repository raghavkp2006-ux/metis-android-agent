package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.storage.GoalStatus
import dev.metis.agent.domain.storage.PlanningRepository
import dev.metis.agent.domain.storage.ProjectStatus
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalPlanningRepository(
    private val database: PersonalDatabase,
    cipher: FieldCipher,
    private val now: () -> Long = System::currentTimeMillis,
) : PlanningRepository {
    private val dao = database.planning()
    private val codec = RecordCodec(cipher)

    override fun observeProjects() = dao.observeProjects().map { rows -> rows.map { row ->
        SavedProject(codec.decrypt(row.title, "projects", row.metadata.id, "title"),
            row.description?.let { codec.decrypt(it, "projects", row.metadata.id, "description") },
            ProjectStatus.valueOf(row.status), row.metadata.domain())
    } }.flowOn(Dispatchers.IO)

    override fun observeGoals() = dao.observeGoals().map { rows -> rows.map { row ->
        SavedGoal(codec.decrypt(row.title, "goals", row.metadata.id, "title"),
            row.description?.let { codec.decrypt(it, "goals", row.metadata.id, "description") },
            row.projectId, row.targetAt, row.priority, GoalStatus.valueOf(row.status), row.metadata.domain())
    } }.flowOn(Dispatchers.IO)

    override suspend fun saveProject(project: SavedProject) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), codec)
            val old = dao.project(project.metadata.id)
            val metadata = nextMetadata(project.metadata, old?.metadata, now())
            val row = ProjectEntity(metadata, codec.encrypt(project.title, "projects", metadata.id, "title"),
                project.description?.let { codec.encrypt(it, "projects", metadata.id, "description") },
                project.status.name)
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun saveGoal(goal: SavedGoal) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), codec)
            goal.projectId?.let { requireNotNull(dao.project(it)) }
            if (goal.projectId != null) {
                check(database.records().conflictingGoalTasks(goal.metadata.id, goal.projectId) == 0)
            }
            val old = dao.goal(goal.metadata.id)
            val metadata = nextMetadata(goal.metadata, old?.metadata, now())
            val row = GoalEntity(metadata, codec.encrypt(goal.title, "goals", metadata.id, "title"),
                goal.description?.let { codec.encrypt(it, "goals", metadata.id, "description") },
                goal.projectId, goal.targetAt, goal.priority, goal.status.name)
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun deleteProject(id: String, revision: Long) = delete { dao.deleteProject(id, revision) }
    override suspend fun deleteGoal(id: String, revision: Long) = delete { dao.deleteGoal(id, revision) }

    private suspend fun delete(operation: suspend () -> Int) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), codec)
            if (operation() != 1) throw RevisionConflictException()
        }
    }
}

private fun StoredMetadata.domain() = RecordMetadata(id, createdAt, updatedAt, revision)
