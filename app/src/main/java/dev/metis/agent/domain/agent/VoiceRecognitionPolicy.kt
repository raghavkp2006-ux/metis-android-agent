package dev.metis.agent.domain.agent

/** The platform recognizer is selected without treating an offline preference as a guarantee. */
enum class VoiceRecognitionMode { ON_DEVICE, ONLINE_FALLBACK, UNAVAILABLE }

object VoiceRecognitionPolicy {
    private const val ON_DEVICE_API_LEVEL = 31

    fun select(apiLevel: Int, onDeviceAvailable: Boolean, onlineFallbackAllowed: Boolean): VoiceRecognitionMode =
        when {
            apiLevel >= ON_DEVICE_API_LEVEL && onDeviceAvailable -> VoiceRecognitionMode.ON_DEVICE
            onlineFallbackAllowed -> VoiceRecognitionMode.ONLINE_FALLBACK
            else -> VoiceRecognitionMode.UNAVAILABLE
        }
}
