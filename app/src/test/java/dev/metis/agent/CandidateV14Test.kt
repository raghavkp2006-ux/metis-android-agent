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
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.ParsedRequest
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.agent.ReadSafetyV14
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.MemorySearchText
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Current app candidate, including the complete retained corpus, independent new gold and exposed human drafts. */
class CandidateV14Test {
    private val root = requireNotNull(File(requireNotNull(System.getProperty("metis.projectDir"))).parentFile)
    private val time = Instant.parse("2026-10-12T05:30:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val rules = EnglishRules(ReadCandidate.V14) { zone }

    @Test fun completeNativePythonReplayAndAllForcedReadLabels(): Unit = runBlocking {
        val input = File(root, "docs/evaluation/phase15/candidate_v14/regression.tsv")
        val output = mutableListOf("# input_sha256=${hash(input.readBytes())}",
            "# production_sha256=${productionHash()}",
            "id\tintent\tstatus\treads\tcontexts\tquery_base64\tmessage_base64")
        fixtures().forEach { row ->
            val text = decode(row[1])
            val request = request(text).copy(source = InputSource.valueOf(row[2]))
            val expected = row[4].toInt()
            val parsed = rules.parse(request)
            val ports = Ports()
            val result = pipeline(ports).process(request)
            assertEquals(row[0], expected, ports.reads)
            if (expected > 0) {
                assertEquals(row[0], row[3], parsed.intent.name)
                assertEquals(row[0], decode(row[5]), ports.query)
                assertEquals(row[0], decode(row[6]), parsed.entities.joinToString(";") {
                    "${it.type}:${it.startOffset}:${it.endOffset}:${encode(it.rawValue)}"
                })
            }
            if (result.status.name == "FOLLOW_UP") assertTrue(row[0], result.message.contains(text))
            ReadSafetyV14.intents.forEach { label ->
                val forced = object : IntentClassifier {
                    override suspend fun classify(request: AgentRequest) = IntentPrediction(label, 1.0)
                }
                val forcedPorts = Ports()
                pipeline(forcedPorts, forced).process(request)
                val eligible = if (expected > 0 && row[3] == label.name) 1 else 0
                assertEquals("${row[0]}/$label", eligible, forcedPorts.reads)
                assertEquals("${row[0]}/$label", eligible, forcedPorts.contexts)
            }
            output.add(listOf(row[0], parsed.intent.name, result.status.name, ports.reads.toString(),
                ports.contexts.toString(), encode(ports.query), encode(result.message)).joinToString("\t"))
        }
        File(root, "app/build/reports/phase15/v14/native.tsv").apply {
            requireNotNull(parentFile).mkdirs()
            writeText(output.joinToString("\n", postfix = "\n"))
        }
    }

    @Test fun forgedSpansTypesAndCompleteQueriesFailBeforeContextAndDispatch(): Unit = runBlocking {
        fixtures().filter { it[4] == "1" && it[3] == "CHECK_MEMORY" }.forEach { row ->
            val request = request(decode(row[1])).copy(source = InputSource.valueOf(row[2]))
            val original = rules.parse(request).entities.single()
            val forged = listOf(original.copy(startOffset = original.startOffset + 1,
                    endOffset = original.endOffset + 1),
                original.copy(endOffset = original.endOffset + 1, rawValue = original.rawValue + "x"),
                original.copy(type = EntityKind.PERSON), original.copy(rawValue = "#".repeat(original.rawValue.length)),
                original.copy(normalizedValue = NormalizedValue.Text("forged")))
            forged.forEach { value ->
                val extractor = object : EntityExtractor {
                    override suspend fun extract(request: AgentRequest) = listOf(value)
                }
                val ports = Ports()
                pipeline(ports, extractor = extractor).process(request)
                assertEquals(row[0], 0, ports.contexts)
                assertEquals(row[0], 0, ports.reads)
                val direct = try {
                    ParsedRequest(request, IntentPrediction(AgentIntent.CHECK_MEMORY, 1.0),
                        listOf(value), ports.context(request))
                } catch (_: IllegalArgumentException) { null } // Structural span rejection is also before dispatch.
                if (direct != null) LanguageSpecialist(ports, localCalendar = true,
                    readCandidate = ReadCandidate.V14).respond(direct)
                assertEquals(row[0], 0, ports.reads)
            }
        }
    }

    @Test fun retainedQueryBoundsAndNewArticleAttributionAreExact(): Unit = runBlocking {
        val prefix = "what the doctor at the " + "c".repeat(180) + " said about "
        val query = prefix + "g".repeat(256 - prefix.length)
        val ports = Ports()
        pipeline(ports).process(request("Did I write down $query"))
        assertEquals(query, ports.query)
        assertEquals(1, ports.reads)
        val oversized = Ports()
        pipeline(oversized).process(request("Did I write down ${query}g"))
        assertEquals(0, oversized.reads)
        assertEquals(0, oversized.contexts)
        val titled = rules.parse(request("What do you remember about The Who?"))
        assertEquals("Who", titled.entities.single().rawValue)
        assertEquals("The Who", (titled.entities.single().normalizedValue as NormalizedValue.Text).value)
        val speech = rules.parse(request("What was your recollection of my talk with Zoë O’Neil?"))
        assertEquals("Zoë O’Neil", speech.entities.single().rawValue)
        assertEquals("my talk with Zoë O’Neil",
            (speech.entities.single().normalizedValue as NormalizedValue.Text).value)
        assertEquals(AgentIntent.UNKNOWN,
            EnglishRules().parse(request("What do you remember about The Who?")).intent)
    }

    @Test fun memorySearchIgnoresLoneEmojiMarksAndPreservesAccentedWords() {
        assertEquals("\"quantum\" \"resonator\"", MemorySearchText.expression("quantum 🛰️ resonator"))
        assertEquals("\"café\" \"receiver\"", MemorySearchText.expression("café receiver"))
        assertEquals(null, MemorySearchText.expression("🛰️"))
    }

    private fun fixtures() = File(root, "docs/evaluation/phase15/candidate_v14/regression.tsv")
        .readLines().drop(1).map { it.split('\t') }.also { require(it.size == 9366) }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun productionHash(): String {
        val directory = File(root, "app/src/main/java/dev/metis/agent/domain/agent")
        val sources = directory.listFiles().orEmpty().filter { it.extension == "kt" } +
            listOf(File(root, "app/src/main/java/dev/metis/agent/presentation/navigation/AgentApp.kt"),
                File(root, "app/src/main/java/dev/metis/agent/domain/storage/MemorySearch.kt"))
        val digest = MessageDigest.getInstance("SHA-256")
        sources.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach {
            digest.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(it.readText().replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, time)
    private fun encode(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    private fun pipeline(ports: Ports, classifier: IntentClassifier = rules, extractor: EntityExtractor = rules) =
        LocalAgentOrchestrator(classifier, extractor, ports,
            LanguageSpecialist(ports, localCalendar = true, readCandidate = ReadCandidate.V14), ProposalPolicy(),
            localCalendar = true, readCandidate = ReadCandidate.V14)

    private inner class Ports : AgentReadPort, ContextBuilder {
        var reads = 0
        var contexts = 0
        var query = ""
        fun context(request: AgentRequest) = ResolvedContext(request.timestamp, zone, AutonomyLevel.ANSWER_ONLY,
            CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))
        override suspend fun snapshot(request: AgentRequest): ResolvedContext { contexts++; return context(request) }
        override suspend fun tasks(): String { reads++; return "Synthetic tasks" }
        override suspend fun memories(query: String, at: Long): String {
            reads++; this.query = query; return "Synthetic notes"
        }
        override suspend fun promises(person: String): SpecialistResult {
            reads++; query = person; return SpecialistResult.Answer("Synthetic promises")
        }
        override suspend fun calendar(scope: CalendarReadScope, now: Long, zone: ZoneId): SpecialistResult {
            reads++; return SpecialistResult.Answer("Synthetic calendar")
        }
    }
}
