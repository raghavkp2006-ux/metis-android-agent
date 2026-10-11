package dev.metis.agent.evaluation

import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.ExtractedEntity
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.languageAgentOrchestrator
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** Evaluation entry point only; never compiled into a debug or release application APK. */
object Phase15RulesSnapshot {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        require(args.size == 3) { "Expected input, output, and repository paths" }
        val rows = File(args[0]).readLines(Charsets.UTF_8).filter(String::isNotBlank)
        require(rows.first() == "id\ttext_base64\tsource\ttimestamp\tzone")
        val seen = mutableSetOf<String>()
        val output = buildString {
            appendLine("# input_sha256=${hash(File(args[0]).readBytes())}")
            appendLine("# source_sha256=${sourceHash(File(args[2]))}")
            appendLine("# exporter_sha256=${hash(File(args[2], EXPORTER_PATH).readBytes())}")
            appendLine("id\tintent\tnegated\tstatus\tentities\treads\tproposals\tcompleted\tquery_base64")
            rows.drop(1).forEach { line ->
                val fields = line.split('\t')
                require(fields.size == INPUT_COLUMNS && seen.add(fields[0]))
                appendLine(snapshot(fields))
            }
        }
        File(args[1]).apply { requireNotNull(parentFile).mkdirs(); writeText(output, Charsets.UTF_8) }
        println("Exported ${seen.size} synthetic requests using production EnglishRules")
    }

    private suspend fun snapshot(fields: List<String>): String {
        val text = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
        val zone = ZoneId.of(fields[4])
        val request = AgentRequest(UUID.nameUUIDFromBytes(fields[0].toByteArray(Charsets.UTF_8)),
            text, InputSource.valueOf(fields[2]), Instant.parse(fields[3]))
        val parsed = EnglishRules { zone }.parse(request)
        val reads = SyntheticReads()
        val result = languageAgentOrchestrator(reads) { zone }.process(request)
        return listOf(fields[0], parsed.intent.name, parsed.negated.toString(), result.status.name,
            parsed.entities.joinToString(";", transform = ::encodeEntity), reads.calls.toString(),
            result.proposals.size.toString(), result.completedActions.size.toString(),
            encode(reads.query)).joinToString("\t")
    }

    internal fun encodeEntity(entity: ExtractedEntity): String {
        val normalized = when (val value = entity.normalizedValue) {
            is NormalizedValue.Time -> value.value.instant.toString()
            is NormalizedValue.Duration -> value.seconds.toString()
            is NormalizedValue.Reference -> value.value.id.toString()
            // Literal retrieval text has its own query_base64 column. Keep the
            // historical annotation schema distinct from that dispatch value.
            is NormalizedValue.Text -> ""
            null -> ""
        }
        return listOf(entity.type.name, entity.startOffset.toString(), entity.endOffset.toString(),
            encode(entity.rawValue), encode(normalized)).joinToString(":")
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun sourceHash(root: File): String {
        val agent = File(root, "app/src/main/java/dev/metis/agent/domain/agent")
        val sources = agent.listFiles().orEmpty().filter { it.extension == "kt" } +
            File(root, "app/src/main/java/dev/metis/agent/domain/planner/PlannerWindow.kt")
        val digest = MessageDigest.getInstance("SHA-256")
        sources.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach {
            digest.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(it.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class SyntheticReads : AgentReadPort {
        var calls = 0
        var query = ""
        override suspend fun tasks(): String { calls++; return "Synthetic task fixture" }
        override suspend fun memories(query: String, at: Long): String {
            calls++
            this.query = query
            return "Synthetic memory fixture"
        }
        override suspend fun promises(person: String): SpecialistResult {
            calls++
            query = person
            return SpecialistResult.Answer("Synthetic promise fixture")
        }
    }

    private const val INPUT_COLUMNS = 5
    private const val EXPORTER_PATH = "app/src/test/java/dev/metis/agent/evaluation/Phase15RulesSnapshot.kt"
}
