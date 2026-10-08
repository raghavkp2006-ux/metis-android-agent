package dev.metis.agent.domain.storage

import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class ProjectStatus { ACTIVE, COMPLETED, ARCHIVED }
enum class GoalStatus { ACTIVE, ACHIEVED, CANCELLED }

data class SavedProject(
    val title: String,
    val description: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val metadata: RecordMetadata = RecordMetadata(),
) {
    init { validatePlanningContent(title, description) }
}

data class SavedGoal(
    val title: String,
    val description: String? = null,
    val projectId: String? = null,
    val targetAt: Long? = null,
    val priority: Int = 0,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val metadata: RecordMetadata = RecordMetadata(),
) {
    init {
        validatePlanningContent(title, description)
        projectId?.let { require(UUID.fromString(it).toString() == it) }
        require(priority in 0..MAX_PLANNING_PRIORITY)
    }
}

interface PlanningRepository {
    fun observeProjects(): Flow<List<SavedProject>>
    fun observeGoals(): Flow<List<SavedGoal>>
    suspend fun saveProject(project: SavedProject)
    suspend fun saveGoal(goal: SavedGoal)
    suspend fun deleteProject(id: String, revision: Long)
    suspend fun deleteGoal(id: String, revision: Long)
}

private fun validatePlanningContent(title: String, description: String?) {
    require(title.isNotBlank() && title.length <= MAX_PLANNING_CONTENT)
    require(description == null || description.length <= MAX_PLANNING_CONTENT)
}

private const val MAX_PLANNING_CONTENT = 4_000
private const val MAX_PLANNING_PRIORITY = 3
