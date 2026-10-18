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
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.IntentClassifier
import dev.metis.agent.domain.agent.IntentPrediction
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.LocalAgentOrchestrator
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReadSafetyV7
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

/** New assistant-authored scenario families; no human approval or independent evaluation. */
class ReadCoverageV7Test {
    @org.junit.Ignore("Archived Phase 0: requires historical docs/evaluation fixtures.")
    @Test
    fun developmentCasesPreserveReferencesAndCannotGainAuthorityFromWrongLabels(): Unit = runBlocking {
        val directory = File(System.getProperty("metis.projectDir"), "../docs/evaluation/phase15/read_coverage_v7")
        val inputs = File(directory, "development-input.tsv").readLines().drop(1).associate {
            it.split('\t').let { fields -> fields[0] to decode(fields[1]) }
        }
        val expectations = File(directory, "development-expectations.tsv").readLines().drop(1)
        assertEquals(60, expectations.size)
        expectations.forEach { row ->
            val fields = row.split('\t')
            val input = request(requireNotNull(inputs[fields[0]]))
            val intent = AgentIntent.valueOf(fields[1])
            val isRead = fields[2] == "read"
            val expectedReference = decode(fields[3])
            val reads = Reads()
            val result = languageAgentOrchestrator(reads).process(input)
            assertEquals(fields[0], if (isRead) AgentResultStatus.ANSWER else AgentResultStatus.FOLLOW_UP,
                result.status)
            assertEquals(fields[0], if (isRead) 1 else 0, reads.calls)
            if (isRead) assertEquals(fields[0], expectedReference, reads.reference)
            ReadSafetyV7.intents.forEach { label ->
                val forcedReads = Reads()
                val contexts = Contexts()
                val pipeline = LocalAgentOrchestrator(object : IntentClassifier {
                    override suspend fun classify(request: AgentRequest) = IntentPrediction(label, 1.0)
                }, EnglishRules(), contexts, LanguageSpecialist(forcedReads), ProposalPolicy())
                val forcedResult = pipeline.process(input)
                val eligible = isRead && intent == label
                assertEquals("${fields[0]} $label", if (eligible) 1 else 0, contexts.calls)
                assertEquals("${fields[0]} $label", if (eligible) 1 else 0, forcedReads.calls)
                assertTrue(forcedResult.proposals.isEmpty() && forcedResult.completedActions.isEmpty())
            }
        }
    }

    @Test
    fun wholeTopicReachesPortWhileTrailingConstraintsStopBeforeContext(): Unit = runBlocking {
        val reads = Reads()
        val valid = "Find my notes about friction in pulleys."
        assertEquals(AgentResultStatus.ANSWER, languageAgentOrchestrator(reads).process(request(valid)).status)
        assertEquals("friction in pulleys", reads.reference)
        listOf(" last year", " in May", " on Monday", " at 5", " at 5 pm", " with tag exam",
            " older than 3 days", " sorted by date", " from 2024", " containing equations").forEach { suffix ->
            val text = "Find my notes about friction in pulleys$suffix"
            val contexts = Contexts()
            val rules = EnglishRules()
            val pipeline = LocalAgentOrchestrator(rules, rules, contexts, LanguageSpecialist(reads), ProposalPolicy())
            val result = pipeline.process(request(text))
            assertEquals(text, AgentResultStatus.FOLLOW_UP, result.status)
            assertEquals(text, 0, contexts.calls)
            assertTrue(result.message.contains(text))
        }
        assertEquals(1, reads.calls)
    }

    private fun decode(encoded: String) = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-09T05:30:00Z"))

    private class Contexts : ContextBuilder {
        var calls = 0
        override suspend fun snapshot(request: AgentRequest): ResolvedContext {
            calls++
            return ResolvedContext(request.timestamp, ZoneId.of("UTC"), AutonomyLevel.ANSWER_ONLY,
                CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))
        }
    }

    private class Reads : AgentReadPort {
        var calls = 0
        var reference = ""
        override suspend fun tasks(): String { calls++; return "Synthetic open tasks" }
        override suspend fun memories(query: String, at: Long): String {
            calls++; reference = query; return "Synthetic explicit memory"
        }
        override suspend fun promises(person: String): SpecialistResult {
            calls++; reference = person; return SpecialistResult.Answer("Synthetic promises")
        }
    }
}
