package dev.metis.agent.domain.agent

sealed interface ExternalLaunchResult {
    data object Success : ExternalLaunchResult
    data class NoHandler(val message: String) : ExternalLaunchResult {
        init { requireText(message) }
    }
    data class Failed(val message: String) : ExternalLaunchResult {
        init { requireText(message) }
    }
}

interface ExternalActionLauncher {
    fun launchTimer(lengthSeconds: Int, message: String): ExternalLaunchResult
    fun launchAlarm(hour: Int, minutes: Int, message: String): ExternalLaunchResult
    fun launchDial(phoneNumber: String): ExternalLaunchResult
    fun launchSendTo(phoneNumber: String, body: String): ExternalLaunchResult
}

