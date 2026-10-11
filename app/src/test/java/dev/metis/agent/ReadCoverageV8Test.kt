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
import dev.metis.agent.domain.agent.EntityExtractor
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExtractedEntity
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.IntentClassifier
import dev.metis.agent.domain.agent.IntentPrediction
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.LocalAgentOrchestrator
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.ParsedRequest
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReadSafetyV8
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Assistant-authored development plus retained exposed safety; not independent evaluation. */
class ReadCoverageV8Test {
    @Test
    fun developmentCasesCheckSpansLiteralQueriesAndEveryForcedLabelBeforeContext(): Unit = runBlocking {
        val directory = File(System.getProperty("metis.projectDir"), "../docs/evaluation/phase15/read_coverage_v8")
        val inputs = File(directory, "development-input.tsv").readLines().drop(1).associate {
            it.split('\t').let { fields -> fields[0] to decode(fields[1]) }
        }
        val expectations = File(directory, "development-expectations.tsv").readLines().drop(1)
        assertEquals(61, expectations.size)
        expectations.forEach { line ->
            val fields = line.split('\t')
            val input = request(requireNotNull(inputs[fields[0]]))
            val intent = AgentIntent.valueOf(fields[1])
            val isRead = fields[2] == "read"
            val reads = Reads()
            val contexts = Contexts()
            val result = pipeline(reads, contexts).process(input)
            assertEquals(fields[0], if (isRead) 1 else 0, reads.calls)
            assertEquals(fields[0], if (isRead) 1 else 0, contexts.calls)
            assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
            if (result.status == AgentResultStatus.FOLLOW_UP) {
                assertTrue(result.message.contains(input.text))
                assertTrue(requireNotNull(result.followUp).question.contains(input.text))
            }
            if (isRead) {
                assertEquals(AgentResultStatus.ANSWER, result.status)
                assertEquals(decode(fields[4]), reads.query)
                checkSpan(input, fields)
            }
            (ReadSafetyV8.intents + AgentIntent.CHECK_CALENDAR).forEach { label ->
                val forcedReads = Reads()
                val forcedContexts = Contexts()
                val forced = pipeline(forcedReads, forcedContexts, label).process(input)
                val eligible = isRead && intent == label
                assertEquals("${fields[0]} $label", if (eligible) 1 else 0, forcedContexts.calls)
                assertEquals("${fields[0]} $label", if (eligible) 1 else 0, forcedReads.calls)
                assertTrue(forced.proposals.isEmpty() && forced.completedActions.isEmpty())
                if (eligible) assertEquals(decode(fields[4]), forcedReads.query)
            }
        }
    }

    private fun checkSpan(input: AgentRequest, fields: List<String>) {
        if (fields[1] != AgentIntent.CHECK_TASKS.name) {
            val entity = EnglishRules().parse(input).entities.single()
            assertEquals(decode(fields[3]), entity.rawValue)
            assertEquals(fields[5].toInt(), entity.startOffset)
            assertEquals(fields[6].toInt(), entity.endOffset)
            assertEquals(entity.rawValue, input.text.substring(entity.startOffset, entity.endOffset))
        }
    }

    @Test
    fun forgedSpansKindsAndQueryTextStopBeforeContextAndSpecialistReads(): Unit = runBlocking {
        val input = request("Show me notes about the kiln glaze")
        val entity = EnglishRules().parse(input).entities.single()
        val variants = listOf(
            listOf(entity.copy(normalizedValue = NormalizedValue.Text("kiln glaze"))),
            listOf(entity.copy(normalizedValue = NormalizedValue.Text("the kiln glaze last month"))),
            listOf(entity.copy(type = EntityKind.PERSON)),
            listOf(entity.copy(rawValue = "the kiln glaze", startOffset = entity.startOffset - 4)),
            listOf(entity, entity), emptyList(),
        )
        variants.forEach { forged ->
            val extractor = object : EntityExtractor {
                override suspend fun extract(request: AgentRequest) = forged
            }
            val reads = Reads()
            val contexts = Contexts()
            val result = pipeline(reads, contexts, extractor = extractor).process(input)
            assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
            assertEquals(0, contexts.calls)
            assertEquals(0, reads.calls)
            val parsed = ParsedRequest(input, IntentPrediction(AgentIntent.CHECK_MEMORY, 1.0), forged,
                context(input))
            assertTrue(LanguageSpecialist(reads).respond(parsed) is SpecialistResult.Clarification)
            assertEquals(0, reads.calls)
        }
    }

    @Test
    fun fullQueriesAndTitlesReachPortWhileConstraintsAndSourcesStayBlocked(): Unit = runBlocking {
        val reads = Reads()
        listOf("Can you show me notes about The Who?" to "The Who",
            "Remind me of the cat's harness" to "the cat's harness",
            "Show me notes about the sitar 🚀 tuning" to "the sitar 🚀 tuning").forEach { (text, query) ->
            val result = pipeline(reads, Contexts()).process(request(text).copy(source = InputSource.VOICE))
            assertEquals(AgentResultStatus.ANSWER, result.status)
            assertEquals(query, reads.query)
        }
        listOf(" last year", " in May", " on Monday", " at 5 pm", " with tag exam", " from 2024",
            " older than 3 days", " sorted by date", " and delete tasks", "; show tasks", "\n").forEach { suffix ->
            val contexts = Contexts()
            val text = "Remind me of the cat's harness$suffix"
            val result = pipeline(reads, contexts, AgentIntent.CHECK_MEMORY).process(request(text))
            assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
            assertTrue(result.message.contains(text))
            assertEquals(0, contexts.calls)
        }
        InputSource.entries.filter { it !in setOf(InputSource.TEXT, InputSource.VOICE) }.forEach { source ->
            val contexts = Contexts()
            pipeline(reads, contexts, AgentIntent.CHECK_MEMORY).process(
                request("Remind me of kiln glaze").copy(source = source))
            assertEquals(0, contexts.calls)
        }
        assertEquals(3, reads.calls)
    }

    @Test
    fun longTopicsAndNegationCannotBypassBoundsWithForcedLabels(): Unit = runBlocking {
        val reads = Reads()
        val text = "Remind me of my " + "x".repeat(200)
        assertEquals(AgentResultStatus.ANSWER, pipeline(reads, Contexts()).process(request(text)).status)
        assertEquals("my " + "x".repeat(200), reads.query)
        listOf(text + "x", "Remind me of no kiln glaze").forEach { invalid ->
            ReadSafetyV8.intents.forEach { label ->
                val contexts = Contexts()
                val result = pipeline(reads, contexts, label).process(request(invalid))
                assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
                assertEquals(0, contexts.calls)
            }
        }
        assertEquals(1, reads.calls)
    }

    private fun pipeline(reads: Reads, contexts: Contexts, label: AgentIntent? = null,
                         extractor: EntityExtractor = EnglishRules()) = LocalAgentOrchestrator(
        if (label == null) EnglishRules() else object : IntentClassifier {
            override suspend fun classify(request: AgentRequest) = IntentPrediction(label, 1.0)
        }, extractor, contexts, LanguageSpecialist(reads), ProposalPolicy())

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-09T05:30:00Z"))
    private fun decode(text: String) = String(Base64.getDecoder().decode(text), Charsets.UTF_8)

    private class Contexts : ContextBuilder {
        var calls = 0
        override suspend fun snapshot(request: AgentRequest): ResolvedContext { calls++; return context(request) }
    }

    private class Reads : AgentReadPort {
        var calls = 0
        var query = ""
        override suspend fun tasks(): String { calls++; return "Synthetic tasks" }
        override suspend fun memories(query: String, at: Long): String {
            calls++; this.query = query; return "Synthetic memory"
        }
        override suspend fun promises(person: String): SpecialistResult {
            calls++; query = person; return SpecialistResult.Answer("Synthetic promises")
        }
    }

    companion object {
        private fun context(input: AgentRequest) = ResolvedContext(input.timestamp, ZoneId.of("Asia/Kolkata"),
            AutonomyLevel.ANSWER_ONLY, CapabilitySnapshot(input.timestamp, setOf(Capability.LOCAL_READ)))
    }
}
