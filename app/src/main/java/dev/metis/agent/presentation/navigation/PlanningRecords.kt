package dev.metis.agent.presentation.navigation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.metis.agent.R
import dev.metis.agent.domain.storage.SavedTask
@Composable
internal fun TaskPlanningLinks(task: SavedTask, records: RecordsUiState) {
    records.projects.firstOrNull { it.metadata.id == task.projectId }?.let {
        Text(stringResource(R.string.task_project, it.title))
    }
    records.goals.firstOrNull { it.metadata.id == task.goalId }?.let {
        Text(stringResource(R.string.task_goal, it.title))
    }
}
@Composable
internal fun PlanningRecords(records: RecordsUiState) {
    if (records.projects.isNotEmpty()) Text(stringResource(R.string.saved_projects))
    records.projects.forEach { project ->
        dev.metis.agent.presentation.designsystem.MetisCard {
            Text(stringResource(R.string.planning_record, project.title, project.status.name),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(project.status.name, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            project.description?.let { Text(it) }
            val related = records.tasks.filter { it.projectId == project.metadata.id }
            Text("${related.size} linked tasks")
            related.forEach { Text(it.title) }
        }
    }
    if (records.goals.isNotEmpty()) Text(stringResource(R.string.saved_goals))
    records.goals.forEach { goal ->
        dev.metis.agent.presentation.designsystem.MetisCard {
            Text(stringResource(R.string.planning_record, goal.title, goal.status.name),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(goal.status.name, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            goal.description?.let { Text(it) }
            val related = records.tasks.filter { it.goalId == goal.metadata.id }
            Text("${related.size} linked tasks")
            related.forEach { Text(it.title) }
        }
    }
}
