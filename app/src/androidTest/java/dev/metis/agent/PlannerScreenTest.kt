package dev.metis.agent

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.platform.ReminderRuntime
import dev.metis.agent.presentation.navigation.RecordsViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend() }

    @Before fun reset() = runBlocking { PersonalStorage.repository(composeRule.activity).database.clearAllTables() }
    @After fun cleanup() = runBlocking {
        val store = ReminderRuntime.store(composeRule.activity)
        store.history().first().filter { it.pending || it.receipt != null }.forEach { store.cancel(it.actionId) }
        PersonalStorage.repository(composeRule.activity).database.clearAllTables()
    }

    @Test fun realComposerAcceptsConflictFreePlanThenSeparatelyReviewsAcceptsAndCancelsReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                composeRule.activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val repository = PersonalStorage.repository(composeRule.activity)
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(1)
        fun at(hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val plannedTask = SavedTask("Synthetic planned work", dueAt = at(11), dueZoneId = zone.id,
            estimatedSeconds = 1_800, priority = 2)
        runBlocking {
            repository.saveTask(plannedTask)
            repository.saveSchedule(SavedSchedule("Synthetic existing commitment", at(9), at(10), zone.id,
                "Busy", status = ScheduleStatus.ACCEPTED))
        }
        submit("plan my day tomorrow from 09:00 to 12:00")
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("plan_proposal").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Accept local plan").performScrollTo().assertIsDisplayed()
        assertEquals(1, runBlocking { repository.observeSchedules().first().size })
        assertTrue(runBlocking { repository.foundation.actionRun.observe().first().isEmpty() })
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_accept))
            .performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText("Plan saved and verified on this device.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val block = runBlocking { repository.observeSchedules().first().single {
            it.scoreComponentsJson?.contains(plannedTask.metadata.id) == true
        } }
        assertEquals(null, block.taskId)
        assertEquals(at(10), block.startAt)
        assertTrue(runBlocking { repository.foundation.reminder.observe().first().isEmpty() })
        close()
        composeRule.reloadWorkspaceRecords()
        val recordsViewModel = composeRule.runOnIdle {
            ViewModelProvider(composeRule.activity)[RecordsViewModel::class.java]
        }
        composeRule.waitUntil(TIMEOUT) {
            recordsViewModel.uiState.value.schedules.any { it.metadata.id == block.metadata.id }
        }
        composeRule.navigateWorkspace("PLAN")
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("plan_reminder_${block.metadata.id}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("plan_reminder_${block.metadata.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("composer_input").assertTextContains("Remind me", substring = true)
        composeRule.onNodeWithTag("send_request").performClick()
        try {
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithTag("reminder_proposal").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError(composeRule.onRoot().printToString(), failure)
        }
        composeRule.onNodeWithText("Approximate · may be late.").performScrollTo().assertIsDisplayed()
        assertTrue(runBlocking { repository.foundation.reminder.observe().first().isEmpty() })
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_accept))
            .performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText("Approximate reminder registered and verified.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("undo_created_task").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText("Reminder registration cancelled and verified.").fetchSemanticsNodes().isNotEmpty()
        }
        close()
        composeRule.navigateWorkspace("ACTIVITY", "Timeline")
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText("PLAN ACCEPTED").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("PLAN ACCEPTED").performScrollTo().assertIsDisplayed()
        assertEquals(2, runBlocking { repository.observeSchedules().first().size })
    }

    @Test fun closingPreviewSavesNothingAndPlanEntryButtonsOpenEditableComposer() {
        val repository = PersonalStorage.repository(composeRule.activity)
        runBlocking { repository.saveTask(SavedTask("Synthetic cancelled plan", estimatedSeconds = 600)) }
        submit("plan my evening tomorrow")
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("plan_proposal").fetchSemanticsNodes().isNotEmpty()
        }
        close()
        assertTrue(runBlocking { repository.observeSchedules().first().isEmpty() })
        assertTrue(runBlocking { repository.foundation.event.observe().first().isEmpty() })
        composeRule.navigateWorkspace("PLAN")
        composeRule.onNodeWithText("Plan my evening").performScrollTo().performClick()
        composeRule.onNodeWithTag("composer_input").assertIsDisplayed()
        composeRule.onAllNodesWithText("Plan my evening").fetchSemanticsNodes().also { assertTrue(it.size >= 2) }
        assertTrue(runBlocking { repository.observeSchedules().first().isEmpty() })
    }

    private fun submit(text: String) {
        composeRule.enterFrontend()
        composeRule.onNodeWithTag("composer_input").performTextInput(text)
        composeRule.onNodeWithTag("send_request").performClick()
    }
    private fun close() = composeRule.enterFrontend()
    private companion object { const val TIMEOUT = 10_000L }
}
