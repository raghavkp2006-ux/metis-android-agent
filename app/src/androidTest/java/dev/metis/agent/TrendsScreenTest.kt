@file:Suppress("MagicNumber") // Fixed chart fixtures and 200% font are UI test cases.

package dev.metis.agent

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.DisciplineRepository
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.TrendsViewModel
import dev.metis.agent.presentation.navigation.TrendsWorkspace
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrendsScreenTest {
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<DesignSystemTestActivity>()
    private val today = LocalDate.of(2026, 10, 17)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test fun loadingAndUnreadableKeyUseSafeStates() {
        val records = MemoryDisciplineRepository()
        val gate = CompletableDeferred<Unit>()
        records.readGate = gate
        val model = TrendsViewModel(records, clock) { ZoneOffset.UTC }
        compose.setContent { AgentTheme { TrendsWorkspace(model, compose.activity) } }
        compose.onNodeWithText("Loading trends…").assertIsDisplayed()
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("No trends yet").assertIsDisplayed()
        compose.runOnIdle { records.failReads = true; model.reload() }
        compose.onNodeWithText("Trends could not be opened. Check local storage and try again.")
            .assertIsDisplayed()
        compose.onNodeWithText("No trends yet").assertDoesNotExist()
    }

    @Test fun emptyPopulatedDarkLargeFontAndLowMoodAreReadable() {
        val records = MemoryDisciplineRepository()
        val model = TrendsViewModel(records, clock) { ZoneOffset.UTC }
        val dark = androidx.compose.runtime.mutableStateOf(false)
        val scale = androidx.compose.runtime.mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                AgentTheme(darkTheme = dark.value) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
                            TrendsWorkspace(model, compose.activity)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("No trends yet").assertIsDisplayed()
        screenshot("trends-empty.png")

        val habit = HabitDefinition("Read", today.minusDays(60))
        records.habits += habit
        (0..7).forEach { offset ->
            val day = today.minusDays(offset.toLong())
            val checkin = DailyCheckin(day, ZoneOffset.UTC.id, if (offset < 4) 2 else 4)
            val ticks = if (offset < 4) listOf(HabitTick(checkin.metadata.id, habit.metadata.id, true))
                else emptyList()
            records.checkins += CheckinDay(checkin, ticks)
        }
        compose.runOnIdle { model.reload() }
        compose.onNodeWithTag("trends_mood_summary").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("trends_completion_summary").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Weekday pattern · last 90 days").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mood tends to be lower on days you did Read " +
            "(4 done days, 4 not done days).").performScrollTo().assertIsDisplayed()
        val chartDescriptions = compose.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("weekly chart description") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text ->
                    text.contains("mood") && text.contains("observations")
                } == true
            }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Mood chart needs text descriptions", chartDescriptions.isNotEmpty())
        compose.onNodeWithTag("trends_insight_low-mood").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
        screenshot("trends-low-mood.png")
        compose.onNodeWithTag("trends_mood_summary").performScrollTo().assertIsDisplayed()
        screenshot("trends-populated.png")
        compose.runOnIdle { dark.value = true }
        screenshot("trends-dark.png")
        compose.runOnIdle { scale.floatValue = 2f }
        compose.onNodeWithTag("trends_mood_summary").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Trends").performScrollTo().assertIsDisplayed()
        screenshot("trends-font-200.png")
        compose.onNodeWithTag("trends_insights_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("trends_insight_low-mood").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(compose.activity.filesDir, name)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class MemoryDisciplineRepository : DisciplineRepository {
        val habits = mutableListOf<HabitDefinition>()
        val checkins = mutableListOf<CheckinDay>()
        var readGate: CompletableDeferred<Unit>? = null
        var failReads = false
        override suspend fun habits(range: DisciplineDateRange): List<HabitDefinition> {
            readGate?.await()
            if (failReads) error("unreadable key")
            return habits.filter {
            it.startLocalDate <= range.through && (it.archivedAt == null || it.archivedAt > range.from)
            }
        }
        override suspend fun checkins(range: DisciplineDateRange): List<CheckinDay> {
            if (failReads) error("unreadable key")
            return checkins.filter { it.checkin.localDate in range.from..range.through }
        }
        override suspend fun saveHabit(habit: HabitDefinition): HabitDefinition = error("read-only fixture")
        override suspend fun archiveHabit(id: String, revision: Long): HabitDefinition = error("read-only fixture")
        override suspend fun restoreHabit(id: String, revision: Long): HabitDefinition = error("read-only fixture")
        override suspend fun deleteHabit(id: String, revision: Long): Unit = error("read-only fixture")
        override suspend fun saveCheckin(checkin: DailyCheckin, completions: Map<String, Boolean>): DailyCheckin = error("read-only fixture")
        override suspend fun setTick(checkinId: String, revision: Long, habitId: String, completed: Boolean): DailyCheckin = error("read-only fixture")
        override suspend fun deleteCheckin(id: String, revision: Long): Unit = error("read-only fixture")
    }
}
