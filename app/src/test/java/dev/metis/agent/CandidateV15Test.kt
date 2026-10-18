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
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.IntentClassifier
import dev.metis.agent.domain.agent.IntentPrediction
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.LocalAgentOrchestrator
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.ParsedRequest
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.agent.ReadDateV15
import dev.metis.agent.domain.agent.ReadSafetyV12
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.ReadTextV15
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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent new gold plus complete retained regression, with real pre-context counters. */
class CandidateV15Test {
    private val root = requireNotNull(File(requireNotNull(System.getProperty("metis.projectDir"))).parentFile)
    private val zone = ZoneId.of("Asia/Kolkata")
    private val at = Instant.parse("2026-10-12T05:30:00Z")
    private val rules = EnglishRules(ReadCandidate.V15) { zone }

    @org.junit.Ignore("Archived Phase 0: requires historical docs/evaluation fixtures.")
    @Test fun nativePythonScopesEntitiesQueriesAndForcedLabels(): Unit = runBlocking {
        val input = File(root, "docs/evaluation/phase15/candidate_v15/regression.tsv")
        val output = mutableListOf("# input_sha256=${hash(input.readBytes())}",
            "# production_sha256=${productionHash()}",
            "id\tintent\tstatus\treads\tcontexts\tquery_base64\tmessage_base64\tscope_base64")
        rows().forEach { row ->
            val request = request(decode(row[1])).copy(source = InputSource.valueOf(row[2]))
            val expected = row[4].toInt()
            val parsed = rules.parse(request)
            val ports = Ports()
            val answer = pipeline(ports).process(request)
            assertEquals(row[0], expected, ports.reads)
            if (expected > 0) {
                assertEquals(row[0], row[3], parsed.intent.name)
                assertEquals(row[0], decode(row[5]), ports.query)
                assertEquals(row[0], decode(row[6]), parsed.entities.joinToString(";") {
                    "${it.type}:${it.startOffset}:${it.endOffset}:${encode(it.rawValue)}"
                })
                if (row[7].isNotEmpty()) assertEquals(row[0], decode(row[7]), audit(requireNotNull(parsed.readScope)))
            }
            if (answer.status == AgentResultStatus.FOLLOW_UP) assertTrue(row[0], answer.message.contains(request.text))
            ReadSafetyV12.intents.forEach { label ->
                val forced = object : IntentClassifier {
                    override suspend fun classify(request: AgentRequest) = IntentPrediction(label, 1.0)
                }
                val probe = Ports()
                pipeline(probe, forced).process(request)
                val admitted = if (expected == 1 && label.name == row[3]) 1 else 0
                assertEquals("${row[0]}/$label", admitted, probe.reads)
                assertEquals("${row[0]}/$label", admitted, probe.contexts)
            }
            output.add(listOf(row[0], parsed.intent.name, answer.status.name, ports.reads.toString(),
                ports.contexts.toString(),
                encode(ports.query), encode(answer.message),
                    encode(parsed.readScope?.let(::audit).orEmpty())).joinToString("\t"))
        }
        File(root, "app/build/reports/phase15/v15/native.tsv").apply {
            requireNotNull(parentFile).mkdirs(); writeText(output.joinToString("\n", postfix = "\n"))
        }
    }

    @org.junit.Ignore("Archived Phase 0: requires historical docs/evaluation fixtures.")
    @Test fun forgedScopesAndExtractionNeverReachReadPorts(): Unit = runBlocking {
        rows().filter { it[4] == "1" }.forEach { row ->
            val request = request(decode(row[1])).copy(source = InputSource.valueOf(row[2]))
            val parsed = rules.parse(request)
            val ports = Ports()
            forgedScopes(requireNotNull(parsed.readScope)).forEach { scope ->
                val forged = ParsedRequest(request, parsed.prediction, parsed.entities, ports.context(request), scope)
                assertTrue(row[0], LanguageSpecialist(ports, true,
                    ReadCandidate.V15).respond(forged) is SpecialistResult.Clarification)
            }
            assertEquals(row[0], 0, ports.reads)
            parsed.entities.firstOrNull()?.let { entity ->
                val alterations = listOf(entity.copy(normalizedValue = NormalizedValue.Text("forged")),
                    entity.copy(startOffset = entity.startOffset + 1, endOffset = entity.endOffset + 1),
                    entity.copy(type = if (entity.type == EntityKind.PERSON) EntityKind.MEMORY else EntityKind.PERSON))
                alterations.forEach { changed ->
                    val extractor = object : EntityExtractor {
                        override suspend fun extract(request: AgentRequest) = listOf(changed)
                    }
                    pipeline(ports, extractor = extractor).process(request)
                    assertEquals(row[0], 0, ports.contexts)
                    assertEquals(row[0], 0, ports.reads)
                }
            }
        }
    }

    @Test fun dateWindowsAreLocalBoundedAndPolarityIsLiteral() {
        val dst = ZoneId.of("America/New_York")
        val span = ReadDateV15(ReadDateV15.Field.DUE, "2026-03-08").window(at, dst)
        assertEquals(23 * 60 * 60 * 1000L, span.second - span.first)
        assertTrue(!ReadDateV15.valid("2026-02-30"))
        assertTrue(!ReadDateV15.valid("Friday"))
        assertTrue(ReadTextV15.matches("I would not review The Who today", "not review The Who today", true, true))
        assertTrue(!ReadTextV15.matches("I would review The Who today", "not review The Who today", true, true))
        assertTrue(!ReadTextV15.matches("I would not review The Who today", "review The Who", false, true))
        assertTrue(!ReadTextV15.matches("Who", "The Who"))
    }

    @Test fun missingRecipientAndInvalidDatesHaveNoContextOrReads(): Unit = runBlocking {
        for (text in listOf("Did I promise to send the circuit diagram?", "Did I tell her I would review the code?",
            "Find tasks about the report due Friday", "Find tasks about the report due 2026-02-30",
            "What email address did I save for " + "x".repeat(201), "What do you remember about the report?\u200b")) {
            val ports = Ports()
            val answer = pipeline(ports).process(request(text))
            assertTrue(text, answer.status in setOf(AgentResultStatus.FOLLOW_UP, AgentResultStatus.DENIED))
            assertEquals(text, 0, ports.contexts); assertEquals(text, 0, ports.reads)
        }
        assertEquals(AgentIntent.UNKNOWN, EnglishRules().parse(request("Do I have a task to review The Who?")).intent)
    }

    private fun audit(scope: ReadScopeV15): String {
        val values = when (scope) {
            is ReadScopeV15.Tasks -> listOf("TASK", scope.subject, "", scope.status, scope.date?.field?.name.orEmpty(),
                scope.date?.expression.orEmpty(), scope.requestedField, "false")
            is ReadScopeV15.Memories -> listOf("MEMORY", scope.subject, "", "", scope.date?.field?.name.orEmpty(),
                scope.date?.expression.orEmpty(), scope.requestedField, scope.negative.toString())
            is ReadScopeV15.Promises -> listOf("PROMISE", scope.subject, scope.person, scope.status,
                scope.date?.field?.name.orEmpty(),
                scope.date?.expression.orEmpty(), "", scope.negative.toString())
            is ReadScopeV15.Calendar -> listOf("CALENDAR", scope.subject, scope.person, "",
                scope.date?.field?.name.orEmpty(),
                scope.date?.expression.orEmpty(), "", "false")
        }
        return values.joinToString("|")
    }

    private fun forgedScopes(scope: ReadScopeV15): List<ReadScopeV15> = when (scope) {
        is ReadScopeV15.Tasks -> listOf(scope.copy(subject = "forged"), scope.copy(status = "FORGED"),
            scope.copy(date = ReadDateV15(ReadDateV15.Field.CREATED, "2027-01-01")),
            scope.copy(requestedField = "assignee"), ReadScopeV15.Memories("forged"))
        is ReadScopeV15.Memories -> listOf(scope.copy(subject = "forged"), scope.copy(negative = !scope.negative),
            scope.copy(date = ReadDateV15(ReadDateV15.Field.DUE, "2027-01-01")),
            scope.copy(requestedField = "assignee"), ReadScopeV15.Tasks())
        is ReadScopeV15.Promises -> listOf(scope.copy(subject = "forged"), scope.copy(person = "Forged Person"),
            scope.copy(status = "FORGED"), scope.copy(negative = !scope.negative),
            scope.copy(date = ReadDateV15(ReadDateV15.Field.CREATED, "2027-01-01")), ReadScopeV15.Tasks())
        is ReadScopeV15.Calendar -> listOf(scope.copy(subject = "forged"), scope.copy(person = "Forged Person"),
            scope.copy(meetings = !scope.meetings),
            scope.copy(date = ReadDateV15(ReadDateV15.Field.DUE, "2027-01-01")), ReadScopeV15.Tasks())
    }

    private fun productionHash(): String {
        val sources = File(root, "app/src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
        val digest = MessageDigest.getInstance("SHA-256")
        sources.forEach {
            digest.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(it.readText().replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun rows() = File(root,
        "docs/evaluation/phase15/candidate_v15/regression.tsv").readLines().drop(1).map { it.split('\t') }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, at)
    private fun encode(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    private fun pipeline(ports: Ports, classifier: IntentClassifier = rules, extractor: EntityExtractor = rules) =
        LocalAgentOrchestrator(classifier, extractor, ports, LanguageSpecialist(ports, true, ReadCandidate.V15),
            ProposalPolicy(),
            localCalendar = true, readCandidate = ReadCandidate.V15)

    private inner class Ports : AgentReadPort, ContextBuilder {
        var reads = 0
        var contexts = 0
        var query = ""
        fun context(request: AgentRequest) = ResolvedContext(request.timestamp, zone, AutonomyLevel.ANSWER_ONLY,
            CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))
        override suspend fun snapshot(request: AgentRequest): ResolvedContext { contexts++; return context(request) }
        private fun answer(value: String): SpecialistResult {
            reads++; query = value
            return SpecialistResult.Answer("Synthetic saved records")
        }
        override suspend fun tasks(scope: ReadScopeV15.Tasks, now: Long, zone: ZoneId) = answer(scope.subject)
        override suspend fun memories(scope: ReadScopeV15.Memories, now: Long, zone: ZoneId) = answer(scope.subject)
        override suspend fun promises(scope: ReadScopeV15.Promises, now: Long,
            zone: ZoneId) = answer(scope.subject.ifEmpty { scope.person })
        override suspend fun calendar(scope: ReadScopeV15.Calendar, now: Long,
            zone: ZoneId) = answer(scope.subject.ifEmpty { scope.person })
        override suspend fun tasks(): String = error("V15 must use typed scopes")
        override suspend fun memories(query: String, at: Long): String = error("V15 must use typed scopes")
        override suspend fun promises(person: String): SpecialistResult = error("V15 must use typed scopes")
    }
}
