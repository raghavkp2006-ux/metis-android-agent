package dev.metis.agent

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalDisciplineRepository
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.HabitCheckinViewModel
import dev.metis.agent.presentation.navigation.HabitWorkspace
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Screenshot fixture: renders the real screen over the encrypted repository and writes PNGs for review docs. */
@RunWith(AndroidJUnit4::class)
class HabitCheckinScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<DesignSystemTestActivity>()

    private val models = androidx.lifecycle.ViewModelStore()
    private val fixture = StorageTestFixture()
    private val at = Instant.parse("2026-03-10T12:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 3, 10)
    private val repository = LocalDisciplineRepository(fixture.database, fixture.cipher, clock) { ZoneOffset.UTC }

    @org.junit.After fun cleanup() {
        compose.activityRule.scenario.close()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }
        fixture.close()
    }

    @Test
    fun captureTodayScreen() {
        val model = HabitCheckinViewModel(repository, clock) { ZoneOffset.UTC }.also { models.put("habit", it) }
        compose.setContent {
            AgentTheme { Column(Modifier.verticalScroll(rememberScrollState())) { HabitWorkspace(model) } }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("habit_name").fetchSemanticsNodes().isNotEmpty() }
        save("phase2-today-empty.png")

        val readingId = runBlocking {
            val reading = repository.saveHabit(HabitDefinition("Read 20 pages", LocalDate.of(2026, 3, 5)))
            val gym = repository.saveHabit(HabitDefinition("Evening gym", LocalDate.of(2026, 3, 1),
                HabitCadence.weekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))))
            repository.saveCheckin(DailyCheckin(today, ZoneOffset.UTC.id, 4, "Steady day, walked twice."),
                linkedMapOf(reading.metadata.id to true, gym.metadata.id to false))
            reading.metadata.id
        }
        model.reload()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("habit_archive_$readingId").fetchSemanticsNodes().isNotEmpty()
        }
        save("phase2-today-populated.png")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val dir: File = compose.activity.getExternalFilesDir(null)!!
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
