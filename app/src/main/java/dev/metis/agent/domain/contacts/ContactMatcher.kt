package dev.metis.agent.domain.contacts

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Conservative local matcher: uncertain matches stay questions. */
object ContactMatcher {
    const val FUZZY_SCORE_THRESHOLD = 0.86
    const val SEPARATION_MARGIN = 0.08

    fun resolve(query: String, contacts: List<ContactCandidate>): ContactResolution {
        val normalized = normalize(query)
        if (normalized.isBlank()) return ContactResolution.NotFound
        val ranked = contacts.mapNotNull { candidate ->
            score(normalized, normalize(candidate.displayName)).takeIf { it >= FUZZY_SCORE_THRESHOLD }
                ?.let { RankedContact(candidate, it) }
        }
        return choose(ranked)
    }

    /** Exact names outrank fuzzy names; any tie or multiple plausible names needs a choice. */
    @Suppress("ReturnCount") // Exact matches must finish before fuzzy candidates are considered.
    fun resolvePerson(query: String, contacts: List<ContactCandidate>): ContactResolution {
        val normalized = normalize(query)
        if (normalized.isBlank()) return ContactResolution.NotFound
        val exact = contacts.filter { normalize(it.displayName) == normalized }
        if (exact.isNotEmpty()) return strictChoice(exact)
        return strictChoice(contacts.filter {
            score(normalized, normalize(it.displayName)) >= FUZZY_SCORE_THRESHOLD
        })
    }

    private fun strictChoice(candidates: List<ContactCandidate>): ContactResolution = when {
        candidates.isEmpty() -> ContactResolution.NotFound
        candidates.size > MAX_CANDIDATES -> ContactResolution.Ambiguous(emptyList(), true)
        candidates.size == 1 -> ContactResolution.Resolved(candidates.single())
        else -> ContactResolution.Ambiguous(candidates.sortedBy { it.id })
    }

    @Suppress("ReturnCount") // Uncertain and over-limit sets take separate fail-closed exits.
    internal fun choose(ranked: List<RankedContact>): ContactResolution {
        val plausible = ranked.filter { it.score >= FUZZY_SCORE_THRESHOLD }
            .sortedWith(compareByDescending<RankedContact> { it.score }.thenBy { it.contact.id })
        if (plausible.isEmpty()) return ContactResolution.NotFound
        if (plausible.size > MAX_CANDIDATES) return ContactResolution.Ambiguous(emptyList(), true)
        val best = plausible.first()
        val runnerUp = plausible.getOrNull(1)
        if (runnerUp == null || best.score - runnerUp.score > SEPARATION_MARGIN) {
            return ContactResolution.Resolved(best.contact)
        }
        return ContactResolution.Ambiguous(plausible.map { it.contact })
    }

    @Suppress("ReturnCount") // Exact, transliterated and prefix matches have explicit scores.
    internal fun score(query: String, name: String): Double {
        if (query == name) return 1.0
        val words = name.split(' ').filter(String::isNotBlank)
        val variants = listOf(name) + words
        if (variants.any { equivalent(query, it) }) return TRANSLITERATION_SCORE
        if (query.length >= MIN_PREFIX && variants.any { it.startsWith(query) }) return PREFIX_SCORE
        return variants.maxOfOrNull { jaroWinkler(query, it) } ?: 0.0
    }

    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        .replace(Regex("\\s+"), " ")

    private fun equivalent(left: String, right: String): Boolean = TRANSLITERATIONS.any {
        left in it && right in it
    }

    @Suppress("ReturnCount") // Empty or unmatched names have a zero score.
    private fun jaroWinkler(left: String, right: String): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val distance = max(max(left.length, right.length) / 2 - 1, 0)
        val leftMatch = BooleanArray(left.length)
        val rightMatch = BooleanArray(right.length)
        var matches = 0
        left.indices.forEach { index ->
            val match = (max(0, index - distance)..min(index + distance, right.lastIndex))
                .firstOrNull { !rightMatch[it] && left[index] == right[it] }
            if (match != null) {
                leftMatch[index] = true
                rightMatch[match] = true
                matches++
            }
        }
        if (matches == 0) return 0.0
        var transpositions = 0
        var rightIndex = 0
        left.indices.filter { leftMatch[it] }.forEach { index ->
            while (!rightMatch[rightIndex]) rightIndex++
            if (left[index] != right[rightIndex]) transpositions++
            rightIndex++
        }
        val count = matches.toDouble()
        val jaro = (count / left.length + count / right.length +
            (count - transpositions / TRANSPOSITION_DIVISOR) / count) / JARO_TERMS
        val prefix = left.zip(right).takeWhile { it.first == it.second }.size.coerceAtMost(MAX_PREFIX)
        return jaro + prefix * WINKLER_SCALE * (1.0 - jaro)
    }

    private const val TRANSLITERATION_SCORE = 0.94
    private const val PREFIX_SCORE = 0.9
    private const val MIN_PREFIX = 3
    private const val MAX_PREFIX = 4
    private const val WINKLER_SCALE = 0.1
    private const val TRANSPOSITION_DIVISOR = 2.0
    private const val JARO_TERMS = 3.0
    private val TRANSLITERATIONS = listOf(
        setOf("rahul", "rahool"), setOf("raghav", "raghu"),
        setOf("sam", "samuel", "samantha"),
    )
}

internal class RankedContact(val contact: ContactCandidate, val score: Double)
