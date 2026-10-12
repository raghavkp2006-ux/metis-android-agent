package dev.metis.agent

import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
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
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** New classifier development requests must not weaken production read admission. */
class ClassifierV11SafetyTest {
    @Test
    fun everyProhibitedRequestRejectsEveryForcedReadLabelBeforeContext(): Unit = runBlocking {
        val directory = File(System.getProperty("metis.projectDir"), "../docs/evaluation/phase15/classifier_v11")
        val inputs = File(directory, "development-input.tsv").readLines().drop(1).associate {
            val fields = it.split('\t')
            fields[0] to AgentRequest(UUID.randomUUID(),
                String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8), InputSource.TEXT,
                Instant.parse(fields[3]))
        }
        var attempts = 0
        File(directory, "development-expectations.tsv").readLines().drop(1).forEach { line ->
            val fields = line.split('\t')
            if (fields[2] == "read") return@forEach
            val input = requireNotNull(inputs[fields[0]])
            ReadSafetyV10.intents.forEach { label ->
                attempts++
                var contexts = 0
                val reads = Reads()
                val prediction = IntentPrediction(label, 1.0)
                val classifier = object : IntentClassifier {
                    override suspend fun classify(request: AgentRequest) = prediction
                }
                val extractor = object : EntityExtractor {
                    override suspend fun extract(request: AgentRequest) = listOf(ExtractedEntity(
                        EntityKind.MEMORY, "forged", 0, 6, 1.0, NormalizedValue.Text("replacement query")))
                }
                val builder = object : ContextBuilder {
                    override suspend fun snapshot(request: AgentRequest): ResolvedContext {
                        contexts++
                        return context(request)
                    }
                }
                val result = LocalAgentOrchestrator(classifier, extractor, builder,
                    LanguageSpecialist(reads), ProposalPolicy()).process(input)
                assertEquals("${fields[0]} $label", 0, contexts)
                assertEquals(fields[0], 0, reads.calls)
                assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
                // Direct specialist calls must also validate the complete request.
                LanguageSpecialist(reads).respond(ParsedRequest(input, prediction,
                    EnglishRules().parse(input).entities, context(input)))
                assertEquals(fields[0], 0, reads.calls)
            }
        }
        assertTrue(attempts > 10_000)
    }

    private fun context(request: AgentRequest) = ResolvedContext(request.timestamp, ZoneId.of("Asia/Kolkata"),
        AutonomyLevel.ANSWER_ONLY, CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))

    private class Reads : AgentReadPort {
        var calls = 0
        override suspend fun tasks(): String { calls++; return "Synthetic tasks" }
        override suspend fun memories(query: String, at: Long): String { calls++; return "Synthetic memory" }
        override suspend fun promises(person: String): SpecialistResult {
            calls++; return SpecialistResult.Answer("Synthetic promises")
        }
    }
}
