package dev.metis.agent

import dev.metis.agent.domain.agent.ActionPersonResolution
import dev.metis.agent.domain.agent.ActionPersonResolver
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedPerson
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPersonResolverTest {
    @Test fun savedExactNameWinsBeforeContactsAreRead(): Unit = runBlocking {
        val contacts = FakeContactsResolver(listOf(candidate("Rahul")))
        val saved = PromisePersonMatch(UUID.randomUUID(), "RAHUL")
        val result = ActionPersonResolver(contacts).resolve("Rahul", listOf(saved))
            as ActionPersonResolution.Resolved
        assertEquals(saved.id, result.choice.savedPersonId)
        assertEquals(null, result.choice.contactLookupKey)
        assertEquals(0, contacts.calls)
    }

    @Test fun strictContactCasesAreTableDriven(): Unit = runBlocking {
        data class Case(val query: String, val names: List<String>, val expected: String)
        listOf(
            Case("Rahul", listOf("Rahul"), "resolved"),
            Case("rahul", listOf("RAHUL"), "resolved"),
            Case("Rahul", listOf("Rahool"), "resolved"),
            Case("Raghav", listOf("Raghav Sharma"), "resolved"),
            Case("Raghav Sharm", listOf("Raghav Sharma"), "resolved"),
            Case("Unrelated", listOf("Rahul"), "typed"),
            Case("Raghav", listOf("Raghav Shah", "Raghav Sharma"), "ambiguous"),
            Case("Raghav", listOf("Raghav", "Raghav"), "ambiguous"),
            Case("Raghav", List(6) { "Raghav" }, "refine"),
        ).forEach { case ->
            val resolver = ActionPersonResolver(FakeContactsResolver(case.names.map(::candidate)))
            val result = resolver.resolve(case.query, emptyList())
            val actual = when (result) {
                is ActionPersonResolution.Resolved -> "resolved"
                is ActionPersonResolution.Ambiguous -> if (result.refine) "refine" else "ambiguous"
                ActionPersonResolution.Typed -> "typed"
                ActionPersonResolution.PermissionNeeded -> "permission"
            }
            assertEquals(case.expected, actual)
            if (result is ActionPersonResolution.Ambiguous) assertTrue(result.choices.size <= 5)
            if (result is ActionPersonResolution.Resolved)
                assertTrue(requireNotNull(result.choice.contactLookupKey).startsWith("lookup-"))
        }
    }

    @Test fun deniedAndRevokedPermissionFallBackWithoutLeakingCandidates(): Unit = runBlocking {
        val fake = FakeContactsResolver(listOf(candidate("Raghav")), permitted = false)
        val resolver = ActionPersonResolver(fake)
        assertEquals(ActionPersonResolution.PermissionNeeded, resolver.resolve("Raghav", emptyList()))
        assertEquals(ActionPersonResolution.Typed, resolver.resolve("Raghav", emptyList(), skipContacts = true))
        fake.permitted = true
        val found = resolver.resolve("Raghav", emptyList()) as ActionPersonResolution.Resolved
        assertFalse(found.choice.toString().contains("Raghav"))
        fake.permitted = false
        assertEquals(ActionPersonResolution.PermissionNeeded, resolver.resolve("Raghav", emptyList()))
    }

    @Test fun selectedContactProjectionStoresOnlyNameAndReference() {
        val person = SavedPerson.fromContact("Synthetic Person", "synthetic-lookup",
            RecordMetadata(id = UUID.randomUUID().toString()))
        assertEquals("Synthetic Person", person.displayName)
        assertEquals("synthetic-lookup", person.contactLookupKey)
        assertEquals(null, person.phone)
        assertEquals(null, person.email)
        assertFalse(person.toString().contains("Synthetic Person"))
        assertFalse(person.toString().contains("synthetic-lookup"))
    }

    private fun candidate(name: String) = ContactCandidate(UUID.randomUUID().toString(), name,
        lookupKey = "lookup-${UUID.randomUUID()}")
}
