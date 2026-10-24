package dev.metis.agent.domain.contacts

import java.util.Collections

/** Read-only lookup. Results remain in memory and are never an action or a saved record. */
interface ContactsResolver {
    suspend fun resolve(query: String): ContactResolution

    /** Action resolution never picks a winner when multiple names remain plausible. */
    suspend fun resolvePerson(query: String): ContactResolution = resolve(query)
}

class ContactCandidate(
    val id: String,
    val displayName: String,
    maskedIdentifier: String? = null,
    val lookupKey: String? = null,
) {
    val maskedIdentifier: String? = maskedIdentifier?.filter(Char::isDigit)
        ?.takeIf { it.length >= MASK_DIGITS }?.takeLast(MASK_DIGITS)?.let { "••$it" }

    override fun toString(): String = "ContactCandidate(redacted)"

    private companion object { const val MASK_DIGITS = 2 }
}

sealed interface ContactResolution {
    class Resolved(val contact: ContactCandidate) : ContactResolution {
        override fun toString(): String = "Resolved(redacted)"
    }

    class Ambiguous(candidates: List<ContactCandidate>, val requiresRefinement: Boolean = false) : ContactResolution {
        val candidates: List<ContactCandidate> = Collections.unmodifiableList(candidates.toList())

        init { require(this.candidates.size <= MAX_CANDIDATES) }

        override fun toString(): String = "Ambiguous(redacted)"
    }

    data object NotFound : ContactResolution
    data object PermissionNeeded : ContactResolution
}

const val MAX_CANDIDATES = 5
const val MAX_CONTACT_QUERY_LENGTH = 100
