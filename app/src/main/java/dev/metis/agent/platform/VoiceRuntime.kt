package dev.metis.agent.platform

import android.content.Context
import androidx.core.content.edit

object VoiceRuntime {
    private const val PREFERENCES_NAME = "metis_voice_preferences"
    private const val ONLINE_FALLBACK_KEY = "online_fallback_allowed"

    fun onlineFallbackAllowed(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(ONLINE_FALLBACK_KEY, false)

    fun setOnlineFallbackAllowed(context: Context, allowed: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(ONLINE_FALLBACK_KEY, allowed)
        }
    }
}
