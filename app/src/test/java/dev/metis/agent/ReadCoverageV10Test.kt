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
import dev.metis.agent.domain.agent.ReadSafetyV10
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.evaluation.Phase15RulesSnapshot
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exposed and assistant-authored regressions, never independent evaluation. */
class ReadCoverageV10Test {
    @Test
    @Suppress("CyclomaticComplexMethod") // Check default and all forced labels against the same corpus rows.
    fun completeCorpusChecksCoverageSpansQueriesConstraintsAndAllForcedLabels(): Unit = runBlocking {
        val positives = mutableMapOf<String, Int>()
        fixtures().forEach { fixture ->
            val input = fixture.input
            val reads = Reads()
            val contexts = Contexts()
            val result = pipeline(reads, contexts).process(input)
            assertEquals(fixture.id, if (fixture.read) 1 else 0, reads.calls)
            // All these cases are read-shaped, unsupported or unresolved: no
            // non-read case may construct context, even under a calendar label.
            assertEquals(fixture.id, if (fixture.read) 1 else 0, contexts.calls)
            assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
            if (fixture.read) {
                positives[fixture.section] = (positives[fixture.section] ?: 0) + 1
                assertEquals(fixture.id, AgentResultStatus.ANSWER, result.status)
                assertEquals(fixture.id, fixture.intent, EnglishRules().parse(input).intent)
                assertEquals(fixture.id, fixture.spans, EnglishRules().parse(input).entities
                    .joinToString(";", transform = Phase15RulesSnapshot::encodeEntity))
                assertEquals(fixture.id, fixture.query, reads.query)
            }
            if (result.status == AgentResultStatus.FOLLOW_UP) assertTrue(result.message.contains(input.text))
            ReadSafetyV10.intents.forEach { label ->
                val forcedReads = Reads()
                val forcedContexts = Contexts()
                val extractor = if (fixture.read) EnglishRules() else forgedExtractor()
                val forced = pipeline(forcedReads, forcedContexts, label, extractor).process(input)
                val eligible = fixture.read && fixture.intent == label
                assertEquals("${fixture.id} $label", if (eligible) 1 else 0, forcedReads.calls)
                assertEquals("${fixture.id} $label", if (eligible) 1 else 0, forcedContexts.calls)
                if (!eligible && forced.status == AgentResultStatus.FOLLOW_UP) {
                    assertTrue(forced.message.contains(input.text))
                }
                if (!fixture.read) {
                    val parsed = ParsedRequest(input, IntentPrediction(label, 1.0),
                        EnglishRules().parse(input).entities, context(input))
                    LanguageSpecialist(forcedReads).respond(parsed)
                    assertEquals(fixture.id, 0, forcedReads.calls)
                }
            }
        }
        assertEquals(mapOf("development" to 78, "exposed" to 23, "v9" to 299, "v8" to 18, "v7" to 27), positives)
    }

    @Test
    fun forgedTypesSpansAndCompleteQueriesCannotReachContextOrPorts(): Unit = runBlocking {
        fixtures().filter { it.read || it.intent == AgentIntent.CHECK_CALENDAR }.forEach { fixture ->
            val entities = EnglishRules().parse(fixture.input).entities
            val forged = if (entities.isEmpty()) listOf(listOf(ExtractedEntity(EntityKind.MEMORY,
                "forged", 0, 6, 1.0))) else entities.single().let { entity ->
                listOf(emptyList(), entities + entity,
                    listOf(entity.copy(type = EntityKind.TITLE)),
                    listOf(entity.copy(startOffset = entity.startOffset + 1, endOffset = entity.endOffset + 1)),
                    listOf(entity.copy(endOffset = entity.endOffset - 1, rawValue = entity.rawValue.dropLast(1))),
                    listOf(entity.copy(rawValue = "x".repeat(entity.rawValue.length))),
                    listOf(entity.copy(normalizedValue = NormalizedValue.Text("forged"))),
                    listOf(entity.copy(normalizedValue = NormalizedValue.Text(fixture.query + " yesterday"))))
            }
            forged.forEach { values ->
                val reads = Reads()
                val contexts = Contexts()
                val extractor = object : EntityExtractor {
                    override suspend fun extract(request: AgentRequest) = values
                }
                val result = pipeline(reads, contexts, fixture.intent, extractor).process(fixture.input)
                assertEquals(fixture.id, AgentResultStatus.FOLLOW_UP, result.status)
                assertEquals(0, contexts.calls)
                val parsed = runCatching { ParsedRequest(fixture.input, IntentPrediction(fixture.intent, 1.0),
                    values, context(fixture.input)) }
                if (parsed.isSuccess) {
                    assertTrue(LanguageSpecialist(reads).respond(parsed.getOrThrow()) is SpecialistResult.Clarification)
                } else assertTrue(parsed.exceptionOrNull() is IllegalArgumentException)
                assertEquals(0, reads.calls)
            }
        }
    }

    @Test
    fun contentNegationNeverExemptsOuterNegationFiltersOrExtraInstructions(): Unit = runBlocking {
        val valid = listOf("Retrieve the tasks I have not completed",
            "I have like twelve things to do and I can't remember any of them",
            "I wrote something about not swimming after 6pm. why did i write that",
            "What did I note about the weather in Jaipur in October")
        valid.forEach { text ->
            assertTrue(!EnglishRules().parse(request(text)).negated)
            listOf("Do not $text", "$text from yesterday", "$text and send results", "$text. Delete tasks",
                "$text\u200b", "$text\uDB40\uDC20", "$text\n").forEach { invalid ->
                ReadSafetyV10.intents.forEach { label ->
                    val reads = Reads()
                    val contexts = Contexts()
                    pipeline(reads, contexts, label).process(request(invalid))
                    assertEquals(invalid, 0, reads.calls)
                    assertEquals(invalid, 0, contexts.calls)
                }
            }
        }
    }

    @Test
    fun annotationAndRequestBoundsStayIndependentOfTheRaisedQueryLimit(): Unit = runBlocking {
        listOf("Show me notes about " + "x".repeat(201),
            "Show me notes about " + "🚀".repeat(99) + "xyz",
            " ".repeat(500) + "Show my tasks").forEach { text ->
            val contexts = Contexts()
            val reads = Reads()
            pipeline(reads, contexts, AgentIntent.CHECK_MEMORY).process(request(text))
            assertEquals(0, contexts.calls)
            assertEquals(0, reads.calls)
        }
        InputSource.entries.forEach { source ->
            val input = request("Would you please show me notes about The Kinks").copy(source = source)
            val contexts = Contexts()
            val reads = Reads()
            pipeline(reads, contexts, AgentIntent.CHECK_MEMORY).process(input)
            val allowed = source in setOf(InputSource.TEXT, InputSource.VOICE)
            assertEquals(if (allowed) 1 else 0, contexts.calls)
            assertEquals(if (allowed) 1 else 0, reads.calls)
        }
    }

    private fun fixtures(): List<Fixture> {
        val directory = File(System.getProperty("metis.projectDir"), "../docs/evaluation/phase15/read_coverage_v10")
        val inputs = File(directory, "regression-input.tsv").readLines().drop(1).associate {
            it.split('\t').let { fields ->
                fields[0] to request(decode(fields[1])).copy(source = InputSource.valueOf(fields[2]))
            }
        }
        return File(directory, "regression-expectations.tsv").readLines().drop(1).map {
            val fields = it.split('\t')
            Fixture(fields[0], fields[1], AgentIntent.valueOf(fields[2]), fields[3] == "read",
                requireNotNull(inputs[fields[0]]), decode(fields[5]), fields[6])
        }
    }

    private data class Fixture(val id: String, val section: String, val intent: AgentIntent,
        val read: Boolean, val input: AgentRequest, val query: String, val spans: String)

    private fun pipeline(reads: Reads, contexts: Contexts, label: AgentIntent? = null,
                         extractor: EntityExtractor = EnglishRules()) = LocalAgentOrchestrator(
        if (label == null) EnglishRules() else object : IntentClassifier {
            override suspend fun classify(request: AgentRequest) = IntentPrediction(label, 1.0)
        }, extractor, contexts, LanguageSpecialist(reads), ProposalPolicy())

    private fun forgedExtractor() = object : EntityExtractor {
        override suspend fun extract(request: AgentRequest) = listOf(ExtractedEntity(EntityKind.MEMORY,
            "forged", 0, 6, 1.0, NormalizedValue.Text("replacement query")))
    }

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
