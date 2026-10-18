package dev.metis.agent.platform

import android.content.Context
import androidx.core.content.edit
import dev.metis.agent.domain.proactive.ProactiveCandidate
import dev.metis.agent.domain.proactive.ProactiveEngine
import dev.metis.agent.domain.proactive.ProactiveSuppression
import java.security.MessageDigest

/** Persists only item fingerprints and timestamps; it never stores notification text or task IDs. */
internal object ProactiveNoticeLedger {
    internal const val PREFERENCES_NAME = "proactive_policy"
    private const val ENABLED = "enabled"
    private const val LAST = "last_posted"
    private const val RECENT = "recent_posts"
    private const val SEEN_EVENTS = "seen_events"
    private const val MAX_SEEN_EVENTS = 128
    private const val EVENT_RETENTION_DAYS = 7L
    private const val BYTE_MASK = 255
    private val eventRetention = EVENT_RETENTION_DAYS * ProactiveEngine.DAY_MILLIS

    fun enabled(context: Context) = preferences(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        preferences(context).edit { putBoolean(ENABLED, value) }
    }

    fun allows(context: Context, candidate: ProactiveCandidate, now: Long): Boolean {
        val prefs = preferences(context)
        val recent = prefs.getString(RECENT, "").orEmpty().timestamps()
        val seenAt = prefs.getString(SEEN_EVENTS, "").orEmpty().seenEvents()[hash(candidate.identity)]
        return ProactiveSuppression.allows(now, recent, prefs.getLong(LAST, 0L).takeIf { it > 0 }, seenAt)
    }

    fun reserve(context: Context, candidate: ProactiveCandidate, now: Long): Boolean {
        val prefs = preferences(context)
        val recent = prefs.getString(RECENT, "").orEmpty().timestamps()
            .filter { it <= now && now - it < ProactiveEngine.DAY_MILLIS }
        val seen = prefs.getString(SEEN_EVENTS, "").orEmpty().seenEvents()
            .filterValues { it > now - eventRetention }.toMutableMap()
        val digest = hash(candidate.identity)
        val priorEventAt = seen[digest]
        val policyAllows = ProactiveSuppression.allows(now, recent,
            prefs.getLong(LAST, 0L).takeIf { it > 0 }, priorEventAt)
        if (!policyAllows) return false
        seen[digest] = now
        val encoded = seen.entries.sortedByDescending { it.value }.take(MAX_SEEN_EVENTS).joinToString(",") {
            "${it.key}:${it.value}"
        }
        val updatedRecent = recent + now
        prefs.edit(commit = true) {
            putLong(LAST, now)
            putString(RECENT, updatedRecent.joinToString(","))
            putString(SEEN_EVENTS, encoded)
        }
        return prefs.getLong(LAST, -1L) == now && prefs.getString(SEEN_EVENTS, "").orEmpty()
            .contains("${hash(candidate.identity)}:$now")
    }

    private fun String.timestamps() = split(',').mapNotNull(String::toLongOrNull)

    private fun String.seenEvents(): Map<String, Long> = split(',').mapNotNull { value ->
        val fields = value.split(':', limit = 2)
        if (fields.size == 2) fields[0].takeIf(String::isNotBlank)?.let { id ->
            fields[1].toLongOrNull()?.let { id to it }
        } else null
    }.toMap()

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }

    private fun preferences(context: Context) =
        context.getSharedPreferences(dev.metis.agent.AppIsolation.name(PREFERENCES_NAME), Context.MODE_PRIVATE)
}
