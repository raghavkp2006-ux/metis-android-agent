package dev.metis.agent.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Stores only permission UX flags; no query, name, number or provider identifier. */
class ContactsPermissionPolicy(context: Context) {
    private val application = context.applicationContext
    private val preferences = application.getSharedPreferences(
        dev.metis.agent.AppIsolation.name("metis.contacts.permission"), Context.MODE_PRIVATE)

    fun isPermanentlyDenied(): Boolean {
        if (isGranted()) {
            preferences.edit { remove(PERMANENT); remove(EXPLAINED) }
            return false
        }
        return preferences.getBoolean(PERMANENT, false)
    }

    fun shouldExplainPermanentDenial(): Boolean =
        isPermanentlyDenied() && !preferences.getBoolean(EXPLAINED, false)

    fun noteResult(granted: Boolean, canAskAgain: Boolean): Boolean {
        if (granted) {
            preferences.edit { remove(PERMANENT); remove(EXPLAINED) }
            return false
        }
        val permanent = !canAskAgain
        if (permanent) preferences.edit { putBoolean(PERMANENT, true) }
        return permanent
    }

    fun markPermanentExplanationShown() { preferences.edit { putBoolean(EXPLAINED, true) } }

    private fun isGranted() = ContextCompat.checkSelfPermission(application,
        Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PERMANENT = "permanent"
        const val EXPLAINED = "explained"
    }
}
