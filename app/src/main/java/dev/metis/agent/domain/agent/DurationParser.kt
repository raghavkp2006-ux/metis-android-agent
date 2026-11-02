@file:Suppress("MagicNumber", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "MaxLineLength")

package dev.metis.agent.domain.agent

import java.util.Locale

internal object DurationParser {
    const val MAX_TIMER_SECONDS = 86_400L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L
    private const val HALF_HOUR_SECONDS = 1800L
    private const val QUARTER_HOUR_SECONDS = 900L

    private val SPECIAL = mapOf(
        "half an hour" to HALF_HOUR_SECONDS,
        "half a hour" to HALF_HOUR_SECONDS,
        "quarter hour" to QUARTER_HOUR_SECONDS,
        "quarter of an hour" to QUARTER_HOUR_SECONDS,
        "an hour" to SECONDS_PER_HOUR,
        "a hour" to SECONDS_PER_HOUR,
        "a minute" to SECONDS_PER_MINUTE,
        "one minute" to SECONDS_PER_MINUTE,
        "one hour" to SECONDS_PER_HOUR,
        "aadha ghanta" to HALF_HOUR_SECONDS,
        "adha ghanta" to HALF_HOUR_SECONDS,
        "aadhe ghante" to HALF_HOUR_SECONDS,
        "ek ghanta" to SECONDS_PER_HOUR,
        "das minute" to 600L,
        "dus minute" to 600L,
        "do ghante" to 7200L,
        "teen ghante" to 10800L,
        "paanch minute" to 300L,
        "panch minute" to 300L,
        "pandrah minute" to QUARTER_HOUR_SECONDS,
        "bees minute" to 1200L,
        "pachees minute" to 1500L,
        "tees minute" to HALF_HOUR_SECONDS,
    )

    private val HINDI_NUMBERS = mapOf(
        "ek" to 1L, "do" to 2L, "teen" to 3L, "char" to 4L, "paanch" to 5L, "panch" to 5L,
        "che" to 6L, "saat" to 7L, "aath" to 8L, "nau" to 9L, "das" to 10L, "dus" to 10L,
        "pandrah" to 15L, "bees" to 20L, "pachees" to 25L, "tees" to 30L
    )

    private val UNIT_REGEX = Regex(
        """^(\d+(?:\.\d+)?)\s*(s|sec|secs|second|seconds|m|min|mins|""" +
            """minute|minutes|h|hr|hrs|hour|hours|ghanta|ghante)$""",
        RegexOption.IGNORE_CASE
    )

    fun parseToSeconds(raw: String): Long? {
        val s = raw.trim().lowercase(Locale.ROOT)
        SPECIAL[s]?.let { return if (it in 1..MAX_TIMER_SECONDS) it else null }

        for ((word, num) in HINDI_NUMBERS) {
            if (s.startsWith("$word ")) {
                val rem = s.removePrefix("$word ").trim()
                val multiplier = when (rem) {
                    "minute", "minutes", "min", "mins", "m" -> SECONDS_PER_MINUTE
                    "ghanta", "ghante", "hour", "hours", "hr", "hrs", "h" -> SECONDS_PER_HOUR
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
