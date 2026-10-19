package dev.metis.agent

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.DisciplineBackupManager
import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.DisciplineExportBounds
import dev.metis.agent.domain.storage.DisciplineImportCounts
import dev.metis.agent.domain.storage.DisciplineRepository
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.DisciplineBackupDocumentAccess
import dev.metis.agent.presentation.navigation.DisciplineBackupPicker
import dev.metis.agent.presentation.navigation.DisciplineBackupWorkspace
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisciplineBackupWorkspaceTest {
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<DesignSystemTestActivity>()
    private val passphrase = "backup password chosen by me"

    @Test fun exportPasswordDialogIsMaskedAndPickerSaveShowsSuccess() {
        val repository = FakeBackupDisciplineRepository()
        val manager = DisciplineBackupManager(repository)
        val picker = FakePicker()
        val documents = FakeDocuments()
        val writeGate = CompletableDeferred<Unit>()
        documents.writeGate = writeGate
        compose.setContent {
            AgentTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DisciplineBackupWorkspace(managerOverride = manager,
                pickerOverride = picker, documentAccessOverride = documents) } }
        }
        compose.onNodeWithTag("discipline_export").performClick()
        compose.onNodeWithText("Encrypt export").assertIsDisplayed()
        compose.onNodeWithTag("export_password").assertIsDisplayed()
        screenshot("phase5-export-dialog.png")
        compose.onNodeWithTag("export_password").performTextInput(passphrase)
        compose.onNodeWithTag("export_password_repeat").performTextInput(passphrase)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Working…").assertIsDisplayed()
        compose.runOnIdle { writeGate.complete(Unit) }
        compose.waitUntil(30_000) { documents.savedBytes != null }
        compose.onNodeWithText("Encrypted export saved.").assertIsDisplayed()
        val bytes = requireNotNull(documents.savedBytes)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(passphrase))
    }

    @Test fun validatedImportPreviewRequiresConfirmationThenShowsSuccess() {
        val date = LocalDate.parse("2026-10-17")
        val habitId = id(1)
        val checkinId = id(2)
        val source = FakeBackupDisciplineRepository()
        source.seedForExport(listOf(HabitDefinition("Backup test habit", date,
            metadata = RecordMetadata(habitId))), listOf(CheckinDay(
            DailyCheckin(date, "UTC", 3, "Backup note", RecordMetadata(checkinId)),
            listOf(HabitTick(checkinId, habitId, true, id(3))))))
        val export = runBlocking { DisciplineBackupManager(source).createExport(passphrase.toCharArray(), "0.1.0", 1) }
        val target = FakeBackupDisciplineRepository()
        val picker = FakePicker()
        val documents = FakeDocuments(importBytes = export)
        compose.setContent {
            AgentTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DisciplineBackupWorkspace(managerOverride = DisciplineBackupManager(target),
                pickerOverride = picker, documentAccessOverride = documents) } }
        }
        compose.onNodeWithTag("discipline_import").performClick()
        compose.onNodeWithText("Unlock export").assertIsDisplayed()
        compose.onNodeWithTag("import_password").performTextInput(passphrase)
        compose.onNodeWithText("Validate").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Review import").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Review import").assertIsDisplayed()
        compose.onNodeWithText("1 habits · 1 check-ins · 1 completion ticks").assertIsDisplayed()
        screenshot("phase5-import-preview.png")
        compose.onNodeWithTag("confirm_discipline_import").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Import complete.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Import complete.", substring = true).assertIsDisplayed()
        screenshot("phase5-success.png")
    }

    @Test fun invalidImportShowsFailureWithoutChangingRecords() {
        val target = FakeBackupDisciplineRepository()
        val picker = FakePicker()
        val documents = FakeDocuments(importBytes = byteArrayOf(1, 2, 3, 4))
        compose.setContent {
            AgentTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DisciplineBackupWorkspace(managerOverride = DisciplineBackupManager(target),
                pickerOverride = picker, documentAccessOverride = documents) } }
        }
        compose.onNodeWithTag("discipline_import").performClick()
        compose.onNodeWithTag("import_password").performTextInput(passphrase)
        compose.onNodeWithText("Validate").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Import failed. The file is corrupted", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Import failed. The file is corrupted", substring = true).assertIsDisplayed()
        assertTrue(target.allStored().first.isEmpty() && target.allStored().second.isEmpty())
        screenshot("phase5-failure.png")
    }

    @Test fun passwordDialogRemainsReachableAt200PercentFontScale() {
        val manager = DisciplineBackupManager(FakeBackupDisciplineRepository())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AgentTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) { DisciplineBackupWorkspace(managerOverride = manager,
                        pickerOverride = FakePicker(), documentAccessOverride = FakeDocuments()) }
                }
            }
        }
        compose.onNodeWithTag("discipline_export").performScrollTo().performClick()
        compose.onNodeWithText("Encrypt export").assertIsDisplayed()
        compose.onNodeWithTag("export_password").assertIsDisplayed()
        compose.onNodeWithTag("export_password_repeat").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class FakeBackupDisciplineRepository : DisciplineRepository {
        private val habits = linkedMapOf<String, HabitDefinition>()
        private val days = linkedMapOf<String, DailyCheckin>()
        private val ticks = linkedMapOf<Pair<String, String>, HabitTick>()
        fun seedForExport(habits: List<HabitDefinition>, checkins: List<CheckinDay>) {
            habits.forEach { this.habits[it.metadata.id] = it }
            checkins.forEach { day ->
                days[day.checkin.metadata.id] = day.checkin
                day.ticks.forEach { ticks[day.checkin.metadata.id to it.habitId] = it }
            }
        }
        fun allStored() = Triple(habits.values.toList(), days.values.toList(), ticks.values.toList())
        override suspend fun exportBounds(): DisciplineExportBounds? {
            val dates = habits.values.map { it.startLocalDate } + days.values.map { it.localDate }
            val ends = habits.values.map { it.archivedAt ?: it.startLocalDate } + days.values.map { it.localDate }
            return if (dates.isEmpty()) null else DisciplineExportBounds(dates.min(), ends.max())
        }
        override suspend fun importRecords(habits: List<HabitDefinition>, checkins: List<CheckinDay>): DisciplineImportCounts {
            habits.forEach { this.habits[it.metadata.id] = it }
            checkins.forEach { day ->
                days[day.checkin.metadata.id] = day.checkin
                day.ticks.forEach { ticks[day.checkin.metadata.id to it.habitId] = it }
            }
            return DisciplineImportCounts(habits.size, checkins.size, checkins.sumOf { it.ticks.size }, 0, 0, 0)
        }
        override suspend fun habits(range: DisciplineDateRange) = habits.values.filter {
            it.startLocalDate <= range.through && (it.archivedAt == null || it.archivedAt > range.from)
        }
        override suspend fun checkins(range: DisciplineDateRange) = days.values.filter {
            it.localDate in range.from..range.through
        }.map { day -> CheckinDay(day, ticks.values.filter { it.checkinId == day.metadata.id }) }
        override suspend fun saveHabit(habit: HabitDefinition): HabitDefinition = error("not used")
        override suspend fun archiveHabit(id: String, revision: Long): HabitDefinition = error("not used")
        override suspend fun restoreHabit(id: String, revision: Long): HabitDefinition = error("not used")
        override suspend fun deleteHabit(id: String, revision: Long): Unit = error("not used")
        override suspend fun saveCheckin(checkin: DailyCheckin, completions: Map<String, Boolean>): DailyCheckin = error("not used")
        override suspend fun setTick(checkinId: String, revision: Long, habitId: String, completed: Boolean): DailyCheckin = error("not used")
        override suspend fun deleteCheckin(id: String, revision: Long): Unit = error("not used")
    }

    private class FakePicker : DisciplineBackupPicker {
        private val uri = Uri.parse("content://fake.discipline/backup")
        override fun createDocument(fileName: String, onResult: (Uri?) -> Unit) = onResult(uri)
        override fun openDocument(onResult: (Uri?) -> Unit) = onResult(uri)
    }

    private class FakeDocuments(private val importBytes: ByteArray = byteArrayOf()) : DisciplineBackupDocumentAccess {
        var savedBytes: ByteArray? = null
        var writeGate: CompletableDeferred<Unit>? = null
        override suspend fun write(uri: Uri, bytes: ByteArray) { writeGate?.await(); savedBytes = bytes.copyOf() }
        override fun open(uri: Uri): InputStream = ByteArrayInputStream(importBytes)
    }

    private fun id(number: Int) = UUID(0, number.toLong()).toString()
}
