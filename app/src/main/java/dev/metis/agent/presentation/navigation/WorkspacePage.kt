package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.domain.storage.TaskStatus
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.MetisCard
import dev.metis.agent.presentation.designsystem.MetisSectionHeader
import dev.metis.agent.presentation.designsystem.LoadingState
import dev.metis.agent.presentation.designsystem.ErrorState
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.SecondaryButton
import dev.metis.agent.presentation.designsystem.ScheduleBlock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
internal fun WorkspacePage(
    state: ShellUiState, records: RecordsUiState, onReload: () -> Unit,
    onSearch: (String) -> Unit, onSearchMore: () -> Unit, onMemoryFilter: (MemoryOrigin?) -> Unit,
    onDraft: (String) -> Unit, onSelect: (Workspace) -> Unit, onPrivacy: () -> Unit, onBack: () -> Unit,
    onEntity: (EntityReference?) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(AgentSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(AgentSpacing.large)) {
        if (state.privacyOpen) {
            MetisSectionHeader(androidx.compose.ui.res.stringResource(dev.metis.agent.R.string.privacy_title))
            Text(androidx.compose.ui.res.stringResource(dev.metis.agent.R.string.privacy_status))
            Text(androidx.compose.ui.res.stringResource(dev.metis.agent.R.string.privacy_detail))
            Text(androidx.compose.ui.res.stringResource(dev.metis.agent.R.string.draft_privacy))
            SecondaryButton(androidx.compose.ui.res.stringResource(dev.metis.agent.R.string.action_back), onBack)
        } else {
            Text(state.workspace.subtitle, style = MaterialTheme.typography.headlineMedium)
            when {
                records.loading -> LoadingState("Loading your saved data…")
                records.failed -> ErrorState("Your saved data could not be opened.", onReload)
                else -> WorkspaceContent(state, records, onSearch, onSearchMore, onMemoryFilter,
                    onDraft, onSelect, onPrivacy, onEntity)
            }
        }
    }
}

@Composable
private fun WorkspaceContent(
    state: ShellUiState, records: RecordsUiState,
    onSearch: (String) -> Unit, onSearchMore: () -> Unit, onMemoryFilter: (MemoryOrigin?) -> Unit,
    onDraft: (String) -> Unit, onSelect: (Workspace) -> Unit, onPrivacy: () -> Unit,
    onEntity: (EntityReference?) -> Unit,
) {
    when (state.workspace) {
        Workspace.TODAY -> TodayWorkspace(records, onSelect, onEntity, state.selectedEntity)
        Workspace.HABITS -> HabitWorkspace()
        Workspace.PLAN -> {
            MetisCard { ScheduleRecords(records, onDraft) }
            MetisCard { FoundationRecords(ShellDestination.PLAN) }
            MetisSectionHeader("All tasks")
            TaskRecords(records)
        }
        Workspace.MEMORY -> MemoryWorkspace(records, onSearch, onSearchMore, onMemoryFilter)
        Workspace.PEOPLE -> PeopleWorkspace(state.selectedEntity, onEntity)
        Workspace.PROJECTS_GOALS -> {
            if (records.projects.isEmpty() && records.goals.isEmpty()) EmptyState(
                "Your intentions start here", "Saved projects and goals will appear here. No progress is inferred.")
            PlanningRecords(records)
        }
        Workspace.INSIGHTS -> {
            TrendsWorkspace()
            MetisCard { BehavioralReflectionWorkspace() }
        }
        Workspace.ACTIVITY -> ActivityWorkspace()
        Workspace.SETTINGS -> SettingsWorkspace(records, onPrivacy)
        Workspace.CHAT -> Unit
    }
}

@Composable
private fun TodayWorkspace(
    records: RecordsUiState, onSelect: (Workspace) -> Unit,
    onEntity: (EntityReference?) -> Unit, selected: EntityReference?,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var allTasks by rememberSaveable { mutableStateOf(false) }
    Text(today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    val openTasks = records.tasks.filter { it.status == TaskStatus.OPEN }
    MetisCard {
        Text("${openTasks.size} open tasks", style = MaterialTheme.typography.titleLarge)
        Text("Take it one step at a time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val upNext = records.schedules.filter {
        it.status == ScheduleStatus.ACCEPTED && it.endAt > System.currentTimeMillis()
    }.minByOrNull { it.startAt }
    if (upNext != null) {
        MetisSectionHeader("Up next")
        ScheduleBlock(upNext.title, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.of(upNext.zoneId)).format(Instant.ofEpochMilli(upNext.startAt)), "Accepted local schedule")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MetisSectionHeader(if (allTasks) "All tasks" else "Due today & overdue")
        TextButton(onClick = { allTasks = !allTasks },
            modifier = Modifier.testTag("all_tasks_filter").semantics { this.selected = allTasks }) {
            Text(if (allTasks) "Today" else "All tasks")
        }
    }
    val visible = if (allTasks) records.tasks else records.tasks.filter { task ->
        task.status == TaskStatus.OPEN && task.dueAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.of(task.dueZoneId ?: zone.id)).toLocalDate() <= today
        } == true
    }
    if (visible.isEmpty()) EmptyState("Nothing due here", "Use All tasks to see future and undated tasks.")
    visible.forEach { task ->
        MetisCard {
            TaskRecords(records.copy(tasks = listOf(task)))
            TextButton(onClick = { onEntity(EntityReference(MemoryEntityType.TASK,
                UUID.fromString(task.metadata.id))) }) {
                Text("Task details")
            }
            if (selected?.id.toString() == task.metadata.id) {
                Text("Priority: ${task.priority} · ${task.status}")
                Text("Use the composer for supported task changes. Changes require review.")
                TextButton(onClick = { onEntity(null) }) { Text("Close details") }
            }
        }
    }
    MetisCard {
        Text("Make room for what matters", style = MaterialTheme.typography.titleLarge)
        Text("${records.schedules.count { it.endAt > System.currentTimeMillis() }} upcoming saved schedule items")
        SecondaryButton("View plan", { onSelect(Workspace.PLAN) })
    }
    FoundationRecords(ShellDestination.TODAY)
}

@Composable
private fun PeopleWorkspace(selected: EntityReference?, onEntity: (EntityReference?) -> Unit) {
    val context = LocalContext.current.applicationContext
    val model: FoundationRecordsViewModel = viewModel(factory = viewModelFactory {
        initializer { FoundationRecordsViewModel(PersonalStorage.repository(context).foundation) }
    })
    val people by model.uiState.collectAsStateWithLifecycle()
    when {
        people.loading -> LoadingState("Loading people…")
        people.failed -> ErrorState("People could not be loaded.", model::reload)
        people.people.isEmpty() -> EmptyState("Your people, in one place",
            "Saved people will appear here. This view is read-only.")
        else -> people.people.forEach { person ->
            MetisCard {
                Text(person.displayName, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onEntity(EntityReference(MemoryEntityType.PERSON,
                    UUID.fromString(person.metadata.id))) }) {
                    Text("View saved details")
                }
                if (selected?.id.toString() == person.metadata.id) {
                    person.phone?.let { Text(it) }
                    person.email?.let { Text(it) }
                    Text("Saved locally · read-only")
                    TextButton(onClick = { onEntity(null) }) { Text("Close details") }
                }
            }
        }
    }
}

@Composable
private fun ActivityWorkspace() {
    var section by rememberSaveable { mutableStateOf("Timeline") }
    Column {
        listOf("Timeline", "Task actions", "Reminders", "Decisions").forEach { title ->
            FilterChip(selected = section == title, onClick = { section = title }, label = { Text(title) })
        }
    }
    MetisCard {
        when (section) {
            "Timeline" -> EventTimeline()
            "Task actions" -> TaskActionHistory()
            "Reminders" -> ReminderWorkspace()
            else -> DecisionHistory()
        }
    }
}

@Composable
private fun SettingsWorkspace(records: RecordsUiState, onPrivacy: () -> Unit) {
    ProfileCard()
    MetisCard { PreferenceRecords(records.preferences) }
    MetisCard { VoiceSettingsWorkspace() }
    MetisCard { ProactiveWorkspace() }
    MetisCard {
        MetisSectionHeader("Privacy & data")
        Text("Your saved data is local. Conversations are temporary.")
        SecondaryButton("Privacy", onPrivacy, Modifier.testTag("open_privacy"))
        StorageDeveloperTools(records)
    }
}

@Composable
private fun ProfileCard() {
    val context = LocalContext.current.applicationContext
    val model: FoundationRecordsViewModel = viewModel(factory = viewModelFactory {
        initializer { FoundationRecordsViewModel(PersonalStorage.repository(context).foundation) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    MetisCard {
        MetisSectionHeader("Profile")
        when {
            state.loading -> LoadingState("Loading profile…")
            state.failed -> ErrorState("Profile could not be loaded.", model::reload)
            state.profiles.isEmpty() -> Text("METIS · personal local agent")
            else -> state.profiles.forEach { Text(it.displayName); Text("${it.zoneId} · ${it.locale}") }
        }
    }
}
