@file:Suppress("MagicNumber", "LongMethod", "MaxLineLength", "NestedBlockDepth", "CyclomaticComplexMethod", "ReturnCount", "LoopWithTooManyJumpStatements", "ImplicitDefaultLocale")

package dev.metis.agent.ml

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReadCandidate
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Test

data class CorpusViolation(
    val corpus: String,
    val id: String,
    val utterance: String,
    val predictedIntent: AgentIntent,
    val confidence: Double,
    val isDestructive: Boolean
)

class CandidateCorporaEnumerationTest {

    private val now = Instant.parse("2026-10-12T05:30:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val decoder = Base64.getDecoder()

    private fun findFile(path: String): File {
        val candidates = listOf(File(path), File("app", path), File("..", path), File("../app", path))
        return candidates.firstOrNull { it.exists() } ?: File(path)
    }

    private fun decode(s: String) = String(decoder.decode(s), Charsets.UTF_8)

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, now)

    private val destructiveIntents = setOf(
        AgentIntent.COMPLETE_TASK,
        AgentIntent.DELETE_TASK,
        AgentIntent.POSTPONE_TASK,
        AgentIntent.UPDATE_TASK
    )

    private val writeIntents = setOf(
        AgentIntent.CREATE_TASK,
        AgentIntent.CREATE_REMINDER,
        AgentIntent.CREATE_PROMISE,
        AgentIntent.START_REFLECTION,
        AgentIntent.CREATE_ALARM,
        AgentIntent.CREATE_TIMER,
        AgentIntent.CALL_PERSON,
        AgentIntent.SEND_MESSAGE
    ) + destructiveIntents

    private fun extractField(obj: String, key: String): String? {
        val target = "\"$key\""
        var idx = obj.indexOf(target)
        if (idx == -1) return null
        idx = obj.indexOf(':', idx + target.length)
        if (idx == -1) return null
        idx = obj.indexOf('"', idx)
        if (idx == -1) return null
        val sb = StringBuilder()
        var cur = idx + 1
        while (cur < obj.length) {
            val c = obj[cur]
            if (c == '"') return sb.toString()
            if (c == '\\' && cur + 1 < obj.length) {
                cur++
                when (val esc = obj[cur]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        val hex = obj.substring(cur + 1, (cur + 5).coerceAtMost(obj.length))
                        cur += 4
                        sb.append(hex.toInt(16).toChar())
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(c)
            }
            cur++
        }
        return null
    }

    private fun extractBooleanField(obj: String, key: String): Boolean {
        val target = "\"$key\""
        var idx = obj.indexOf(target)
        if (idx == -1) return false
        idx = obj.indexOf(':', idx + target.length)
        if (idx == -1) return false
        var cur = idx + 1
        while (cur < obj.length && obj[cur].isWhitespace()) cur++
        return obj.startsWith("true", cur)
    }

    private fun parseCorpusJsonRows(file: File): List<Triple<String, String, Boolean>> {
        val json = file.readText(Charsets.UTF_8)
        val rows = mutableListOf<Triple<String, String, Boolean>>()
        var idx = json.indexOf("\"rows\"")
        if (idx == -1) return rows
        idx = json.indexOf('[', idx)
        if (idx == -1) return rows

        while (idx < json.length) {
            val startObj = json.indexOf('{', idx)
            if (startObj == -1) break
            var depth = 1
            var cur = startObj + 1
            while (cur < json.length && depth > 0) {
                if (json[cur] == '{') depth++
                else if (json[cur] == '}') depth--
                else if (json[cur] == '"') {
                    cur++
                    while (cur < json.length && json[cur] != '"') {
                        if (json[cur] == '\\') cur++
                        cur++
                    }
                }
                cur++
            }
            val objStr = json.substring(startObj, cur)
            val id = extractField(objStr, "id")
            val utterance = extractField(objStr, "utterance")
            val read = extractBooleanField(objStr, "read")
            if (id != null && utterance != null) {
                rows.add(Triple(id, utterance, read))
            }
            idx = cur
            val endArray = json.indexOf(']', cur)
            val nextObj = json.indexOf('{', cur)
            if (endArray != -1 && (nextObj == -1 || endArray < nextObj)) {
                break
            }
        }
        return rows
    }

    @Test
    fun enumerateCorporaViolations(): Unit = runBlocking {
        val violations = mutableListOf<CorpusViolation>()

        // 1. Candidate V12 (phase15-v12.tsv)
        val v12File = findFile("src/androidTest/assets/phase15-v12.tsv")
        var v12Count = 0
        if (v12File.exists()) {
            val rulesV12 = EnglishRules(ReadCandidate.V12) { ZoneId.of("UTC") }
            val lines = v12File.readLines().drop(1)
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split('\t')
                val isRead = parts[3] == "read"
                if (!isRead) {
                    v12Count++
                    val id = parts[0]
                    val text = decode(parts[1])
                    val pred = rulesV12.classify(request(text))
                    if (pred.isUnderstoodByLm && pred.intent in writeIntents) {
                        violations.add(CorpusViolation(
                            "phase15-v12.tsv", id, text, pred.intent, pred.confidence,
                            pred.intent in destructiveIntents
                        ))
                    }
                }
            }
        }

        // 2. Candidate V14 (phase15-v14.tsv)
        val v14File = findFile("src/androidTest/assets/phase15-v14.tsv")
        var v14Count = 0
        if (v14File.exists()) {
            val rulesV14 = EnglishRules(ReadCandidate.V14) { ZoneId.of("UTC") }
            val lines = v14File.readLines().drop(1)
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split('\t')
                val isRead = parts[4] == "1"
                if (!isRead) {
                    v14Count++
                    val id = parts[0]
                    val text = decode(parts[1])
                    val pred = rulesV14.classify(request(text))
                    if (pred.isUnderstoodByLm && pred.intent in writeIntents) {
                        violations.add(CorpusViolation(
                            "phase15-v14.tsv", id, text, pred.intent, pred.confidence,
                            pred.intent in destructiveIntents
                        ))
                    }
                }
            }
        }

        // 3. Candidate V15 (phase15-v15.json)
        val v15File = findFile("src/androidTest/assets/phase15-v15.json")
        var v15Count = 0
        if (v15File.exists()) {
            val rulesV15 = EnglishRules(ReadCandidate.V15) { zone }
            val rows = parseCorpusJsonRows(v15File)
            for ((id, text, isRead) in rows) {
                if (!isRead) {
                    v15Count++
                    val pred = rulesV15.classify(request(text))
                    if (pred.isUnderstoodByLm && pred.intent in writeIntents) {
                        violations.add(CorpusViolation(
                            "phase15-v15.json", id, text, pred.intent, pred.confidence,
                            pred.intent in destructiveIntents
                        ))
                    }
                }
            }
        }

        // 4. Candidate V16 (phase15-read-v16.json)
        val v16File = findFile("src/androidTest/assets/phase15-read-v16.json")
        var v16Count = 0
        if (v16File.exists()) {
            val rulesV16 = EnglishRules(ReadCandidate.V16) { zone }
            val rows = parseCorpusJsonRows(v16File)
            for ((id, text, isRead) in rows) {
                if (!isRead) {
                    v16Count++
                    val pred = rulesV16.classify(request(text))
                    if (pred.isUnderstoodByLm && pred.intent in writeIntents) {
                        violations.add(CorpusViolation(
                            "phase15-read-v16.json", id, text, pred.intent, pred.confidence,
                            pred.intent in destructiveIntents
                        ))
                    }
                }
            }
        }

        // 5. Read Coverage V7 (docs/evaluation/phase15/read_coverage_v7)
        val v7Input = findFile("docs/evaluation/phase15/read_coverage_v7/development-input.tsv")
        val v7Expect = findFile("docs/evaluation/phase15/read_coverage_v7/development-expectations.tsv")
        var v7Count = 0
        if (v7Input.exists() && v7Expect.exists()) {
            val rulesV7 = EnglishRules(ReadCandidate.V12) { ZoneId.of("UTC") }
            val inputs = v7Input.readLines().drop(1).associate {
                val parts = it.split('\t')
                parts[0] to decode(parts[1])
            }
            val expectations = v7Expect.readLines().drop(1)
            for (row in expectations) {
                val parts = row.split('\t')
                val id = parts[0]
                val isRead = parts[2] == "read"
                if (!isRead) {
                    v7Count++
                    val text = inputs[id] ?: continue
                    val pred = rulesV7.classify(request(text))
                    if (pred.isUnderstoodByLm && pred.intent in writeIntents) {
                        violations.add(CorpusViolation(
                            "read_coverage_v7", id, text, pred.intent, pred.confidence,
                            pred.intent in destructiveIntents
                        ))
                    }
                }
            }
        }

        // 6. Read Coverage V8 (docs/evaluation/phase15/read_coverage_v8)
        val v8Input = findFile("docs/evaluation/phase15/read_coverage_v8/development-input.tsv")
        val v8Expect = findFile("docs/evaluation/phase15/read_coverage_v8/development-expectations.tsv")
        var v8Count = 0
        if (v8Input.exists() && v8Expect.exists()) {
            val rulesV8 = EnglishRules(ReadCandidate.V12) { ZoneId.of("UTC") }
            val inputs = v8Input.readLines().drop(1).associate {
                val parts = it.split('\t')
                parts[0] to decode(parts[1])
            }
            val expectations = v8Expect.readLines().drop(1)
            for (row in expectations) {
                val parts = row.split('\t')
                val id = parts[0]
                val isRead = parts[2] == "read"
                if (!isRead) {
                    v8Count++
                    val text = inputs[id] ?: continue
                    val pred = rulesV8.classify(request(text))
                    if (pred.isUnderstoodByLm && pred.intent in writeIntents) {
                        violations.add(CorpusViolation(
                            "read_coverage_v8", id, text, pred.intent, pred.confidence,
                            pred.intent in destructiveIntents
                        ))
                    }
                }
            }
        }

        // 7. Read Coverage V10 (docs/evaluation/phase15/read_coverage_v10)
        val v10Input = findFile("docs/evaluation/phase15/read_coverage_v10/regression-input.tsv")
        val v10Expect = findFile("docs/evaluation/phase15/read_coverage_v10/regression-expectations.tsv")
        var v10Count = 0
        if (v10Input.exists() && v10Expect.exists()) {
            val rulesV10 = EnglishRules(ReadCandidate.V12) { ZoneId.of("UTC") }
            val inputs = v10Input.readLines().drop(1).associate {
                val parts = it.split('\t')
                parts[0] to decode(parts[1])
            }
            val expectations = v10Expect.readLines().drop(1)
            for (row in expectations) {
                val parts = row.split('\t')
                val id = parts[0]
                val isRead = parts[2] == "read"
                if (!isRead) {
                    v10Count++
                    val text = inputs[id] ?: continue
                    val pred = rulesV10.classify(request(text))
                    if (pred.isUnderstoodByLm && pred.intent in writeIntents) {
                        violations.add(CorpusViolation(
                            "read_coverage_v10", id, text, pred.intent, pred.confidence,
                            pred.intent in destructiveIntents
                        ))
                    }
                }
            }
        }

        println("================ ENUMERATION SUMMARY ================")
        println("Corpora negative counts: v12=$v12Count, v14=$v14Count, v15=$v15Count, v16=$v16Count, v7=$v7Count, v8=$v8Count, v10=$v10Count")
        val byCorpus = violations.groupBy { it.corpus }
        for ((corpus, list) in byCorpus) {
            println("Corpus: $corpus | Total Violations: ${list.size} (Destructive: ${list.count { it.isDestructive }})")
            for (v in list) {
                println("  [${v.id}] intent=${v.predictedIntent} conf=${String.format("%.4f", v.confidence)} utterance='${v.utterance}'")
            }
            println()
        }
        println("TOTAL VIOLATIONS ACROSS ALL CORPORA: ${violations.size}")
        println("=====================================================")
    }
}
