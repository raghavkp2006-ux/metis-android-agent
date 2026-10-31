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

        val graphForm = GraphReadGrammarV18.form(request)
        if (graphForm != null && graphForm.kind == GraphReadKindV18.PROJECT_PENDING) {
            return "PROJECT_STATUS"
        }

        return when (parse.intent) {
            AgentIntent.COMPLETE_TASK -> "COMPLETE_TASK"
            AgentIntent.DELETE_TASK -> "DELETE_TASK"
            AgentIntent.POSTPONE_TASK -> "POSTPONE_TASK"
            AgentIntent.UPDATE_TASK -> {
                if (request.text.contains("prioritize task:", ignoreCase = true)) "PRIORITIZE_TASK"
                else "RENAME_TASK"
            }
            AgentIntent.CREATE_TASK -> "CREATE_TASK"
            AgentIntent.CREATE_REMINDER -> "CREATE_REMINDER"
            AgentIntent.CREATE_PROMISE -> "CREATE_PROMISE"
            AgentIntent.START_REFLECTION -> "REFLECTION"
            AgentIntent.CHECK_CALENDAR -> "CHECK_CALENDAR"
            AgentIntent.CHECK_TASKS -> "CHECK_TASKS"
            AgentIntent.CHECK_MEMORY -> "CHECK_MEMORY"
            AgentIntent.WHAT_DID_I_PROMISE -> "PERSON_COMMITMENTS"
            AgentIntent.PLAN_DAY -> "PLAN_DAY"
            AgentIntent.WHAT_SHOULD_I_DO -> "WHAT_SHOULD_I_DO"
            AgentIntent.CREATE_TIMER -> "CREATE_TIMER"
            AgentIntent.CREATE_ALARM -> "CREATE_ALARM"
            AgentIntent.CALL_PERSON -> "CALL_PERSON"
            AgentIntent.SEND_MESSAGE -> "SEND_MESSAGE"
            else -> "UNKNOWN"
        }
    }

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
            val escapedText = r.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            sb.append("  {\"index\": ${r.index}, \"text\": \"$escapedText\", \"truth\": \"${r.truth}\", \"predicted\": \"${r.predicted}\", \"raw_intent\": \"${r.rawIntent}\"}")
            if (i < rows.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }
}
