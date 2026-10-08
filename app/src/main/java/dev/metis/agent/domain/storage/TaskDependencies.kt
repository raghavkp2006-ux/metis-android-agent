package dev.metis.agent.domain.storage

import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** An explicit stored prerequisite, not a planner decision or permission to act. */
data class SavedTaskDependency(
    val taskId: String,
    val dependsOnTaskId: String,
    val metadata: RecordMetadata = RecordMetadata(),
) {
    init {
        require(UUID.fromString(taskId).toString() == taskId)
        require(UUID.fromString(dependsOnTaskId).toString() == dependsOnTaskId)
        require(taskId != dependsOnTaskId)
    }
}

interface TaskDependencyRepository {
    fun observeDependencies(): Flow<List<SavedTaskDependency>>
    suspend fun saveDependency(dependency: SavedTaskDependency)
    suspend fun deleteDependency(id: String, revision: Long)
}

/** Iterative reachability avoids stack overflow and visits shared prerequisites only once. */
internal object TaskDependencyGraph {
    fun wouldCreateCycle(taskId: String, prerequisiteId: String, edges: List<SavedTaskDependency>): Boolean {
        val outgoing = edges.groupBy { it.taskId }
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        pending.add(prerequisiteId)
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (current == taskId) return true
            if (visited.add(current)) outgoing[current]?.forEach { pending.add(it.dependsOnTaskId) }
        }
        return false
    }
}
