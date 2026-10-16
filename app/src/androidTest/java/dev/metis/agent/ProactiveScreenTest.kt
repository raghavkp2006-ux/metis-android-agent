package dev.metis.agent

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import dev.metis.agent.platform.ProactiveNoticeLedger
import dev.metis.agent.platform.ProactiveRuntime
import dev.metis.agent.domain.storage.SavedTask
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProactiveScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend() }


    @Before fun resetOptIn() {
        ProactiveRuntime.setEnabled(composeRule.activity, false)
        composeRule.activity.getSharedPreferences(AppIsolation.name(ProactiveNoticeLedger.PREFERENCES_NAME), 0).edit().clear().commit()
    }

    @After fun disableOptIn() {
        ProactiveRuntime.setEnabled(composeRule.activity, false)
        composeRule.activity.getSystemService(NotificationManager::class.java).cancel("proactive", 0)
        composeRule.activity.getSharedPreferences(AppIsolation.name(ProactiveNoticeLedger.PREFERENCES_NAME), 0).edit().clear().commit()
    }

    @Test fun optInDefaultsOffAndEnableDisableSchedulesChecks() {
        assertFalse(ProactiveRuntime.enabled(composeRule.activity))
        composeRule.navigateWorkspace("SETTINGS")
        composeRule.onNodeWithText("Proactive updates").performScrollTo().assertIsDisplayed()
        val toggle = composeRule.onNodeWithTag("proactive_toggle").performScrollTo()
        toggle.performClick()
        composeRule.waitUntil(TIMEOUT) {
            ProactiveRuntime.enabled(composeRule.activity) && periodicWork().isNotEmpty()
        }
        assertTrue(periodicWork().any { !it.state.isFinished })
        composeRule.onNodeWithTag("proactive_toggle").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            !ProactiveRuntime.enabled(composeRule.activity) && periodicWork().all { it.state.isFinished }
        }
    }

    @Test fun enabledNoticeKeepsTaskTitlePrivateAndSuppressesSameItem() = runBlocking {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                composeRule.activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val repository = PersonalStorage.repository(composeRule.activity)
        val now = System.currentTimeMillis()
        val secretTitle = "Synthetic private proactive task"
        val task = SavedTask(secretTitle, dueAt = now + 3 * 60 * 60 * 1_000L, dueZoneId = "UTC")
        repository.saveTask(task)
        try {
            ProactiveRuntime.evaluate(composeRule.activity, now)
            assertTrue(composeRule.activity.getSystemService(NotificationManager::class.java)
                .activeNotifications.none { it.tag == "proactive" })
            ProactiveRuntime.setEnabled(composeRule.activity, true)
            ProactiveRuntime.evaluate(composeRule.activity, now)
            val manager = composeRule.activity.getSystemService(NotificationManager::class.java)
            composeRule.waitUntil(TIMEOUT) {
                manager.activeNotifications.any { it.tag == "proactive" }
            }
            val notice = manager.activeNotifications.single { it.tag == "proactive" }.notification
            assertEquals("METIS update", notice.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("A saved task deadline is approaching.", notice.extras.getString(Notification.EXTRA_TEXT))
            assertFalse(notice.extras.toString().contains(secretTitle))
            assertEquals(Notification.VISIBILITY_PRIVATE, notice.visibility)
            assertNotNull(notice.publicVersion)
            assertFalse(notice.publicVersion.extras.toString().contains(secretTitle))

            val firstPostAt = composeRule.activity.getSharedPreferences(
                AppIsolation.name(ProactiveNoticeLedger.PREFERENCES_NAME), 0).getLong("last_posted", 0L)
            ProactiveRuntime.evaluate(composeRule.activity, now + 60 * 60 * 1_000L)
            assertEquals(firstPostAt, composeRule.activity.getSharedPreferences(
                AppIsolation.name(ProactiveNoticeLedger.PREFERENCES_NAME), 0).getLong("last_posted", 0L))
        } finally {
            ProactiveRuntime.setEnabled(composeRule.activity, false)
            repository.deleteTask(task.metadata.id, task.metadata.revision)
        }
    }

    private fun periodicWork() = WorkManager.getInstance(composeRule.activity)
        .getWorkInfosForUniqueWork(ProactiveRuntime.WORK_NAME).get()

    private companion object { const val TIMEOUT = 10_000L }
}
