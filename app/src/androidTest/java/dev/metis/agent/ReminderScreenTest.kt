package dev.metis.agent

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.platform.ReminderRuntime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    @After fun cleanup() = runBlocking { PersonalStorage.repository(composeRule.activity).database.clearAllTables() }

    @Test fun proposalDisclosesPrecisionAndOnlyAcceptedReminderCanBeCancelled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                composeRule.activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val repository = PersonalStorage.repository(composeRule.activity)
        val store = ReminderRuntime.store(composeRule.activity)
        val title = "Synthetic reminder ${UUID.randomUUID()}"
        val before = runBlocking { repository.foundation.actionRun.observe().first().map { it.metadata.id }.toSet() }
        try {
            composeRule.onNodeWithText("Write a request").performClick()
            composeRule.onNodeWithText("What do you want to do?")
                .performTextInput("remind me tomorrow at 08:00 to $title")
            composeRule.onNodeWithText("Send request").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithTag("reminder_proposal").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Approximate · may be late.").performScrollTo().assertIsDisplayed()
            assertTrue(runBlocking { repository.foundation.reminder.observe().first().none { it.title == title } })
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_accept))
                .performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Approximate reminder registered and verified.", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(runBlocking { repository.foundation.reminder.observe().first()
                .single { it.title == title }.schedulingState == "SCHEDULED" })
            composeRule.onNodeWithTag("undo_created_task").performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT) {
                composeRule.onAllNodesWithText("Reminder registration cancelled and verified.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(runBlocking { repository.foundation.reminder.observe().first()
                .single { it.title == title }.schedulingState == "CANCELLED" })
        } finally {
            runBlocking {
                store.history().first().filter { it.title == title && (it.pending || it.receipt != null) }
                    .forEach { store.cancel(it.actionId) }
                repository.foundation.reminder.observe().first().filter { it.title == title }.forEach {
                    repository.foundation.reminder.delete(it.metadata.id, it.metadata.revision)
                }
                repository.foundation.actionRun.observe().first().filter { it.metadata.id !in before }.forEach {
                    repository.foundation.actionRun.delete(it.metadata.id, it.metadata.revision)
                }
            }
        }
    }
    private companion object { const val TIMEOUT = 10_000L }
}
