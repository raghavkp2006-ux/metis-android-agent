package dev.metis.agent.platform

import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.metis.agent.PersonalStorage
import dev.metis.agent.domain.proactive.ProactiveCandidate
import dev.metis.agent.domain.proactive.ProactiveEngine
import dev.metis.agent.domain.proactive.ProactivePostponement
import dev.metis.agent.domain.storage.SavedActionRun
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import org.json.JSONObject

object ProactiveRuntime {
    internal val WORK_NAME = dev.metis.agent.AppIsolation.name("metis-proactive-review")
    private val INITIAL_WORK_NAME = "$WORK_NAME-initial"
    private const val REVIEW_INTERVAL_HOURS = 6L
    private val postingLock = Any()

    fun enabled(context: Context) = ProactiveNoticeLedger.enabled(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        ProactiveNoticeLedger.setEnabled(context, enabled)
        val manager = WorkManager.getInstance(context.applicationContext)
        if (enabled) {
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ProactiveWorker>(REVIEW_INTERVAL_HOURS, TimeUnit.HOURS).build())
            manager.enqueueUniqueWork(INITIAL_WORK_NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ProactiveWorker>().build())
        } else {
            manager.cancelUniqueWork(WORK_NAME)
            manager.cancelUniqueWork(INITIAL_WORK_NAME)
            context.getSystemService(NotificationManager::class.java).cancel("proactive", 0)
        }
    }

    suspend fun evaluate(context: Context, now: Long = System.currentTimeMillis()) {
        if (!enabled(context)) return
        val repository = PersonalStorage.repository(context.applicationContext)
        val actions = repository.foundation.actionRun.observe().first()
        val postponements = actions.mapNotNull { it.toProactivePostponement() }
        val candidates = ProactiveEngine.candidates(repository.observeTasks().first(),
            repository.foundation.promise.observe().first(), repository.observeSchedules().first(), postponements, now)
        synchronized(postingLock) {
            if (enabled(context)) {
                val candidate = candidates.firstOrNull { ProactiveNoticeLedger.allows(context, it, now) &&
                    ProactiveNotificationPoster.available(context, it.priority) }
                if (candidate != null && ProactiveNoticeLedger.reserve(context, candidate, now)) {
                    ProactiveNotificationPoster.post(context, candidate)
                }
            }
        }
    }

    private fun SavedActionRun.toProactivePostponement(): ProactivePostponement? = takeIf {
        it.actionType == "TASK" && it.status == "SUCCEEDED" && it.safeErrorCode != "PERSONAL_DATA_REMOVED" &&
            it.entityType == "TASK"
    }?.let { run ->
        run.finishedAt?.let { timestamp ->
            run.entityId?.takeIf { run.isTaskPostponement() }?.let { ProactivePostponement(it, timestamp) }
        }
    }

    private fun SavedActionRun.isTaskPostponement() = runCatching {
        val payloadJson = JSONObject(payload)
        payloadJson.optString("operation") == "CONFIRMED_TASK_EDIT_V1" &&
            payloadJson.optString("kind") == "POSTPONE"
    }.getOrDefault(false)
}
