package dev.metis.agent

import android.Manifest
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReminderWorkState
import dev.metis.agent.platform.AndroidReminderPlatform
import dev.metis.agent.platform.ReminderRuntime
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.After
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Host also invokes these methods separately with process stop + emulator reboot between them. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class ReminderRestartTest {
    @After fun cleanup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        PersonalStorage.repository(context).database.clearAllTables()
    }
    @Ignore("Run the two restart stages with a host process/reboot harness; ordinary instrumentation cannot preserve state across per-test cleanup.")
    @Test fun stage1RegisterForHostRestart(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val repository = PersonalStorage.repository(context)
        val agent = ConfirmedReminderAgent(ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions),
            ReminderRuntime.store(context))
        val result = agent.process(AgentRequest(UUID.randomUUID(), "remind me tomorrow at 23:00 to Synthetic restart probe",
            InputSource.TEXT, Instant.now()))
        val receipt = agent.accept(result.proposals.single()).completedActions.single()
        val id = receipt.affectedEntities.single().id.toString()
        val prefs = context.getSharedPreferences("restart-probe", android.content.Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("reminder", id).putString("action", receipt.identity.id.toString()).commit())
        assertEquals(ReminderWorkState.QUEUED, AndroidReminderPlatform(context).registration(UUID.fromString(id))?.state)
    }

    @Ignore("Run the two restart stages with a host process/reboot harness; ordinary instrumentation cannot preserve state across per-test cleanup.")
    @Test fun stage2VerifyAndCleanAfterHostRestart(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("restart-probe", android.content.Context.MODE_PRIVATE)
        val id = UUID.fromString(requireNotNull(prefs.getString("reminder", null)))
        val action = UUID.fromString(requireNotNull(prefs.getString("action", null)))
        val repository = PersonalStorage.repository(context)
        val store = ReminderRuntime.store(context)
        try {
            assertEquals(ReminderWorkState.QUEUED, AndroidReminderPlatform(context).registration(id)?.state)
            store.reconcile()
            val entry = store.history().first().single { it.actionId == action }
            assertEquals("SCHEDULED", entry.status)
            assertTrue(entry.receipt != null)
            assertTrue(!AndroidReminderPlatform(context).posted(id))
        } finally {
            store.cancel(action)
            repository.foundation.reminder.observe().first().single { it.metadata.id == id.toString() }.let {
                repository.foundation.reminder.delete(it.metadata.id, it.metadata.revision)
            }
            repository.foundation.actionRun.observe().first().filter {
                it.metadata.id == action.toString() || it.payload.contains(action.toString())
            }.forEach { repository.foundation.actionRun.delete(it.metadata.id, it.metadata.revision) }
            assertTrue(prefs.edit().clear().commit())
        }
    }
}
