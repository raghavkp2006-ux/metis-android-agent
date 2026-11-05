package dev.metis.agent.domain.agent

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * RAM-only working memory scoped to the active conversation session.
 * Stores transient session context (such as the last resolved person for pronoun resolution).
 * Survives clarification clear, evict, cancel, and expiry.
 * Cleared only on explicit session reset.
 */
class SessionWorkingMemory {
    private val lastResolvedPerson = AtomicReference<String?>(null)

    fun lastResolvedPerson(): String? = lastResolvedPerson.get()

    fun updateLastResolvedPerson(person: String) {
        val trimmed = person.trim()
        if (trimmed.isNotEmpty() && trimmed.lowercase(Locale.ROOT) !in PRONOUNS) {
            lastResolvedPerson.set(trimmed)
        }
    }

    fun clear() {
        lastResolvedPerson.set(null)
    }

    private companion object {
        private val PRONOUNS = setOf("him", "her", "them", "that one")
    }
}

