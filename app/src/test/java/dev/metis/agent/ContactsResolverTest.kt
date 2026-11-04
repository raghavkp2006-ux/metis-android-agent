package dev.metis.agent

import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactMatcher
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.MAX_CANDIDATES
import dev.metis.agent.domain.contacts.RankedContact
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsResolverTest {
    @Test fun transliterationPairsResolveOnlyWhenUnique(): Unit = runBlocking {
        val resolver = FakeContactsResolver(listOf(candidate("Rahool"), candidate("Raghav")))
        assertEquals("Rahool", (resolver.resolve("Rahul") as ContactResolution.Resolved).contact.displayName)
        assertEquals("Raghav", (resolver.resolve("Raghu") as ContactResolution.Resolved).contact.displayName)
    }

    @Test fun duplicateNamesAndNicknameStayAmbiguous(): Unit = runBlocking {
        val duplicates = FakeContactsResolver(listOf(candidate("Raghav"), candidate("Raghav")))
        val result = duplicates.resolve("Raghav") as ContactResolution.Ambiguous
        assertEquals(2, result.candidates.size)
        assertFalse(result.requiresRefinement)

        val nicknames = FakeContactsResolver(listOf(candidate("Samuel"), candidate("Samantha")))
        assertEquals(2, (nicknames.resolve("Sam") as ContactResolution.Ambiguous).candidates.size)

        val transliterationCollision = FakeContactsResolver(listOf(candidate("Raghav"), candidate("Raghu")))
        assertEquals(2, (transliterationCollision.resolve("Raghav") as
            ContactResolution.Ambiguous).candidates.size)
    }

    @Test fun moreThanFiveCandidatesAskForRefinementWithoutTruncation(): Unit = runBlocking {
        val resolver = FakeContactsResolver((1..MAX_CANDIDATES + 1).map { candidate("Raghav") })
        val result = resolver.resolve("Raghav") as ContactResolution.Ambiguous
        assertTrue(result.requiresRefinement)
        assertTrue(result.candidates.isEmpty())
    }

    @Test fun ambiguitySnapshotIsImmutableAndRedacted() {
        val source = mutableListOf(candidate("Synthetic One"))
        val result = ContactResolution.Ambiguous(source)
        source += candidate("Synthetic Two")
        assertEquals(1, result.candidates.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.candidates as MutableList).add(candidate("Synthetic Three"))
        }
        assertFalse(result.toString().contains("Synthetic"))
        assertFalse(result.candidates.first().toString().contains("Synthetic"))
        assertEquals("••42", ContactCandidate("synthetic", "Synthetic", "+1 555 010 4242")
            .maskedIdentifier)
    }

    @Test fun thresholdHasJustAboveAndJustBelowBoundary() {
        val justAbove = ContactMatcher.FUZZY_SCORE_THRESHOLD + EPSILON
        val justBelow = ContactMatcher.FUZZY_SCORE_THRESHOLD - EPSILON
        assertTrue(ContactMatcher.choose(listOf(ranked(justAbove))) is ContactResolution.Resolved)
        assertEquals(ContactResolution.NotFound, ContactMatcher.choose(listOf(ranked(justBelow))))
    }

    @Test fun separationMarginHasJustAboveAndJustBelowBoundary() {
        val best = ranked(0.99)
        val above = ranked(best.score - ContactMatcher.SEPARATION_MARGIN - EPSILON)
        val below = ranked(best.score - ContactMatcher.SEPARATION_MARGIN + EPSILON)
        assertTrue(ContactMatcher.choose(listOf(best, above)) is ContactResolution.Resolved)
        assertTrue(ContactMatcher.choose(listOf(best, below)) is ContactResolution.Ambiguous)
    }

    @Test fun fuzzyMatchAndNoMatch(): Unit = runBlocking {
        val resolver = FakeContactsResolver(listOf(candidate("Contact C")))
        assertTrue(resolver.resolve("Contact C") is ContactResolution.Resolved)
        assertEquals(ContactResolution.NotFound, resolver.resolve("unrelated"))
    }

    @Test fun fakeCanModelDeniedAndRevokedPermission(): Unit = runBlocking {
        val resolver = FakeContactsResolver(listOf(candidate("Raghav")), permitted = false)
        assertEquals(ContactResolution.PermissionNeeded, resolver.resolve("Raghav"))
        resolver.permitted = true
        assertTrue(resolver.resolve("Raghav") is ContactResolution.Resolved)
        resolver.permitted = false
        assertEquals(ContactResolution.PermissionNeeded, resolver.resolve("Raghav"))
    }

    @Test fun fuzzyVariantsIncludeDiacriticsCaseRaghawAndSmallEditDistance(): Unit = runBlocking {
        val resolver = FakeContactsResolver(listOf(candidate("Raghav")))
        assertEquals("Raghav", (resolver.resolve("Rághav") as ContactResolution.Resolved).contact.displayName)
        assertEquals("Raghav", (resolver.resolve("raghav") as ContactResolution.Resolved).contact.displayName)
        assertEquals("Raghav", (resolver.resolve("Raghaw") as ContactResolution.Resolved).contact.displayName)
        assertEquals("Raghav", (resolver.resolve("Raghxv") as ContactResolution.Resolved).contact.displayName)
    }

    @Test fun exactAmbiguousAndNoneResolutions(): Unit = runBlocking {
        val exactResolver = FakeContactsResolver(listOf(candidate("Contact A")))
        assertTrue(exactResolver.resolve("Contact A") is ContactResolution.Resolved)

        val ambiguousResolver = FakeContactsResolver(listOf(candidate("Contact A"), candidate("Contact B")))
        val ambiguousResult = ambiguousResolver.resolve("Contact")
        assertTrue(ambiguousResult is ContactResolution.Ambiguous)
        assertEquals(2, (ambiguousResult as ContactResolution.Ambiguous).candidates.size)

        val noneResolver = FakeContactsResolver(listOf(candidate("Contact A")))
        assertEquals(ContactResolution.NotFound, noneResolver.resolve("Completely Unrelated"))
    }

    @Test fun replyByNumberOrOrdinalResolvesCandidate() {
        val choices = listOf("Contact A", "Contact B", "Contact C")
        assertEquals(0, dev.metis.agent.domain.agent.parseChoiceIndex("1", choices))
        assertEquals(1, dev.metis.agent.domain.agent.parseChoiceIndex("second", choices))
        assertEquals(2, dev.metis.agent.domain.agent.parseChoiceIndex("third", choices))
        assertEquals(0, dev.metis.agent.domain.agent.parseChoiceIndex("first", choices))
        assertEquals(1, dev.metis.agent.domain.agent.parseChoiceIndex("2", choices))
        assertEquals(0, dev.metis.agent.domain.agent.parseChoiceIndex("Contact A", choices))
    }

    private fun candidate(name: String) = ContactCandidate(UUID.randomUUID().toString(), name, "••42")
    private fun ranked(score: Double) = RankedContact(candidate("Synthetic"), score)

    private companion object { const val EPSILON = 0.001 }
}
