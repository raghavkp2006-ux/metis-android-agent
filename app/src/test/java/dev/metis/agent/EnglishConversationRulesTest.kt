package dev.metis.agent

import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.EnglishConversationRules
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReferenceMention
import dev.metis.agent.domain.agent.ReferenceTurn
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishConversationRulesTest {
    private val now = Instant.parse("2026-10-25T06:00:00Z")
    private val session = UUID.randomUUID()

    @Test fun pronounsResolveOnlyTheirTypedSlot() {
        val history = listOf(turn(ReferenceMention(true, "Rahul"), ReferenceMention(false, "buy bread")))
        listOf(
            "remind me tomorrow to call him" to "remind me tomorrow to call Rahul",
            "remind me tomorrow to email her" to "remind me tomorrow to email Rahul",
            "add a task to call him" to "add a task to call Rahul",
            "I promised them the slides" to "I promised Rahul the slides",
            "complete it" to "complete task: buy bread",
            "delete task: that" to "delete task: buy bread",
            "remind me tomorrow to do that" to "remind me tomorrow to do buy bread",
        ).forEach { (text, expected) ->
            val question = requireNotNull(EnglishConversationRules.question(request(text), history))
            assertEquals(text, expected, EnglishConversationRules.resolve(question, "1")?.text)
        }
        assertNull(EnglishConversationRules.question(request("add a task to fix that socket"), history))
        assertNull(EnglishConversationRules.question(request("add a task to buy bread and milk"), history))
    }

    @Test fun missingStaleWrongTypeAndOldSessionNeverGuess() {
        val person = turn(ReferenceMention(true, "Rahul"))
        val empty = turn()
        listOf(
            emptyList(),
            listOf(turn(ReferenceMention(false, "study"))),
            listOf(ReferenceTurn(UUID.randomUUID(), now, person.mentions)),
            listOf(ReferenceTurn(session, now.minusSeconds(600), person.mentions)),
            listOf(ReferenceTurn(session, now.plusSeconds(1), person.mentions)),
            listOf(person, empty, empty, empty),
        ).forEach { history ->
            val question = requireNotNull(EnglishConversationRules.question(
                request("remind me tomorrow to call him"), history))
            assertEquals("Who do you mean?", question.question)
            assertTrue(question.choices.isEmpty())
            assertNull(EnglishConversationRules.resolve(question, "him"))
        }
    }

    @Test fun latestTurnWinsButEqualCandidatesRequireAChoice() {
        val first = turn(ReferenceMention(true, "Rahul"))
        val last = turn(ReferenceMention(true, "Maya"))
        val request = request("I promised her the slides")
        assertEquals(listOf("Maya"), EnglishConversationRules.question(request, listOf(first, last))?.choices)
        val ambiguous = requireNotNull(EnglishConversationRules.question(request,
            listOf(turn(ReferenceMention(true, "Rahul"), ReferenceMention(true, "Maya")))))
        assertEquals(listOf("Rahul", "Maya"), ambiguous.choices)
        assertNull(EnglishConversationRules.resolve(ambiguous, "someone"))
        assertEquals("I promised Maya the slides", EnglishConversationRules.resolve(ambiguous, "2")?.text)
        assertFalse(ambiguous.toString().contains("Maya"))
        assertFalse(first.toString().contains("Rahul"))
        val reminder = turn(ReferenceMention(false, "study", task = false))
        assertTrue(EnglishConversationRules.question(request("complete it"), listOf(
            turn(ReferenceMention(false, "study")), reminder))?.choices?.isEmpty() == true)
    }

    @Test fun explicitReadAndWriteMentionsAreEligibleButAnswerTextIsNotInspected() {
        listOf("I promised Rahul the slides", "what did I promise Rahul").forEach { text ->
            val mentions = EnglishConversationRules.mentions(request(text))
            assertTrue(text, mentions.any { it.person && it.value.equals("Rahul", true) })
            assertNotNull(EnglishConversationRules.question(request("I promised him the notes"),
                listOf(ReferenceTurn(session, now, mentions))))
        }
        assertTrue(EnglishConversationRules.mentions(request("show my tasks")).isEmpty())
        assertTrue(EnglishConversationRules.mentions(request("don't add a task to study")).isEmpty())
    }

    @Test fun splittingIsBoundedAndRequiresAKnownVerbAfterTheSeparator() {
        data class Case(val text: String, val count: Int, val tooMany: Boolean = false)
        listOf(
            Case("add a task to buy bread and milk", 1),
            Case("remind me tomorrow to buy bread and milk", 1),
            Case("add a task to read and add a task to write", 2),
            Case("add a task to read, then remind me tomorrow to study", 2),
            Case("add a task to read and create something", 2),
            Case("add a task to read and complete task: study and delete task: old", 0, true),
            Case("add a task to read and message Rahul", 1),
            Case("add a task to \"read and create notes\"", 1),
            Case(" and add a task to read", 1),
        ).forEach { case ->
            val split = EnglishConversationRules.split(request(case.text))
            assertEquals(case.text, case.count, split.requests.size)
            assertEquals(case.text, case.tooMany, split.tooMany)
        }
    }

    private fun turn(vararg mentions: ReferenceMention) = ReferenceTurn(session, now, mentions.toList())
    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, now,
        conversationId = session)
}
