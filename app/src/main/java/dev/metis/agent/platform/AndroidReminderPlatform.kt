package dev.metis.agent.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.metis.agent.MainActivity
import dev.metis.agent.domain.agent.ReminderPlatform
import dev.metis.agent.domain.agent.ReminderRegistration
import dev.metis.agent.domain.agent.ReminderWorkState
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidReminderPlatform(context: Context) : ReminderPlatform {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val work get() = WorkManager.getInstance(app)

    override fun notificationsAvailable(): Boolean {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT))
        return NotificationManagerCompat.from(app).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }

    override suspend fun registration(reminderId: UUID): ReminderRegistration? = withContext(Dispatchers.IO) {
        val infos = work.getWorkInfosForUniqueWork(name(reminderId)).get()
        val active = infos.filter { !it.state.isFinished }
        check(active.size <= 1)
        val current = active.singleOrNull() ?: infos.maxByOrNull { it.generation }
        current?.let { ReminderRegistration(it.id.toString(), state(it.state)) }
    }

    override suspend fun schedule(reminderId: UUID,
        triggerAt: Long): ReminderRegistration = withContext(Dispatchers.IO) {
        check(triggerAt > System.currentTimeMillis() && notificationsAvailable())
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(triggerAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(REMINDER_ID to reminderId.toString()))
            .addTag(name(reminderId)).build()
        work.enqueueUniqueWork(name(reminderId), ExistingWorkPolicy.KEEP, request).result.get()
        val info = requireNotNull(registration(reminderId))
        check(info.state == ReminderWorkState.QUEUED || info.state == ReminderWorkState.RUNNING)
        info
    }

    override suspend fun cancel(reminderId: UUID): Unit = withContext(Dispatchers.IO) {
        work.cancelUniqueWork(name(reminderId)).result.get()
        check(registration(reminderId)?.state !in setOf(ReminderWorkState.QUEUED, ReminderWorkState.RUNNING))
        manager.cancel(name(reminderId), 0)
    }

    override fun posted(reminderId: UUID) = manager.activeNotifications.any { it.tag == name(reminderId) && it.id == 0 }

    // Rechecked at dispatch; denial cannot produce a verified post.
    @android.annotation.SuppressLint("MissingPermission")
    override fun post(reminderId: UUID, title: String) {
        check(notificationsAvailable())
        val intent = PendingIntent.getActivity(app, reminderId.hashCode(), Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(app, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("METIS reminder").setContentText(title).setContentIntent(intent).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).build()
        manager.notify(name(reminderId), 0, notification)
    }

    private fun state(state: WorkInfo.State) = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ReminderWorkState.QUEUED
        WorkInfo.State.RUNNING -> ReminderWorkState.RUNNING
        WorkInfo.State.SUCCEEDED -> ReminderWorkState.FINISHED
        WorkInfo.State.CANCELLED -> ReminderWorkState.CANCELLED
        WorkInfo.State.FAILED -> ReminderWorkState.FAILED
    }
    private fun name(id: UUID) = "metis-reminder-$id"
    companion object {
        const val CHANNEL = "metis_reminders"
        const val REMINDER_ID = "reminder_id"
    }
}
