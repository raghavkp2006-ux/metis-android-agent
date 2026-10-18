package dev.metis.agent.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.metis.agent.MainActivity
import dev.metis.agent.domain.proactive.ProactiveCandidate
import dev.metis.agent.domain.proactive.ProactiveKind
import dev.metis.agent.domain.proactive.ProactivePriority

/** Owns Android-specific permission checks, channel priority and lock-screen-safe notification formatting. */
internal object ProactiveNotificationPoster {
    private val CHANNEL_LOW = dev.metis.agent.AppIsolation.name("metis_proactive_low")
    private val CHANNEL_DEFAULT = dev.metis.agent.AppIsolation.name("metis_proactive_default")
    private val CHANNEL_URGENT = dev.metis.agent.AppIsolation.name("metis_proactive_urgent")
    private const val TAG = "proactive"
    private const val NOTIFICATION_ID = 0
    private const val PENDING_INTENT_REQUEST = 7001
    private const val API_TIRAMISU = 33

    fun available(context: Context, priority: ProactivePriority): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannels(manager)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && channelAllowed(manager, priority) &&
            runtimePermissionAllowed(context)
    }

    @SuppressLint("MissingPermission")
    fun post(context: Context, candidate: ProactiveCandidate) {
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannels(manager)
        val intent = PendingIntent.getActivity(context, PENDING_INTENT_REQUEST,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = when (candidate.kind) {
            ProactiveKind.MISSED_TASK -> "A saved task deadline has passed."
            ProactiveKind.TASK_DEADLINE -> "A saved task deadline is approaching."
            ProactiveKind.PROMISE_DUE -> "A saved promise is due soon."
            ProactiveKind.SCHEDULE_CONFLICT -> "Accepted schedule blocks overlap."
            ProactiveKind.REPEATED_POSTPONEMENT -> "A task deadline has been postponed several times."
        }
        val publicVersion = NotificationCompat.Builder(context, channel(candidate.priority))
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("METIS update")
            .setContentText("Open METIS to review a saved item.").build()
        val notification = NotificationCompat.Builder(context, channel(candidate.priority))
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("METIS update")
            .setContentText(body).setPublicVersion(publicVersion).setContentIntent(intent).setAutoCancel(true)
            .setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(candidate.priority.compatPriority()).build()
        NotificationManagerCompat.from(context).notify(TAG, NOTIFICATION_ID, notification)
    }

    private fun channel(priority: ProactivePriority) = when (priority) {
        ProactivePriority.LOW -> CHANNEL_LOW
        ProactivePriority.MEDIUM, ProactivePriority.IMPORTANT -> CHANNEL_DEFAULT
        ProactivePriority.URGENT -> CHANNEL_URGENT
    }

    private fun ProactivePriority.compatPriority() = when (this) {
        ProactivePriority.LOW -> NotificationCompat.PRIORITY_LOW
        ProactivePriority.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
        ProactivePriority.IMPORTANT -> NotificationCompat.PRIORITY_HIGH
        ProactivePriority.URGENT -> NotificationCompat.PRIORITY_MAX
    }

    private fun createChannels(manager: NotificationManager) {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_LOW, "Proactive updates · low",
            NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_DEFAULT, "Proactive updates",
            NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_URGENT, "Proactive updates · urgent",
            NotificationManager.IMPORTANCE_HIGH))
    }

    private fun channelAllowed(manager: NotificationManager, priority: ProactivePriority) =
        manager.getNotificationChannel(channel(priority))?.importance != NotificationManager.IMPORTANCE_NONE

    private fun runtimePermissionAllowed(context: Context) = Build.VERSION.SDK_INT < API_TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
