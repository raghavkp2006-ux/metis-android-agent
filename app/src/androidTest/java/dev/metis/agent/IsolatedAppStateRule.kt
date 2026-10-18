package dev.metis.agent

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.metis.agent.platform.ReminderRuntime
import dev.metis.agent.testing.TestSafety
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** Outside the Activity rule: cleanup finishes before a new Activity starts observing Room. */
class IsolatedAppStateRule : ExternalResource() {
    override fun before() = reset()
    override fun after() = reset()

    private fun reset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TestSafety.requireIsolated(context)
        val store = ReminderRuntime.store(context)
        store.history().first().filter { it.pending || it.receipt != null }.forEach { store.cancel(it.actionId) }
        PersonalStorage.repository(context).database.clearAllTables()
    }
}
