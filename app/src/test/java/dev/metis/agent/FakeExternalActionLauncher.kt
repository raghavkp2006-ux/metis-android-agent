package dev.metis.agent

import dev.metis.agent.domain.agent.ExternalActionLauncher
import dev.metis.agent.domain.agent.ExternalLaunchResult

class FakeExternalActionLauncher(
    var timerResult: ExternalLaunchResult = ExternalLaunchResult.Success,
    var alarmResult: ExternalLaunchResult = ExternalLaunchResult.Success,
    var dialResult: ExternalLaunchResult = ExternalLaunchResult.Success,
    var sendToResult: ExternalLaunchResult = ExternalLaunchResult.Success,
) : ExternalActionLauncher {
    val launchedTimers = mutableListOf<TimerCall>()
    val launchedAlarms = mutableListOf<AlarmCall>()
    val launchedDials = mutableListOf<String>()
    val launchedMessages = mutableListOf<MessageCall>()

    data class TimerCall(val lengthSeconds: Int, val message: String)
    data class AlarmCall(val hour: Int, val minutes: Int, val message: String)
    data class MessageCall(val phoneNumber: String, val body: String)

    override fun launchTimer(lengthSeconds: Int, message: String): ExternalLaunchResult {
        launchedTimers.add(TimerCall(lengthSeconds, message))
        return timerResult
    }

    override fun launchAlarm(hour: Int, minutes: Int, message: String): ExternalLaunchResult {
        launchedAlarms.add(AlarmCall(hour, minutes, message))
        return alarmResult
    }

    override fun launchDial(phoneNumber: String): ExternalLaunchResult {
        launchedDials.add(phoneNumber)
        return dialResult
    }

    override fun launchSendTo(phoneNumber: String, body: String): ExternalLaunchResult {
        launchedMessages.add(MessageCall(phoneNumber, body))
        return sendToResult
    }
}

