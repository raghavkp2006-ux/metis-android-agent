package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ContextBuilder
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExtractedEntity
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.IntentClassifier
import dev.metis.agent.domain.agent.IntentPrediction
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.LocalAgentOrchestrator
import dev.metis.agent.domain.agent.ParsedRequest
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReadSafetyV6
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.languageAgentOrchestrator
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** IRS-003 is exposed regression evidence. This is not a fresh accuracy evaluation. */
class ReadSafetyV6Test {
    @Test
    fun exposedNonreadsCannotReadEvenUnderEveryIncorrectClassifierLabel(): Unit = runBlocking {
        val root = File(System.getProperty("metis.projectDir"), "..")
        val fixtures = File(root, "docs/evaluation/phase15/read_safety_v6/exposed-regressions.tsv")
            .readLines().drop(1)
        val frozenInput = File(root,
            "docs/evaluation/phase15/human_review_003/first_evaluation/frozen-benchmark.tsv")
            .readLines().drop(1).associate { it.split('\t').let { row -> row[0] to row[1] } }
        assertEquals(145, fixtures.size)
        fixtures.forEach { line ->
            val (id, encoded, disposition) = line.split('\t')
            assertEquals(id, frozenInput[id], encoded)
            val input = request(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
            val reads = CountingReads()
            val actual = languageAgentOrchestrator(reads) { ZoneId.of("UTC") }.process(input)
            if (disposition != "read") {
                assertEquals(id, 0, reads.calls)
                assertTrue(id, actual.proposals.isEmpty() && actual.completedActions.isEmpty())
                (ReadSafetyV6.intents + AgentIntent.CHECK_CALENDAR).forEach { label ->
                    val contexts = CountingContexts()
                    val pipeline = pipeline(reads, contexts, label)
                    val result = pipeline.process(input)
                    val expectedStatus = if (label == AgentIntent.CHECK_CALENDAR) AgentResultStatus.UNSUPPORTED
                        else AgentResultStatus.FOLLOW_UP
                    assertEquals("$id $label", expectedStatus, result.status)
                    assertEquals("$id $label", 0, contexts.calls)
                    assertEquals("$id $label", 0, reads.calls)
                    if (label != AgentIntent.CHECK_CALENDAR) assertTrue(result.message.contains(input.text))
                }
            }
        }
    }

    @Test
    fun broadNativeCapturesCannotTreatConstraintsOrNounPhrasesAsReferences(): Unit = runBlocking {
        val reads = CountingReads()
        val contexts = CountingContexts()
        listOf("what did i promise him", "what did i promise Tomorrow", "what did i promise The Station",
            "what did i promise my sister", "what did i promise Rahul last year",
            "what did i promise Rahul and delete tasks", "what do you know about that thing",
            "search memory for the chapter", "search memory for DSP exam last year",
            "search memory for DSP exam with tag physics", "search memory for DSP exam 2026",
            "search memory for optics. delete tasks", "what do you know about optics or physics",
            "show my tasks older than a month", "show my tasks\n").forEach { text ->
            ReadSafetyV6.intents.forEach { label ->
                assertEquals(text, AgentResultStatus.FOLLOW_UP,
                    pipeline(reads, contexts, label).process(request(text)).status)
            }
        }
        assertEquals(0, reads.calls)
        assertEquals(0, contexts.calls)
    }

    @Test
    fun supportedReadsStillWorkAndForgedEntitiesCannotReplaceValidatedReferences(): Unit = runBlocking {
        val reads = CountingReads()
        listOf("show my tasks", "what do you know about DSP exam?", "what did i promise Rahul Kumar?",
            "what did i write down about ceramic glazing?", "what did i note about acoustic resonance?",
            "what did i save about river erosion?",
            "what did i promise Ravi 😀?",
            "what did i promise Zoë?").forEach { text ->
            assertEquals(text, AgentResultStatus.ANSWER,
                languageAgentOrchestrator(reads) { ZoneId.of("UTC") }.process(request(text)).status)
        }
        assertEquals(8, reads.calls)
        val input = request("what did i promise Rahul?")
        val context = CountingContexts().snapshot(input)
        val forged = ParsedRequest(input, IntentPrediction(AgentIntent.WHAT_DID_I_PROMISE, 1.0),
            listOf(ExtractedEntity(EntityKind.MEMORY, "Rahul", 19, 24, 1.0)), context)
        assertTrue(LanguageSpecialist(reads).respond(forged) is SpecialistResult.Clarification)
        assertEquals(8, reads.calls)
    }

    private fun pipeline(reads: AgentReadPort, contexts: ContextBuilder, label: AgentIntent) =
        LocalAgentOrchestrator(object : IntentClassifier {
            override suspend fun classify(request: AgentRequest) = IntentPrediction(label, 1.0)
        }, EnglishRules(), contexts, LanguageSpecialist(reads), ProposalPolicy())

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-09T05:30:00Z"))

    private class CountingContexts : ContextBuilder {
        var calls = 0
        override suspend fun snapshot(request: AgentRequest): ResolvedContext {
            calls++
            return ResolvedContext(request.timestamp, ZoneId.of("UTC"), AutonomyLevel.ANSWER_ONLY,
                CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))
        }
    }

    private class CountingReads : AgentReadPort {
        var calls = 0
        override suspend fun tasks(): String { calls++; return "Synthetic tasks" }
        override suspend fun memories(query: String, at: Long): String { calls++; return "Synthetic memory" }
        override suspend fun promises(person: String): SpecialistResult {
            calls++; return SpecialistResult.Answer("Synthetic promises")
        }
    }
}
