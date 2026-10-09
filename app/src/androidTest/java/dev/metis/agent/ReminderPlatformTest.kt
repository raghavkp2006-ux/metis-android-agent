package dev.metis.agent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import dev.metis.agent.domain.agent.ReminderWorkState
import dev.metis.agent.platform.AndroidReminderPlatform
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import org.junit.Ignore
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderPlatformTest {
    @After fun cleanup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        PersonalStorage.repository(context).database.clearAllTables()
    }
    @Test fun registrationContainsOnlyIdAndSurvivesPlatformRecreationThenCancels(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        enablePermission()
        val platform = AndroidReminderPlatform(context)
        assertTrue(platform.notificationsAvailable())
        val id = UUID.randomUUID()
        try {
            val registration = platform.schedule(id, System.currentTimeMillis() + 3_600_000)
            assertEquals(ReminderWorkState.QUEUED, registration.state)
            assertEquals(registration, AndroidReminderPlatform(context).registration(id))
            val request = requireNotNull(WorkManager.getInstance(context)
                .getWorkInfoById(UUID.fromString(registration.token)).get())
            assertEquals(setOf("metis-reminder-$id"), request.tags.filter { it.startsWith("metis-reminder-") }.toSet())
            assertTrue(request.tags.contains("metis-reminder-$id"))
            assertFalse(platform.posted(id))
            // A missing application record must never become a notification when the worker eventually runs.
            platform.cancel(id)
            assertEquals(ReminderWorkState.CANCELLED, platform.registration(id)?.state)
            assertFalse(platform.posted(id))
        } finally { platform.cancel(id) }
    }

    @Test fun postingIsReadBackSeparatelyAndCancellationRemovesIt(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        enablePermission()
        val platform = AndroidReminderPlatform(context)
        val id = UUID.randomUUID()
        try {
            platform.post(id, "Synthetic platform notification")
            repeat(20) { if (!platform.posted(id)) delay(100) }
            assertTrue(platform.posted(id))
            assertEquals(null, platform.registration(id))
            platform.cancel(id)
            repeat(20) { if (platform.posted(id)) delay(100) }
            assertFalse(platform.posted(id))
        } finally { platform.cancel(id) }
    }

    @Ignore("Notification app-op mutation is exercised by the host permission script; API 26 has no runtime notification permission.")
    @Test fun disabledNotificationsPreventRegistration(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        enablePermission()
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = UUID.randomUUID()
        // Exercise Android's real app notification gate and restore it after this isolated device check.
        manager.createNotificationChannel(NotificationChannel(AndroidReminderPlatform.CHANNEL, "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} POST_NOTIFICATION deny")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        try {
            val platform = AndroidReminderPlatform(context)
            assertFalse(platform.notificationsAvailable())
            var rejected = false
            try { platform.schedule(id, System.currentTimeMillis() + 3_600_000) }
            catch (_: IllegalStateException) { rejected = true }
            assertTrue(rejected)
            assertEquals(null, platform.registration(id))
        } finally {
            instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} POST_NOTIFICATION allow")
                .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        }
    }

    private fun enablePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
