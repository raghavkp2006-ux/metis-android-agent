package dev.metis.agent.domain.agent

import java.util.Locale

internal object DurationParser {
    const val MAX_TIMER_SECONDS = 86_400L

    private val SPECIAL = mapOf(
        "half an hour" to 1800L,
        "half a hour" to 1800L,
        "quarter hour" to 900L,
        "quarter of an hour" to 900L,
        "an hour" to 3600L,
        "a hour" to 3600L,
        "a minute" to 60L,
        "one minute" to 60L,
        "one hour" to 3600L,
        "aadha ghanta" to 1800L,
        "adha ghanta" to 1800L,
        "aadhe ghante" to 1800L,
        "ek ghanta" to 3600L,
        "das minute" to 600L,
        "dus minute" to 600L,
        "do ghante" to 7200L,
        "teen ghante" to 10800L,
        "paanch minute" to 300L,
        "panch minute" to 300L,
        "pandrah minute" to 900L,
        "bees minute" to 1200L,
        "pachees minute" to 1500L,
        "tees minute" to 1800L,
    )

    private val HINDI_NUMBERS = mapOf(
        "ek" to 1L, "do" to 2L, "teen" to 3L, "char" to 4L, "paanch" to 5L, "panch" to 5L,
        "che" to 6L, "saat" to 7L, "aath" to 8L, "nau" to 9L, "das" to 10L, "dus" to 10L,
        "pandrah" to 15L, "bees" to 20L, "pachees" to 25L, "tees" to 30L
    )

    private val UNIT_REGEX = Regex(
        """^(\d+(?:\.\d+)?)\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|ghanta|ghante)$""",
        RegexOption.IGNORE_CASE
    )

    fun parseToSeconds(raw: String): Long? {
        val s = raw.trim().lowercase(Locale.ROOT)
        SPECIAL[s]?.let { return if (it in 1..MAX_TIMER_SECONDS) it else null }

        for ((word, num) in HINDI_NUMBERS) {
            if (s.startsWith("$word ")) {
                val rem = s.removePrefix("$word ").trim()
                val multiplier = when (rem) {
                    "minute", "minutes", "min", "mins", "m" -> 60L
                    "ghanta", "ghante", "hour", "hours", "hr", "hrs", "h" -> 3600L
                    "second", "seconds", "sec", "secs", "s" -> 1L
                    else -> null
                }
                if (multiplier != null) {
                    val sec = num * multiplier
                    return if (sec in 1..MAX_TIMER_SECONDS) sec else null
                }
            }
        }

        val match = UNIT_REGEX.matchEntire(s) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        val multiplier = when (unit) {
            "s", "sec", "secs", "second", "seconds" -> 1.0
            "m", "min", "mins", "minute", "minutes" -> 60.0
            "h", "hr", "hrs", "hour", "hours", "ghanta", "ghante" -> 3600.0
            else -> return null
        }
        val total = Math.round(value * multiplier)
        return if (total in 1..MAX_TIMER_SECONDS) total else null
    }
}

