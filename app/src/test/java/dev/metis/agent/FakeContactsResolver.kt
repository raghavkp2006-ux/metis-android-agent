package dev.metis.agent

import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactMatcher
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver

internal class FakeContactsResolver(private val contacts: List<ContactCandidate>,
    var permitted: Boolean = true) : ContactsResolver {
    var calls = 0
        private set

    override suspend fun resolve(query: String): ContactResolution {
        calls++
        return if (permitted) ContactMatcher.resolve(query, contacts) else ContactResolution.PermissionNeeded
    }
}
