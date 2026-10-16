package dev.metis.agent.testing

import android.content.Context
import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.metis.agent.AppIsolation
import dev.metis.agent.PersonalStorage
import dev.metis.agent.domain.storage.SavedTask
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import dev.metis.agent.platform.AndroidReminderPlatform
import dev.metis.agent.platform.ReminderWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class IsolationProofTest {
    @Test fun seedSyntheticIsolationMarker(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TestSafety.requireIsolated(context)
        assertEquals(TestSafety.TARGET, context.packageName)
        assertTrue(context.filesDir.path.contains(TestSafety.TARGET))
        assertTrue(context.cacheDir.path.contains(TestSafety.TARGET))
        val repository = PersonalStorage.repository(context)
        repository.saveTask(SavedTask("Synthetic capability isolation marker"))
        assertTrue(repository.observeTasks().first().any { it.title == "Synthetic capability isolation marker" })
        assertTrue(context.getDatabasePath(AppIsolation.databaseName).exists())
        assertFalse(context.getDatabasePath("metis-personal.db").exists())
        val preferences = context.getSharedPreferences(AppIsolation.name("isolation-proof"), Context.MODE_PRIVATE)
        assertTrue(preferences.edit().putString("synthetic-marker", "synthetic-only").commit())
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(store.containsAlias(AppIsolation.fieldsAlias))
        assertFalse(store.containsAlias("metis.personal.fields.v1"))
        assertFalse(java.io.File("/data/user/0/dev.metis.agent/databases/metis-personal.db").canRead())
        assertTrue(AppIsolation.name("metis_reminders").startsWith(TestSafety.TARGET))
        assertTrue(AppIsolation.name("metis-proactive-review").startsWith(TestSafety.TARGET))
        AndroidReminderPlatform(context).notificationsAvailable()
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(manager.getNotificationChannel(AppIsolation.name("metis_reminders")) != null)
        assertTrue(manager.getNotificationChannel("metis_reminders") == null)
        WorkManager.getInstance(context).enqueueUniqueWork(AppIsolation.name("isolation-proof-work"),
            ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(1, TimeUnit.DAYS).build()).result.get()
    }

    @Test fun verifySyntheticIsolationMarkerSurvivesRestart(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TestSafety.requireIsolated(context)
        assertTrue(PersonalStorage.repository(context).observeTasks().first()
            .any { it.title == "Synthetic capability isolation marker" })
        assertEquals("synthetic-only", context.getSharedPreferences(AppIsolation.name("isolation-proof"),
            Context.MODE_PRIVATE).getString("synthetic-marker", null))
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(store.containsAlias(AppIsolation.fieldsAlias))
        assertFalse(store.containsAlias("metis.personal.fields.v1"))
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AppIsolation.name("isolation-proof-work")).get()
        assertEquals(1, work.size)
        assertFalse(work.single().state.isFinished)
    }
}
