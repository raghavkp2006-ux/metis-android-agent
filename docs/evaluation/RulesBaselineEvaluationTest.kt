package dev.metis.agent.evaluation

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.GraphReadGrammarV18
import dev.metis.agent.domain.agent.GraphReadKindV18
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.LanguageParse
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * JVM harness for evaluating production EnglishRules and GraphReadGrammarV18
 * on the held-out NLU test sets (test.csv and raghav_test_100.csv).
 *
 * To regenerate rules predictions:
 * 1. Copy this file into app/src/test/java/dev/metis/agent/evaluation/
 * 2. Run: ./gradlew :app:testDebugUnitTest --tests dev.metis.agent.evaluation.RulesBaselineEvaluationTest
 * 3. Predictions will be output to ml/nlu_v1/heldout/rules_preds_test.json and rules_preds_smoke.json
 * 4. Remove this file from app/src/test/java/dev/metis/agent/evaluation/ to keep app/ untouched.
 */
class RulesBaselineEvaluationTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val rules = EnglishRules { zone }

    @Test
    fun evaluateRulesOnHeldoutDatasets() {
        val projectDir = File(System.getProperty("metis.projectDir") ?: ".")
        val rootDir = if (projectDir.name == "app") projectDir.parentFile else projectDir

        val testCsv = File(rootDir, "ml/nlu_v1/test.csv")
        val smokeCsv = File(rootDir, "ml/nlu_v1/heldout/raghav_test_100.csv")
        val outputDir = File(rootDir, "ml/nlu_v1/heldout")

        require(testCsv.exists()) { "test.csv not found at ${testCsv.absolutePath}" }
        require(smokeCsv.exists()) { "raghav_test_100.csv not found at ${smokeCsv.absolutePath}" }

        val testPreds = evaluateCsv(testCsv, isSmoke = false)
        val smokePreds = evaluateCsv(smokeCsv, isSmoke = true)

        File(outputDir, "rules_preds_test.json").writeText(toJson(testPreds), Charsets.UTF_8)
        File(outputDir, "rules_preds_smoke.json").writeText(toJson(smokePreds), Charsets.UTF_8)

        println("Rules evaluation finished successfully.")
        println("test.csv rules predictions: ${testPreds.size} rows")
        println("raghav_test_100.csv rules predictions: ${smokePreds.size} rows")
    }

    private data class PredictionRow(
        val index: Int,
        val text: String,
        val truth: String,
        val predicted: String,
        val rawIntent: String,
    )

    private fun evaluateCsv(file: File, isSmoke: Boolean): List<PredictionRow> {
        val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        val header = lines.first().split(",")
        val rows = lines.drop(1)

        val textCol = if (isSmoke) header.indexOf("utterance") else header.indexOf("text")
        val intentCol = if (isSmoke) header.indexOf("intended_intent") else header.indexOf("intent")

        require(textCol >= 0 && intentCol >= 0) { "Missing columns in ${file.name}" }

        val results = mutableListOf<PredictionRow>()

        rows.forEachIndexed { idx, rawLine ->
            val parsedFields = parseCsvLine(rawLine)
            val text = parsedFields[textCol].trim()
            val truth = parsedFields[intentCol].trim()

            val request = AgentRequest(
                id = UUID.randomUUID(),
                text = text,
                source = InputSource.TEXT,
                timestamp = Instant.now()
            )

            val parsed = rules.parse(request)
            val mapped = mapRuleIntent(request, parsed)

            results.add(PredictionRow(idx, text, truth, mapped, parsed.intent.name))
        }

        return results
    }

    private fun mapRuleIntent(request: AgentRequest, parse: LanguageParse): String {
        if (parse.negated) return "UNKNOWN"
        return resolveIntent(request, parse)
    }

    private fun resolveIntent(request: AgentRequest, parse: LanguageParse): String {
        val graphForm = GraphReadGrammarV18.form(request)
        if (graphForm?.kind == GraphReadKindV18.PROJECT_PENDING) {
            return "PROJECT_STATUS"
        }

        return when (parse.intent) {
            AgentIntent.UPDATE_TASK -> resolveUpdateTask(request.text)
            AgentIntent.WHAT_DID_I_PROMISE -> "PERSON_COMMITMENTS"
            AgentIntent.START_REFLECTION -> "REFLECTION"
            else -> DIRECT_INTENT_MAP[parse.intent] ?: "UNKNOWN"
        }
    }

    private fun resolveUpdateTask(text: String): String =
        if (text.contains("prioritize task:", ignoreCase = true)) "PRIORITIZE_TASK"
        else "RENAME_TASK"

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun toJson(rows: List<PredictionRow>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        rows.forEachIndexed { i, r ->
            val escapedText = r.text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
            sb.append("  {\"index\": ${r.index}, ")
            sb.append("\"text\": \"$escapedText\", ")
            sb.append("\"truth\": \"${r.truth}\", ")
            sb.append("\"predicted\": \"${r.predicted}\", ")
            sb.append("\"raw_intent\": \"${r.rawIntent}\"}")
            if (i < rows.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    private companion object {
        val DIRECT_INTENT_MAP = mapOf(
            AgentIntent.COMPLETE_TASK to "COMPLETE_TASK",
            AgentIntent.DELETE_TASK to "DELETE_TASK",
            AgentIntent.POSTPONE_TASK to "POSTPONE_TASK",
            AgentIntent.CREATE_TASK to "CREATE_TASK",
            AgentIntent.CREATE_REMINDER to "CREATE_REMINDER",
            AgentIntent.CREATE_PROMISE to "CREATE_PROMISE",
            AgentIntent.CHECK_CALENDAR to "CHECK_CALENDAR",
            AgentIntent.CHECK_TASKS to "CHECK_TASKS",
            AgentIntent.CHECK_MEMORY to "CHECK_MEMORY",
            AgentIntent.PLAN_DAY to "PLAN_DAY",
            AgentIntent.WHAT_SHOULD_I_DO to "WHAT_SHOULD_I_DO",
            AgentIntent.CREATE_TIMER to "CREATE_TIMER",
            AgentIntent.CREATE_ALARM to "CREATE_ALARM",
            AgentIntent.CALL_PERSON to "CALL_PERSON",
            AgentIntent.SEND_MESSAGE to "SEND_MESSAGE",
        )
    }
}
