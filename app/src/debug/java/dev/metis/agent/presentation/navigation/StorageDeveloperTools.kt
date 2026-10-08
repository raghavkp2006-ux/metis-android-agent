package dev.metis.agent.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import dev.metis.agent.PersonalStorage
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.presentation.designsystem.SecondaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
internal fun StorageDeveloperTools(records: RecordsUiState) {
    val context = LocalContext.current.applicationContext
    val model: DebugStorageViewModel = viewModel(factory = viewModelFactory {
        initializer { DebugStorageViewModel(PersonalStorage.repository(context)) }
    })
    val status by model.status.collectAsStateWithLifecycle()
    Text("Debug storage inspection: ${records.tasks.size} tasks, ${records.schedules.size} blocks, " +
        "${records.memories.size} memories, ${records.dependencies.size} prerequisite links, " +
        "${records.preferences.size} preferences. " +
        "No content is logged or exported.")
    SecondaryButton("Load synthetic records (debug)", model::seed, enabled = !records.loading && !records.failed)
    Text(status)
}

internal class DebugStorageViewModel(private val repository: LocalPersonalRepository) : ViewModel() {
    private val message = MutableStateFlow("Synthetic fixtures are added only to an empty debug database.")
    val status = message.asStateFlow()

    fun seed() {
        viewModelScope.launch {
            try {
                repository.seedForInspection(
                    SavedTask("Synthetic task — review storage", metadata = RecordMetadata(id = TASK_ID)),
                    SavedSchedule(
                        "Synthetic block — storage review", START, START + HOUR, "Asia/Kolkata",
                        "Synthetic development fixture; no reminder or calendar action.",
                        taskId = TASK_ID,
                    ),
                    SavedMemory("Synthetic explicit fact: this is a development fixture."),
                )
                message.value = "Synthetic records saved. Inspect Today, Plan, and You; relaunch to verify persistence."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Seed unavailable. The database must be empty and its key accessible. No data was reset."
            }
        }
    }

    private companion object {
        const val TASK_ID = "00000000-0000-0000-0000-000000000004"
        const val START = 1_791_360_000_000L
        const val HOUR = 3_600_000L
    }
}

private suspend fun LocalPersonalRepository.seedForInspection(
    task: SavedTask, schedule: SavedSchedule, memory: SavedMemory,
) {
    database.withTransaction {
        check(database.records().recordCount() == 0)
        saveTask(task)
        saveSchedule(schedule)
        saveMemory(memory)
    }
}
