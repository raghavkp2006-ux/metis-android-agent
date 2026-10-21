package dev.metis.agent

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.CALENDAR_REASON
import dev.metis.agent.presentation.navigation.CalendarEditorViewModel
import dev.metis.agent.presentation.navigation.CalendarWorkspace
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose uses a fresh encrypted Room database and Android Keystore key for every test. */
@RunWith(AndroidJUnit4::class)
class CalendarOverlapScreenTest {
    @get:Rule val compose = createAndroidComposeRule<DesignSystemTestActivity>()
    private val fixture = StorageTestFixture()
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.now(zone)
    private val dark = mutableStateOf(false)
    private val fontScale = mutableFloatStateOf(1f)
    private lateinit var model: CalendarEditorViewModel

    @After fun close() {
        // @After runs inside the ActivityScenario rule: stop its Room collector before closing Room.
        compose.activityRule.scenario.close()
        fixture.close()
    }

    @Test fun createConflictListsEverySavedItemAndDoesNotSaveSilently() {
        seed(event("Early commitment", "09:10", "09:40"), event("Later commitment", "09:45", "10:15"))
        showCalendar()
        startCreate()
        saveDraft()
        awaitConflict()
        listOf("Early commitment", "Later commitment").forEach { title ->
            val id = rows().single { it.title == title }.metadata.id
            compose.onNodeWithTag("calendar_conflict_item_$id").assertIsDisplayed()
                .assertTextContains(title, substring = true)
        }
        assertEquals(2, rows().size)
        screenshot("phase6a-conflict-dialog.png")
    }

    @Test fun editConflictExcludesTheEditedEventAndShowsTheOtherItem() {
        val editable = event("Editable", "08:00", "09:00")
        seed(editable, event("Blocker", "09:30", "10:30"))
        showCalendar()
        awaitTag("calendar_edit_${editable.metadata.id}")
        compose.onNodeWithTag("calendar_edit_${editable.metadata.id}").performScrollTo().performClick()
        compose.onNodeWithTag("calendar_start_time").performTextReplacement("09:00")
        compose.onNodeWithTag("calendar_end_time").performTextReplacement("10:00")
        saveDraft()
        awaitConflict()
        compose.onNodeWithTag("calendar_conflict_item_${rows().single { it.title == "Blocker" }.metadata.id}")
            .assertIsDisplayed().assertTextContains("Blocker", substring = true)
        assertEquals(2, rows().size)
        assertEquals("08:00", localTime(rows().single { it.metadata.id == editable.metadata.id }.startAt))
    }

    @Test fun saveAnywayPersistsAndDerivesAnIndicatorFromSavedRows() {
        seed(event("Existing", "09:30", "10:30"))
        showCalendar()
        startCreate()
        saveDraft()
        awaitConflict()
        compose.onNodeWithTag("calendar_conflict_save").performClick()
        awaitRows(2)
        compose.onNodeWithText("Calendar updated.").assertIsDisplayed()
        val saved = rows().single { it.title == "New event" }
        awaitTag("calendar_conflict_${saved.metadata.id}")
        compose.onNodeWithTag("calendar_conflict_${saved.metadata.id}").performScrollTo().assertIsDisplayed()
        screenshot("phase6a-conflict-indicator.png")
    }

    @Test fun changeTimeKeepsDraftAndSavesAtClearBoundary() {
        seed(event("Existing", "09:30", "10:30"))
        showCalendar()
        startCreate()
        saveDraft()
        awaitConflict()
        compose.onNodeWithTag("calendar_conflict_change").performClick()
        compose.onNodeWithTag("calendar_start_time").performTextReplacement("10:30")
        compose.onNodeWithTag("calendar_end_time").performTextReplacement("11:30")
        saveDraft()
        awaitRows(2)
        compose.onNodeWithText("Calendar updated.").assertIsDisplayed()
        assertEquals(2, rows().size)
        compose.onNodeWithTag("calendar_conflict_dialog").assertDoesNotExist()
    }

    @Test fun cancelDropsTheDraftWithoutWriting() {
        seed(event("Existing", "09:30", "10:30"))
        showCalendar()
        startCreate()
        saveDraft()
        awaitConflict()
        compose.onNodeWithTag("calendar_conflict_cancel").performClick()
        compose.onNodeWithTag("calendar_conflict_dialog").assertDoesNotExist()
        assertEquals(1, rows().size)
    }

    @Test fun backToBackMinuteBoundarySavesWithoutWarning() {
        seed(event("Starting at ten", "10:00", "11:00"))
        showCalendar()
        startCreate()
        saveDraft()
        awaitRows(2)
        compose.onNodeWithText("Calendar updated.").assertIsDisplayed()
        compose.onNodeWithTag("calendar_conflict_dialog").assertDoesNotExist()
        assertEquals(2, rows().size)
    }

    @Test fun dialogActionsHaveDescriptionsAndFortyEightDpTargetsAtDarkAndTwoHundredPercentFont() {
        seed(event("Existing", "09:30", "10:30"))
        showCalendar()
        startCreate()
        saveDraft()
        awaitConflict()
        val normalTitleHeight = compose.onNodeWithText("Time conflict")
            .fetchSemanticsNode().boundsInRoot.height
        val dialog = compose.onNodeWithTag("calendar_conflict_dialog").fetchSemanticsNode()
        assertTrue(dialog.config[SemanticsProperties.ContentDescription].single().contains("Time conflict"))
        listOf("calendar_conflict_change", "calendar_conflict_save", "calendar_conflict_cancel").forEach { tag ->
            val heightDp = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height /
                compose.activity.resources.displayMetrics.density
            assertTrue("$tag must have a 48dp target", heightDp >= 48f)
        }
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithTag("calendar_conflict_dialog").assertIsDisplayed()
        screenshot("phase6a-conflict-dark.png")
        compose.runOnIdle { fontScale.floatValue = 2f }
        compose.onNodeWithTag("calendar_conflict_dialog").assertIsDisplayed()
        assertTrue(compose.onNodeWithText("Time conflict").fetchSemanticsNode().boundsInRoot.height >=
            normalTitleHeight * 1.5f)
        listOf("calendar_conflict_change", "calendar_conflict_save", "calendar_conflict_cancel").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        screenshot("phase6a-conflict-font-200.png")
    }

    private fun showCalendar() {
        model = CalendarEditorViewModel(fixture.repository, zone)
        compose.setContent {
            val schedules by fixture.repository.observeSchedules().collectAsStateWithLifecycle(emptyList())
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.floatValue)) {
                AgentTheme(darkTheme = dark.value) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            CalendarWorkspace(schedules, model)
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("calendar_add").performScrollTo().assertIsDisplayed()
    }

    private fun startCreate() {
        compose.onNodeWithTag("calendar_add").performScrollTo().performClick()
        compose.onNodeWithTag("calendar_title").performTextInput("New event")
    }

    private fun saveDraft() = compose.onNodeWithText("Save event").performClick()

    private fun seed(vararg events: SavedSchedule) = runBlocking {
        events.forEach { fixture.repository.saveSchedule(it) }
    }

    private fun rows() = runBlocking { fixture.repository.observeSchedules().first() }
    private fun awaitRows(count: Int) = compose.awaitFlowState(
        "$count saved calendar rows", fixture.repository.observeSchedules(),
    ) { it.size == count }
    private fun awaitTag(tag: String) {
        if (tag == "calendar_conflict_dialog") {
            compose.awaitState("calendar conflict review") { model.uiState.value.conflicts.isNotEmpty() }
        } else {
            compose.awaitFlowState("calendar row $tag", fixture.repository.observeSchedules()) { schedules ->
                schedules.any { tag.endsWith(it.metadata.id.toString()) }
            }
        }
        try {
            compose.onNodeWithTag(tag).assertExists()
        } catch (failure: AssertionError) {
            if (tag.startsWith("calendar_conflict_")) {
                runCatching {
                    Log.e("CALENDAR_CONFLICT_DIAGNOSTIC", "missingTag=$tag, savedRows=${rows()}, " +
                        "editorState=${model.uiState.value}")
                    compose.onRoot().printToLog("CALENDAR_CONFLICT_DIAGNOSTIC")
                }.onFailure { Log.e("CALENDAR_CONFLICT_DIAGNOSTIC", "Could not print failure state", it) }
            }
            throw failure
        }
    }
    private fun awaitConflict() {
        awaitTag("calendar_conflict_dialog")
        compose.onNodeWithTag("calendar_conflict_dialog").assertIsDisplayed()
    }

    private fun event(title: String, from: String, until: String): SavedSchedule = SavedSchedule(
        title, instant(from), instant(until), zone.id, CALENDAR_REASON, status = ScheduleStatus.ACCEPTED,
    )

    private fun instant(time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    private fun localTime(at: Long) = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalTime().toString()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(compose.activity.filesDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
