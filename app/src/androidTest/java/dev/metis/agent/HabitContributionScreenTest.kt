@file:Suppress("MagicNumber") // The fixed 53 by 7 fixture and visual scales are test cases.

package dev.metis.agent

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.discipline.CONTRIBUTION_DAYS
import dev.metis.agent.domain.discipline.HabitContributionCalculator
import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.designsystem.MetisSectionHeader
import dev.metis.agent.presentation.navigation.HabitContributionContent
import dev.metis.agent.presentation.navigation.HabitContributionUiState
import dev.metis.agent.presentation.navigation.HabitCheckinViewModel
import dev.metis.agent.presentation.navigation.HabitWorkspace
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only synthetic in-memory records; the four PNGs are harvested by the guarded test harness. */
@RunWith(AndroidJUnit4::class)
class HabitContributionScreenTest {
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<DesignSystemTestActivity>()

    private val today = LocalDate.of(2026, 10, 17)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun heatmapHasFiftyThreeWeeksSelectsCellsAndRemainsReadableAtLargeFont() {
        val calculator = HabitContributionCalculator(clock, ZoneOffset.UTC)
        val emptyReport = calculator.calculate(emptyList(), emptyList())
        val populatedReport = populatedReport(calculator)
        val report = mutableStateOf(emptyReport)
        val selectedDate = mutableStateOf(today)
        val darkTheme = mutableStateOf(false)
        val fontScale = mutableFloatStateOf(1f)

        compose.setContent {
            val baseDensity = LocalDensity.current
            val scroll = rememberScrollState()
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale.floatValue)) {
                AgentTheme(darkTheme = darkTheme.value) {
                    Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(scroll)) {
                        MetisSectionHeader("Habit progress")
                        HabitContributionContent(
                            HabitContributionUiState(
                                today = report.value.today,
                                loading = false,
                                report = report.value,
                                selectedDate = selectedDate.value,
                            ),
                        ) { selectedDate.value = it }
                    }
                    }
                }
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("contribution_cell")
                .fetchSemanticsNodes().size == CONTRIBUTION_DAYS
        }
        assertEquals(53, compose.onAllNodesWithTag("contribution_week")
            .fetchSemanticsNodes().size)
        assertEquals((0 until CONTRIBUTION_DAYS).map { it.toFloat() },
            compose.onAllNodesWithTag("contribution_cell").fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.TraversalIndex] })
        assertAtMostRecentWeek()
        compose.onNodeWithText("No habit progress yet").assertIsDisplayed()
        compose.onNodeWithTag("contribution_rules").assertDoesNotExist()
        compose.onNodeWithTag("contribution_rules_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("contribution_rules").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("contribution_rules_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("contribution_rules").assertDoesNotExist()
        save("phase3-heatmap-empty.png")

        report.value = populatedReport
        selectedDate.value = today
        compose.waitForIdle()
        compose.onNodeWithTag("contribution_week_start").performScrollTo().assertIsDisplayed()
        assertAtMostRecentWeek()
        assertLegendLabelsFit()
        compose.onNodeWithTag("contribution_week_start").performScrollTo()
        save("phase3-heatmap-populated.png")

        val firstCell = compose.onAllNodesWithTag("contribution_cell")
            .fetchSemanticsNodes().first()
        val descriptions = firstCell.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue(descriptions.any { it.contains("1 of 4 habits") })
        val density = compose.activity.resources.displayMetrics.density

        val firstCellDescription = descriptions.first { it.contains("1 of 4 habits") }
        compose.onNodeWithContentDescription(firstCellDescription).performScrollTo().performClick()
        val visibleFirstCell = compose.onNodeWithContentDescription(firstCellDescription).fetchSemanticsNode()
        assertTrue(visibleFirstCell.boundsInRoot.width / density >= 48f)
        assertTrue(visibleFirstCell.boundsInRoot.height / density >= 48f)
        compose.onNodeWithTag("contribution_day_details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 of 4 expected habits · 25%").assertIsDisplayed()
        compose.onNodeWithText("Read 20 pages").assertIsDisplayed()

        compose.onNodeWithTag("contribution_jump_today").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(today, selectedDate.value)
        assertAtMostRecentWeek()
        compose.onNodeWithTag("contribution_grid").performScrollTo()
        compose.onNodeWithContentDescription("Sat 17 Oct, 0 of 4 habits").assertIsDisplayed().assertIsSelected()

        darkTheme.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("contribution_week_start").performScrollTo().assertIsDisplayed()
        save("phase3-heatmap-dark.png")

        compose.onNodeWithContentDescription(firstCellDescription).performScrollTo().performClick()
        fontScale.floatValue = 2f
        darkTheme.value = false
        compose.waitForIdle()
        compose.onNodeWithTag("contribution_day_details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Read 20 pages").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("contribution_week_start").performScrollTo().assertIsDisplayed()
        val weekdayLabels = compose.onAllNodesWithTag("contribution_weekday_label").fetchSemanticsNodes()
        assertEquals(7, weekdayLabels.size)
        assertTrue(weekdayLabels.all { it.boundsInRoot.height / density >= 48f })
        val largeFontCell = compose.onAllNodesWithTag("contribution_cell").fetchSemanticsNodes().first()
        assertTrue(largeFontCell.boundsInRoot.width / density >= 48f)
        assertTrue(largeFontCell.boundsInRoot.height / density >= 48f)
        assertLegendLabelsFit()
        compose.onNodeWithTag("contribution_jump_today").performScrollTo().performClick()
        compose.waitForIdle()
        assertAtMostRecentWeek()
        // At 200%, show every wrapped legend label and the selected-day details in the capture.
        compose.onNodeWithTag("contribution_day_details").performScrollTo()
        compose.onNodeWithTag("contribution_legend_title").performScrollTo()
        save("phase3-heatmap-font-200.png")
    }

    private fun assertAtMostRecentWeek() {
        val range = compose.onNodeWithTag("contribution_grid").fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        assertEquals(range.maxValue(), range.value(), 0f)
    }

    private fun assertLegendLabelsFit() {
        val labels = listOf("No data / none", ">0–<25%", "25–49%", "50–74%", "75%+")
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        labels.forEach { label ->
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Legend label overflows: $label; layouts=" + layouts.map {
                "size=${it.size}, paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}, " +
                    "widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}"
            }, layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right)
        }
    }

    @Test
    fun savedTickRefreshesProgressWithoutLeavingTheWorkspace() {
        val repository = PersonalStorage.repository(compose.activity).discipline
        val systemClock = Clock.systemUTC()
        val zone = ZoneId.systemDefault()
        val localToday = systemClock.instant().atZone(zone).toLocalDate()
        val habit = runBlocking { repository.saveHabit(HabitDefinition("Read", localToday)) }
        val model = HabitCheckinViewModel(repository, systemClock) { zone }
        compose.runOnIdle { compose.activity.viewModelStore.put("phase3_checkin", model) }
        compose.setContent {
            AgentTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
                        HabitWorkspace(model)
                    }
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("checkin_tick_${habit.metadata.id}").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("checkin_tick_${habit.metadata.id}").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("contribution_day_details").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("contribution_cell").fetchSemanticsNodes().any {
                    it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                        .any { label -> label.contains("1 of 1 habit") }
                }
        }
        compose.onNodeWithTag("contribution_day_details").performScrollTo()
        compose.onNodeWithText("1 of 1 expected habit · 100%").assertIsDisplayed()
    }

    private fun populatedReport(calculator: HabitContributionCalculator) = with(calculator) {
        val range = range(today)
        val habits = listOf("Read 20 pages", "Walk", "Stretch", "Journal")
            .map { HabitDefinition(it, range.from) }
        val checkins = buildList {
            var date = range.from
            while (!date.isAfter(today)) {
                val record = DailyCheckin(date, ZoneOffset.UTC.id)
                val completedCount = if (date == range.from) 1 else (date.dayOfYear % (habits.size + 1))
                add(CheckinDay(record, habits.take(completedCount).map { habit ->
                    HabitTick(record.metadata.id, habit.metadata.id, true)
                }))
                date = date.plusDays(1)
            }
        }
        calculate(habits, checkins, today)
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val output = File(compose.activity.filesDir, name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
