package dev.metis.agent

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalDisciplineRepository
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.BACKFILL_REJECTED
import dev.metis.agent.presentation.navigation.HabitCheckinViewModel
import dev.metis.agent.presentation.navigation.HabitWorkspace
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose + real encrypted repository fixture. It never opens or clears the installed app's database. */
@RunWith(AndroidJUnit4::class)
class HabitCheckinScreenTest {
    @get:Rule val compose = createAndroidComposeRule<DesignSystemTestActivity>()

    private val models = androidx.lifecycle.ViewModelStore()
    private val fixture = StorageTestFixture()
    private val at = Instant.parse("2026-03-10T12:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 3, 10)
    private val window = DisciplineDateRange(today.minusDays(7), today)
    private val repository = LocalDisciplineRepository(fixture.database, fixture.cipher, clock) { ZoneOffset.UTC }
    private val model = HabitCheckinViewModel(repository, clock) { ZoneOffset.UTC }.also { models.put("habit", it) }

    @After fun cleanup() {
        compose.activityRule.scenario.close()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }
        fixture.close()
    }

    private fun show(density: Density = Density(1f, 1f)) {
        compose.setContent {
            AgentTheme {
                CompositionLocalProvider(LocalDensity provides density) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { HabitWorkspace(model) }
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("habit_name").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun expectToggle(tag: String, expected: Boolean) {
        val target = if (expected) ToggleableState.On else ToggleableState.Off
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrNull(SemanticsProperties.ToggleableState) == target
        }
    }

    @Test
    fun createTickUntickAndBackfillRejection() {
        show()
        compose.onNodeWithTag("habit_name").performTextInput("Drink water")
        compose.onNodeWithTag("habit_save").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Drink water").fetchSemanticsNodes().isNotEmpty() }
        val id = runBlocking { repository.habits(window).single().metadata.id }
        val row = compose.onNodeWithTag("checkin_tick_$id")
        row.performScrollTo().assertIsDisplayed()
        expectToggle("checkin_tick_$id", false)
        row.performClick()
        expectToggle("checkin_tick_$id", true)
        assertEquals(true, runBlocking { repository.checkins(window).single().ticks.single().completed })
        row.performClick()
        expectToggle("checkin_tick_$id", false)

        compose.onNodeWithTag("checkin_manual_date").performScrollTo()
            .performTextInput(today.plusDays(1).toString())
        compose.onNodeWithTag("checkin_open_day").performScrollTo().performClick()
        compose.onNodeWithTag("checkin_message").assertTextEquals(BACKFILL_REJECTED)
    }

    @Test
    fun archiveRestoreAndDeleteFlow() {
        show()
        compose.onNodeWithTag("habit_name").performTextInput("Evening walk")
        compose.onNodeWithTag("habit_start").performTextReplacement(today.minusDays(1).toString())
        compose.onNodeWithTag("habit_save").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Evening walk").fetchSemanticsNodes().isNotEmpty() }
        val id = runBlocking { repository.habits(window).single().metadata.id }
        compose.onNodeWithTag("habit_archive_$id").performScrollTo().performClick()
        compose.onNodeWithTag("habit_state_$id").performScrollTo()
        compose.waitUntil(5_000) { runBlocking { repository.habits(window).single().archivedAt != null } }
        val archived = runBlocking { repository.habits(window) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("habit_restore_$id").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(today, archived.single().archivedAt)
        compose.onNodeWithTag("habit_restore_$id").performScrollTo().performClick()
        compose.waitUntil(5_000) { !model.uiState.value.busy && model.uiState.value.habits.size == 2 }
        val restored = runBlocking { repository.habits(window).first { it.metadata.id != id } }
        assertEquals(today, restored.startLocalDate)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("habit_delete_${restored.metadata.id}").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("habit_delete_${restored.metadata.id}").performScrollTo().performClick()
        compose.onNodeWithTag("habit_confirm_delete").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.habits(window).size == 1 } }
        assertEquals(id, runBlocking { repository.habits(window).single().metadata.id })
    }

    @Test
    fun screenUsesTouchTargetsAndSurvivesTwoHundredPercentFont() {
        show(Density(1f, 2f))
        compose.onNodeWithTag("checkin_mood_3").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("habit_cadence_daily").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Manage habits").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("checkin_save").performScrollTo().assertIsDisplayed()
    }
}
