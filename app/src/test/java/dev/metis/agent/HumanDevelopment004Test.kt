package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.CalendarReadScope
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
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReadSafetyV12
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exposed unapproved intake: behavior diagnostics and gate invariants, never gold-label scoring. */
class HumanDevelopment004Test {
    private val root = requireNotNull(File(requireNotNull(System.getProperty("metis.projectDir"))).parentFile)
    private val input = File(root, "docs/evaluation/phase15/human_development_004/input.tsv")
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun exportCurrentLocalCalendarEnabledBehavior(): Unit = runBlocking {
        val output = mutableListOf(
            "# input_sha256=${hash(input)}",
            "# runner_sha256=${hash(File(root, RUNNER))}",
            "# v13_manifest_sha256=${hash(File(root,
                "docs/evaluation/phase15/candidate_v13/development-manifest-v13.json"))}",
            "# local_calendar=true; synthetic_ports=true; human_approved=false; ungraded=true",
            "id\tintent\tstatus\tcontexts\treads\tentities\tquery_base64\tcalendar_base64\tmessage_base64",
        )
        requests().forEach { (id, request) ->
            val rules = EnglishRules { zone }
            val parsed = rules.parse(request)
            val ports = Ports()
            val result = pipeline(ports, rules, rules).process(request)
            assertTrue(id, result.proposals.isEmpty())
            assertTrue(id, result.completedActions.isEmpty())
            if (!ReadSafetyV12.allows(request, parsed.intent)) {
                assertEquals(id, 0, ports.reads)
            }
            if (result.status.name == "FOLLOW_UP") assertTrue(id, result.message.contains(request.text))
            val entities = parsed.entities.joinToString(";") {
                "${it.type}:${it.startOffset}:${it.endOffset}:${encode(it.rawValue)}"
            }
            output.add(listOf(id, parsed.intent.name, result.status.name, ports.contexts.toString(),
                ports.reads.toString(), entities, encode(ports.query), encode(ports.scope),
                encode(result.message)).joinToString("\t"))
        }
        File(root, "app/build/reports/phase15/human-development-004/native.tsv").apply {
            requireNotNull(parentFile).mkdirs()
            writeText(output.joinToString("\n", postfix = "\n"))
        }
    }

    @Test fun forcedReadLabelsAndForgedExtractionCannotBypassAdmission(): Unit = runBlocking {
        requests().forEach { (id, request) ->
            ReadSafetyV12.intents.forEach { intent ->
                val ports = Ports()
                val forced = object : IntentClassifier {
                    override suspend fun classify(request: AgentRequest) = IntentPrediction(intent, 1.0)
                }
                val result = pipeline(ports, forced, EnglishRules { zone }).process(request)
                if (!ReadSafetyV12.allows(request, intent)) {
                    assertEquals("$id/$intent", 0, ports.contexts)
                    assertEquals("$id/$intent", 0, ports.reads)
                    assertTrue(id, result.message.contains(request.text))
                }
                val forged = object : EntityExtractor {
                    override suspend fun extract(request: AgentRequest) = listOf(
                        ExtractedEntity(EntityKind.MEMORY, request.text.take(1), 0, 1, 1.0))
                }
                assertFalse(id, ReadSafetyV12.matches(request, intent, forged.extract(request)))
                val forgedPorts = Ports()
                pipeline(forgedPorts, forced, forged).process(request)
                assertEquals("$id/$intent forged", 0, forgedPorts.contexts)
                assertEquals("$id/$intent forged", 0, forgedPorts.reads)
            }
        }
    }

    private fun requests(): List<Pair<String, AgentRequest>> {
        val lines = input.readLines()
        require(lines.first() == "id\ttext_base64\tsource\ttimestamp\tzone")
        val rows = lines.drop(1).map { line ->
            val fields = line.split('\t')
            require(fields.size == 5 && fields[4] == zone.id)
            val text = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
            fields[0] to AgentRequest(UUID.nameUUIDFromBytes(fields[0].toByteArray()), text,
                InputSource.valueOf(fields[2]), Instant.parse(fields[3]))
        }
        require(rows.size == 214 && rows.map { it.first }.toSet().size == rows.size)
        return rows
    }

    private fun pipeline(ports: Ports, classifier: IntentClassifier, extractor: EntityExtractor) =
        LocalAgentOrchestrator(classifier, extractor, ports,
            LanguageSpecialist(ports, localCalendar = true), ProposalPolicy(), localCalendar = true)

    private inner class Ports : AgentReadPort, ContextBuilder {
        var contexts = 0
        var reads = 0
        var query = ""
        var scope = ""
        override suspend fun snapshot(request: AgentRequest): ResolvedContext {
            contexts++
            return ResolvedContext(request.timestamp, zone, AutonomyLevel.ANSWER_ONLY,
                CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))
        }
        override suspend fun tasks(): String { reads++; return "Synthetic tasks; not user data" }
        override suspend fun memories(query: String, at: Long): String {
            reads++
            this.query = query
            return "Synthetic memories; not user data"
        }
        override suspend fun promises(person: String): SpecialistResult {
            reads++
            query = person
            return SpecialistResult.Answer("Synthetic promises; not user data")
        }
        override suspend fun calendar(scope: CalendarReadScope, now: Long, zone: ZoneId): SpecialistResult {
            reads++
            this.scope = scope.toString()
            return SpecialistResult.Answer("Synthetic calendar; not user data")
        }
    }

    private fun encode(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val RUNNER = "app/src/test/java/dev/metis/agent/HumanDevelopment004Test.kt"
    }
}
