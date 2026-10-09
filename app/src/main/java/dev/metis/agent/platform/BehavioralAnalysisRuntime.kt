package dev.metis.agent.platform

import android.content.Context
import androidx.core.content.edit

/** Explicit, local-only opt-in. Analysis runs only when the user opens reflection and taps review. */
object BehavioralAnalysisRuntime {
    private const val PREFERENCES = "behavioral_analysis_consent"
    private const val ENABLED = "enabled"

    fun enabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit(commit = true) { putBoolean(ENABLED, enabled) }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
