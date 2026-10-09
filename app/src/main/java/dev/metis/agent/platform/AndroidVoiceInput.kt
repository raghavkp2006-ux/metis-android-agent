package dev.metis.agent.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dev.metis.agent.domain.agent.VoiceRecognitionMode
import dev.metis.agent.domain.agent.VoiceRecognitionPolicy

enum class VoiceInputPhase { IDLE, LISTENING, FINISHING }
enum class VoiceInputMessage { MICROPHONE_PERMISSION_REQUIRED, OFFLINE_UNAVAILABLE, NO_PROVIDER, NO_RESULT,
    RECOGNITION_FAILED }

/** Owns one transient SpeechRecognizer session. Audio is never copied or persisted by METIS. */
class AndroidVoiceInput(
    context: Context,
    private val onTranscript: (String) -> Unit,
    private val onPhaseChanged: (VoiceInputPhase) -> Unit,
    private val onMessage: (VoiceInputMessage) -> Unit,
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0L

    @Suppress("ReturnCount") // Exit before capture whenever permission or a safe service is unavailable.
    fun start(onlineFallbackAllowed: Boolean): Boolean {
        if (recognizer != null) return false
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            onMessage(VoiceInputMessage.MICROPHONE_PERMISSION_REQUIRED)
            return false
        }
        val apiLevel = Build.VERSION.SDK_INT
        val onDeviceAvailable = hasOnDeviceRecognizer()
        val mode = VoiceRecognitionPolicy.select(apiLevel, onDeviceAvailable, onlineFallbackAllowed)
        if (mode == VoiceRecognitionMode.UNAVAILABLE) {
            onMessage(VoiceInputMessage.OFFLINE_UNAVAILABLE)
            return false
        }
        if (!runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)) {
            onMessage(VoiceInputMessage.NO_PROVIDER)
            return false
        }

        val session = ++generation
        try {
            val service = when {
                mode == VoiceRecognitionMode.ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                mode == VoiceRecognitionMode.ONLINE_FALLBACK -> SpeechRecognizer.createSpeechRecognizer(appContext)
                else -> error("On-device recognition is unavailable")
            }
            recognizer = service
            service.setRecognitionListener(listener(session))
            service.startListening(recognitionIntent())
            onPhaseChanged(VoiceInputPhase.LISTENING)
            return true
        } catch (_: RuntimeException) {
            finish(session)
            onMessage(VoiceInputMessage.NO_PROVIDER)
            return false
        }
    }

    fun stop() {
        val service = recognizer ?: return
        onPhaseChanged(VoiceInputPhase.FINISHING)
        try {
            service.stopListening()
        } catch (_: RuntimeException) {
            finish(generation)
            onMessage(VoiceInputMessage.RECOGNITION_FAILED)
        }
    }

    fun cancel() {
        generation++
        val service = recognizer
        recognizer = null
        runCatching { service?.cancel() }
        runCatching { service?.destroy() }
        onPhaseChanged(VoiceInputPhase.IDLE)
    }

    fun close() {
        generation++
        val service = recognizer
        recognizer = null
        runCatching { service?.cancel() }
        runCatching { service?.destroy() }
    }

    private fun listener(session: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (session == generation) onPhaseChanged(VoiceInputPhase.FINISHING)
        }
        override fun onError(error: Int) {
            if (session != generation) return
            finish(session)
            onMessage(if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) VoiceInputMessage.NO_RESULT
            else VoiceInputMessage.RECOGNITION_FAILED)
        }
        override fun onResults(results: Bundle?) {
            if (session != generation) return
            val text = results?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.trim().orEmpty()
            finish(session)
            if (text.isBlank()) onMessage(VoiceInputMessage.NO_RESULT) else onTranscript(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (session != generation) return
            partialResults?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.trim()?.takeIf(String::isNotBlank)?.let(onTranscript)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun finish(session: Long) {
        if (session != generation) return
        val service = recognizer
        recognizer = null
        runCatching { service?.destroy() }
        onPhaseChanged(VoiceInputPhase.IDLE)
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun hasOnDeviceRecognizer(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        isOnDeviceRecognizerAvailableAt31()
    } else false

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isOnDeviceRecognizerAvailableAt31(): Boolean =
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)
}
