package dev.metis.agent.domain.agent

import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import dev.metis.agent.domain.contacts.MAX_CANDIDATES
import java.util.UUID

internal const val MAX_CONTACT_LOOKUP_KEY_LENGTH = 1024

/** A selected contact is transient until an already-reviewed action is accepted. */
class ActionPersonChoice(
    val displayName: String,
    val savedPersonId: UUID? = null,
    val contactLookupKey: String? = null,
) {
    override fun toString(): String = "ActionPersonChoice(redacted)"
}

sealed interface ActionPersonResolution {
    data object Typed : ActionPersonResolution
    data object PermissionNeeded : ActionPersonResolution
    class Resolved(val choice: ActionPersonChoice) : ActionPersonResolution
    class Ambiguous(val choices: List<ActionPersonChoice>, val refine: Boolean) : ActionPersonResolution
}

/** Exact saved people win. Contacts are read only when no saved name matches. */
class ActionPersonResolver(private val contacts: ContactsResolver?) {
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    // Saved matches and each permission outcome exit before later lookups.
    suspend fun resolve(name: String, saved: List<PromisePersonMatch>, skipContacts: Boolean = false):
        ActionPersonResolution {
        if (saved.size > MAX_CANDIDATES) return ActionPersonResolution.Ambiguous(emptyList(), true)
        if (saved.size > 1) return ActionPersonResolution.Ambiguous(saved.map {
            ActionPersonChoice(it.name, savedPersonId = it.id)
        }, false)
        saved.singleOrNull()?.let {
            return ActionPersonResolution.Resolved(ActionPersonChoice(it.name, savedPersonId = it.id))
        }
        if (skipContacts || contacts == null) return ActionPersonResolution.Typed
        return when (val found = contacts.resolvePerson(name)) {
            is ContactResolution.Resolved -> found.contact.choice()?.let(ActionPersonResolution::Resolved)
                ?: ActionPersonResolution.Typed
            is ContactResolution.Ambiguous -> {
                val usable = found.candidates.mapNotNull { it.choice() }
                when {
                    found.requiresRefinement -> ActionPersonResolution.Ambiguous(emptyList(), true)
                    usable.isEmpty() -> ActionPersonResolution.Typed
                    usable.size == 1 -> ActionPersonResolution.Resolved(usable.single())
                    else -> ActionPersonResolution.Ambiguous(usable, false)
                }
            }
            ContactResolution.NotFound -> ActionPersonResolution.Typed
            ContactResolution.PermissionNeeded -> ActionPersonResolution.PermissionNeeded
        }
    }

    private fun dev.metis.agent.domain.contacts.ContactCandidate.choice(): ActionPersonChoice? =
        lookupKey?.takeIf { it.isNotBlank() && it.length <= MAX_CONTACT_LOOKUP_KEY_LENGTH }?.let {
            ActionPersonChoice(displayName, contactLookupKey = it)
        }

}

/** Only clear person-bearing reminder titles request contact resolution. */
internal object ReminderPersonName {
    private val pattern = Regex("^(?:call|text|message|email|meet)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val suffix = Regex("\\s+(?:about|regarding|for|at|on)\\s+.+$", RegexOption.IGNORE_CASE)

    fun fromTitle(title: String): String? = pattern.matchEntire(title.trim())?.groupValues?.get(1)
        ?.replace(suffix, "")?.trim()?.takeIf {
            it.isNotBlank() && it.length <= MAX_NAME_LENGTH &&
                !it.startsWith("the ", ignoreCase = true) && it.none(Char::isISOControl)
        }

    private const val MAX_NAME_LENGTH = 100
}
