package dev.metis.agent

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.domain.agent.AcceptPlanAction
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleStatus
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerScreenTest {
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend() }

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
        awaitPlanProposal()
        composeRule.scrollToDisplayed("Accept local plan")
        assertEquals(1, runBlocking { repository.observeSchedules().first().size })
        assertTrue(runBlocking { repository.foundation.actionRun.observe().first().isEmpty() })
        composeRule.scrollToDisplayed(composeRule.activity.getString(R.string.action_accept)).performClick()
        composeRule.awaitRequestText("Plan saved and verified on this device.", substring = true)
        val block = runBlocking { repository.observeSchedules().first().single {
            it.scoreComponentsJson?.contains(plannedTask.metadata.id) == true
        } }
        assertEquals(null, block.taskId)
        assertEquals(at(10), block.startAt)
        assertTrue(runBlocking { repository.foundation.reminder.observe().first().isEmpty() })
        close()
        composeRule.navigateWorkspace("PLAN")
        composeRule.onNodeWithTag("plan_reminder_${block.metadata.id}")
            .scrollToDisplayed(composeRule).performClick()
        composeRule.onNodeWithTag("composer_input").assertTextContains("Remind me", substring = true)
        composeRule.onNodeWithTag("send_request").performClick()
        awaitReminderProposal()
        composeRule.scrollToDisplayed("Approximate · may be late.")
        assertTrue(runBlocking { repository.foundation.reminder.observe().first().isEmpty() })
        composeRule.scrollToDisplayed(composeRule.activity.getString(R.string.action_accept)).performClick()
        composeRule.awaitRequestText("Approximate reminder registered and verified.", substring = true)
        composeRule.onNodeWithTag("undo_created_task").scrollToDisplayed(composeRule).performClick()
        composeRule.awaitRequestText("Reminder registration cancelled and verified.")
        close()
        composeRule.navigateWorkspace("ACTIVITY", "Timeline")
        composeRule.scrollToDisplayed("PLAN ACCEPTED")
        assertEquals(2, runBlocking { repository.observeSchedules().first().size })
    }

    @Test fun closingPreviewSavesNothingAndPlanEntryButtonsOpenEditableComposer() {
        val repository = PersonalStorage.repository(composeRule.activity)
        runBlocking { repository.saveTask(SavedTask("Synthetic cancelled plan", estimatedSeconds = 600)) }
        submit("plan my evening tomorrow")
        awaitPlanProposal()
        composeRule.scrollToDisplayed("Accept local plan")
        close()
        assertTrue(runBlocking { repository.observeSchedules().first().isEmpty() })
        assertTrue(runBlocking { repository.foundation.event.observe().first().isEmpty() })
        composeRule.navigateWorkspace("PLAN")
        composeRule.scrollToDisplayed("Plan my evening").performClick()
        composeRule.onNodeWithTag("composer_input").assertIsDisplayed()
        composeRule.onAllNodesWithText("Plan my evening").fetchSemanticsNodes().also { assertTrue(it.size >= 2) }
        assertTrue(runBlocking { repository.observeSchedules().first().isEmpty() })
    }

    private fun submit(text: String) {
        composeRule.enterFrontend()
        composeRule.onNodeWithTag("composer_input").performTextInput(text)
        composeRule.onNodeWithTag("send_request").performClick()
    }

    private fun awaitPlanProposal() {
        composeRule.awaitRequestProposal("plan_proposal") { it.action is AcceptPlanAction }
    }

    private fun awaitReminderProposal() {
        composeRule.awaitRequestProposal("reminder_proposal") { it.action is CreateReminderAction }
    }

    private fun close() = composeRule.enterFrontend()
}
