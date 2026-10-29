package dev.metis.agent.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import dev.metis.agent.domain.agent.ExternalActionLauncher
import dev.metis.agent.domain.agent.ExternalLaunchResult

@Suppress("TooGenericExceptionCaught")
class AndroidExternalActionLauncher(private val context: Context) : ExternalActionLauncher {

    override fun launchTimer(lengthSeconds: Int, message: String): ExternalLaunchResult = try {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, lengthSeconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        ExternalLaunchResult.Success
    } catch (_: ActivityNotFoundException) {
        ExternalLaunchResult.NoHandler("No timer app was found to start this timer.")
    } catch (e: Exception) {
        ExternalLaunchResult.Failed(e.message ?: "Failed to start timer.")
    }

    override fun launchAlarm(hour: Int, minutes: Int, message: String): ExternalLaunchResult = try {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        ExternalLaunchResult.Success
    } catch (_: ActivityNotFoundException) {
        ExternalLaunchResult.NoHandler("No alarm app was found to set this alarm.")
    } catch (e: Exception) {
        ExternalLaunchResult.Failed(e.message ?: "Failed to set alarm.")
    }

    override fun launchDial(phoneNumber: String): ExternalLaunchResult = try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.fromParts("tel", phoneNumber, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        ExternalLaunchResult.Success
    } catch (_: ActivityNotFoundException) {
        ExternalLaunchResult.NoHandler("No phone app was found to dial this number.")
    } catch (e: Exception) {
        ExternalLaunchResult.Failed(e.message ?: "Failed to dial number.")
    }

    override fun launchSendTo(phoneNumber: String, body: String): ExternalLaunchResult = try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.fromParts("smsto", phoneNumber, null)
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        ExternalLaunchResult.Success
    } catch (_: ActivityNotFoundException) {
        ExternalLaunchResult.NoHandler("No messaging app was found to prepare this message.")
    } catch (e: Exception) {
        ExternalLaunchResult.Failed(e.message ?: "Failed to prepare message.")
    }
}
