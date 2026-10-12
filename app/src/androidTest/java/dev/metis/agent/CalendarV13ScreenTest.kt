package dev.metis.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.ScheduleRepository
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.CalendarEditorViewModel
import dev.metis.agent.presentation.navigation.CalendarWorkspace
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose-only fixture; it never opens or clears the installed app's personal database. */
@RunWith(AndroidJUnit4::class)
class CalendarV13ScreenTest {
    @get:Rule val compose = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test fun addEditAndDeleteLocalEventFromCalendar() {
        val repo = FakeSchedules()
        val model = CalendarEditorViewModel(repo)
        compose.setContent {
            val events by repo.rows.collectAsStateWithLifecycle()
            AgentTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CalendarWorkspace(events, model)
            } }
        }
        compose.onNodeWithTag("calendar_add").performScrollTo().performClick()
        compose.onNodeWithTag("calendar_title").performTextInput("Synthetic meeting")
        compose.onNodeWithText("Save event").performClick()
        compose.waitUntil(5_000) { repo.rows.value.size == 1 }
        compose.runOnIdle { assertEquals("Synthetic meeting", repo.rows.value.single().title) }
        val id = repo.rows.value.single().metadata.id
        compose.onNodeWithTag("calendar_edit_$id").performScrollTo().performClick()
        compose.onNodeWithTag("calendar_title").performTextReplacement("Updated synthetic meeting")
        compose.onNodeWithText("Save event").performClick()
        compose.waitUntil(5_000) { repo.rows.value.single().title == "Updated synthetic meeting" }
        compose.onNodeWithTag("calendar_delete_$id").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { repo.rows.value.isEmpty() }
    }

    private class FakeSchedules : ScheduleRepository {
        val rows = MutableStateFlow<List<SavedSchedule>>(emptyList())
        override fun observeSchedules() = rows
        override suspend fun saveSchedule(schedule: SavedSchedule) {
            rows.value = rows.value.filterNot { it.metadata.id == schedule.metadata.id } + schedule
        }
        override suspend fun deleteSchedule(id: String, revision: Long) {
            rows.value = rows.value.filterNot { it.metadata.id == id }
        }
    }
}
