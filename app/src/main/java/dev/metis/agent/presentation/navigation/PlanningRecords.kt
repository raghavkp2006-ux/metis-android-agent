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
        Text(stringResource(R.string.planning_record, project.title, project.status.name))
        project.description?.let { Text(it) }
    }
    if (records.goals.isNotEmpty()) Text(stringResource(R.string.saved_goals))
    records.goals.forEach { goal ->
        Text(stringResource(R.string.planning_record, goal.title, goal.status.name))
        goal.description?.let { Text(it) }
    }
}
