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
import dev.metis.agent.domain.agent.ReadSafetyV12
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

/** User-submitted development requests plus retained exposed regression evidence. */
class CandidateV12ReadTest {
    @Test
    @Suppress("CyclomaticComplexMethod") // Compare normal and all forged labels over the same annotated corpus.
    fun completeCorpusPreservesScopeAndBlocksEveryForcedUnsafeLabel(): Unit = runBlocking {
        val counts = mutableMapOf<String, Int>()
        fixtures().forEach { fixture ->
            val reads = Reads()
            val contexts = Contexts()
            val result = pipeline(reads, contexts).process(fixture.input)
            if (fixture.section != "v11" || !fixture.read) {
                assertEquals(fixture.id, if (fixture.read) 1 else 0, reads.calls)
            }
            if (reads.calls > 0) {
                counts[fixture.section] = (counts[fixture.section] ?: 0) + 1
                assertEquals(fixture.id, fixture.intent, EnglishRules().parse(fixture.input).intent)
                assertEquals(fixture.id, fixture.query, reads.query)
                assertEquals(fixture.id, fixture.spans, EnglishRules().parse(fixture.input).entities
                    .joinToString(";", transform = Phase15RulesSnapshot::encodeEntity))
            }
            if (fixture.section == "user" && !fixture.read) assertEquals(fixture.id, 0, contexts.calls)
            if (result.status == AgentResultStatus.FOLLOW_UP) assertTrue(result.message.contains(fixture.input.text))
            ReadSafetyV12.intents.forEach { label ->
                val forcedReads = Reads()
                val forcedContexts = Contexts()
                val eligible = reads.calls > 0 && fixture.intent == label
                val forced = pipeline(forcedReads, forcedContexts, label,
                    if (fixture.read) EnglishRules() else forgedExtractor()).process(fixture.input)
                assertEquals("${fixture.id} $label", if (eligible) 1 else 0, forcedReads.calls)
                assertEquals("${fixture.id} $label", if (eligible) 1 else 0, forcedContexts.calls)
                if (!fixture.read) {
                    LanguageSpecialist(forcedReads).respond(ParsedRequest(fixture.input,
                        IntentPrediction(label, 1.0), EnglishRules().parse(fixture.input).entities,
                        context(fixture.input)))
                    assertEquals(fixture.id, 0, forcedReads.calls)
                }
                if (!eligible && forced.status == AgentResultStatus.FOLLOW_UP) {
                    assertTrue(forced.message.contains(fixture.input.text))
                }
                assertTrue(forced.proposals.isEmpty() && forced.completedActions.isEmpty())
            }
        }
        assertEquals(mapOf("development" to 78, "exposed" to 23, "v9" to 299,
            "v8" to 18, "v7" to 27, "v11" to 30, "v12" to 30, "user" to 4), counts)
    }

    @Test
    fun completeQueryAccepts256Utf16UnitsAndRejects257BeforeContext(): Unit = runBlocking {
        val prefix = "what the doctor at the " + "c".repeat(180) + " said about "
        val query = prefix + "g".repeat(256 - prefix.length)
        val input = request("Did I write down $query")
        val reads = Reads()
        val contexts = Contexts()
        pipeline(reads, contexts).process(input)
        assertEquals(1, reads.calls)
        assertEquals(query, reads.query)
        val rejectedReads = Reads()
        val rejectedContexts = Contexts()
        pipeline(rejectedReads, rejectedContexts, AgentIntent.CHECK_MEMORY).process(input.copy(text = input.text + "g"))
        assertEquals(0, rejectedReads.calls)
        assertEquals(0, rejectedContexts.calls)
        val articleReads = Reads()
        val articleQuery = "The " + "x".repeat(200)
        pipeline(articleReads, Contexts()).process(request("Remind me of my notes about $articleQuery"))
        assertEquals(articleQuery, articleReads.query)
        assertEquals(1, articleReads.calls)
        val oversizedAnnotation = Reads()
        val annotationContext = Contexts()
        pipeline(oversizedAnnotation, annotationContext)
            .process(request("Remind me of my notes about ${articleQuery}x"))
        assertEquals(0, oversizedAnnotation.calls)
        assertEquals(0, annotationContext.calls)
    }

    @Test
    fun newRecallCannotForgeEntityTypesSpansOrCompleteQuery(): Unit = runBlocking {
        fixtures().filter { it.section in setOf("v12", "user", "v11") && it.read &&
            ReadSafetyV12.allows(it.input, it.intent) }.forEach { fixture ->
            val original = EnglishRules().parse(fixture.input).entities.single()
            listOf(emptyList(), listOf(original.copy(type = EntityKind.TITLE)),
                listOf(original.copy(rawValue = "x".repeat(original.rawValue.length))),
                listOf(original.copy(normalizedValue = NormalizedValue.Text("different subject"))),
                listOf(original.copy(startOffset = original.startOffset + 1, endOffset = original.endOffset + 1)))
                .forEach { values ->
                    val reads = Reads()
                    val contexts = Contexts()
                    val extractor = object : EntityExtractor {
                        override suspend fun extract(request: AgentRequest) = values
                    }
                    pipeline(reads, contexts, fixture.intent, extractor).process(fixture.input)
                    assertEquals(fixture.id, 0, contexts.calls)
                    assertEquals(fixture.id, 0, reads.calls)
                }
        }
    }

    private fun fixtures(): List<Fixture> {
        val directory = File(System.getProperty("metis.projectDir"), "../docs/evaluation/phase15/candidate_v12")
        val inputs = File(directory, "regression-input.tsv").readLines().drop(1).associate {
            it.split('\t').let { fields ->
                fields[0] to request(decode(fields[1])).copy(source = InputSource.valueOf(fields[2]))
            }
        }
        return File(directory, "regression-expectations.tsv").readLines().drop(1).map {
            val fields = it.split('\t')
            Fixture(fields[0], fields[1], AgentIntent.valueOf(fields[2]), fields[3] == "read",
                requireNotNull(inputs[fields[0]]), decode(fields[4]), fields[6])
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
