@file:Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "ComplexCondition", "LongParameterList", "ReturnCount", "MaxLineLength")

package dev.metis.agent.ml

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExtractedEntity
import dev.metis.agent.domain.agent.IntentPrediction
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.ResolvedTime
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Pure Kotlin inference engine for the 21-class NLU model.
 * Zero external ML libraries, zero network, zero Android-only runtime dependencies.
 */
class NluIntentClassifier(val model: NluModel) {

    fun predict(text: String): NluResult {
        val lowered = text.lowercase(Locale.ROOT)
        val numClasses = model.classes.size

        // 1. Word feature extraction
        val wordCounts = LinkedHashMap<String, Int>()
        val wordMatcher = WORD_PATTERN.matcher(lowered)
        val tokens = ArrayList<String>()
        while (wordMatcher.find()) {
            tokens.add(wordMatcher.group())
        }
        for (i in 0 until tokens.size) {
            val t = tokens[i]
            wordCounts[t] = (wordCounts[t] ?: 0) + 1
            if (i < tokens.size - 1) {
                val bg = "$t ${tokens[i + 1]}"
                wordCounts[bg] = (wordCounts[bg] ?: 0) + 1
            }
        }

        var wordSumSq = 0.0
        val wordIndices = ArrayList<Int>()
        val wordValues = ArrayList<Double>()
        for ((term, count) in wordCounts) {
            val idx = model.wordVocab[term] ?: continue
            val idf = model.wordIdf[idx]
            val value = count.toDouble() * idf
            wordIndices.add(idx)
            wordValues.add(value)
            wordSumSq += value * value
        }
        val wordNorm = sqrt(wordSumSq)
        if (wordNorm > 0.0) {
            for (i in 0 until wordValues.size) {
                wordValues[i] = wordValues[i] / wordNorm
            }
        }

        // 2. Char feature extraction (sliding window 2..5)
        val charCounts = LinkedHashMap<String, Int>()
        val len = lowered.length
        for (n in 2..5) {
            for (i in 0..len - n) {
                val sub = lowered.substring(i, i + n)
                charCounts[sub] = (charCounts[sub] ?: 0) + 1
            }
        }

        var charSumSq = 0.0
        val charIndices = ArrayList<Int>()
        val charValues = ArrayList<Double>()
        for ((term, count) in charCounts) {
            val idx = model.charVocab[term] ?: continue
            val idf = model.charIdf[idx]
            val value = count.toDouble() * idf
            charIndices.add(idx)
            charValues.add(value)
            charSumSq += value * value
        }
        val charNorm = sqrt(charSumSq)
        if (charNorm > 0.0) {
            for (i in 0 until charValues.size) {
                charValues[i] = charValues[i] / charNorm
            }
        }

        // 3. Linear scores: intercept + dot product
        val scores = DoubleArray(numClasses) { model.intercepts[it] }
        for (i in 0 until wordIndices.size) {
            val featIdx = wordIndices[i]
            val featVal = wordValues[i]
            val offset = featIdx * numClasses
            for (c in 0 until numClasses) {
                scores[c] += featVal * model.wordWeights[offset + c]
            }
        }
        for (i in 0 until charIndices.size) {
            val featIdx = charIndices[i]
            val featVal = charValues[i]
            val offset = featIdx * numClasses
            for (c in 0 until numClasses) {
                scores[c] += featVal * model.charWeights[offset + c]
            }
        }

        // 4. Softmax
        var maxScore = scores[0]
        for (c in 1 until numClasses) {
            if (scores[c] > maxScore) maxScore = scores[c]
        }
        var sumExp = 0.0
        val expScores = DoubleArray(numClasses)
        for (c in 0 until numClasses) {
            val e = exp(scores[c] - maxScore)
            expScores[c] = e
            sumExp += e
        }

        var bestIdx = 0
        var bestProb = 0.0
        val probsMap = LinkedHashMap<String, Float>()
        for (c in 0 until numClasses) {
            val p = (expScores[c] / sumExp).toFloat()
            val label = model.classes[c]
            probsMap[label] = p
            if (p > bestProb) {
                bestProb = p.toDouble()
                bestIdx = c
            }
        }

        val bestLabel = model.classes[bestIdx]
        val threshold = when {
            bestLabel in UNCALIBRATED_CONSERVATIVE_INTENTS -> model.uncalibratedThreshold
            bestLabel in READ_INTENTS -> model.readThreshold
            else -> model.writeThreshold
        }
        val accepted = bestProb >= threshold && bestLabel != "UNKNOWN"
        val mappedIntent = if (accepted) mapModelLabel(bestLabel) else AgentIntent.UNKNOWN

        return NluResult(
            rawLabel = bestLabel,
            confidence = bestProb.toFloat(),
            accepted = accepted,
            agentIntent = mappedIntent,
            probabilities = probsMap,
            isUnderstoodByLm = accepted,
        )
    }

    companion object {
        const val ML_FALLBACK_ENABLED = true
        private val WORD_PATTERN = Pattern.compile("(?u)\\b\\w\\w+\\b")

        /**
         * Untuned conservative intents without human ground-truth calibration rows in data pool.
         * Enforces a stricter threshold of 0.50 (vs 0.35 for calibrated writes and 0.24 for reads).
         */
        val UNCALIBRATED_CONSERVATIVE_INTENTS = setOf(
            "CREATE_ALARM", "CREATE_TIMER", "CALL_PERSON", "SEND_MESSAGE", "CHECK_MEMORY"
        )

        val READ_INTENTS = setOf(
            "CHECK_CALENDAR", "CHECK_MEMORY", "CHECK_TASKS", "PERSON_COMMITMENTS",
            "PLAN_DAY", "PROJECT_STATUS", "WHAT_SHOULD_I_DO"
        )

        val WRITE_INTENTS = setOf(
            "COMPLETE_TASK", "DELETE_TASK", "POSTPONE_TASK", "PRIORITIZE_TASK", "RENAME_TASK",
            "CREATE_TASK", "CREATE_REMINDER", "CREATE_PROMISE", "REFLECTION",
            "CREATE_ALARM", "CREATE_TIMER", "CALL_PERSON", "SEND_MESSAGE"
        )

        val FORBIDDEN_TIER = setOf(
            "COMPLETE_TASK", "DELETE_TASK", "POSTPONE_TASK", "PRIORITIZE_TASK", "RENAME_TASK"
        )

        fun mapModelLabel(modelLabel: String): AgentIntent = when (modelLabel) {
            "RENAME_TASK", "PRIORITIZE_TASK" -> AgentIntent.UPDATE_TASK
            "PERSON_COMMITMENTS" -> AgentIntent.WHAT_DID_I_PROMISE
            "PROJECT_STATUS" -> AgentIntent.CHECK_TASKS
            "CALL_PERSON" -> AgentIntent.CALL_PERSON
            "SEND_MESSAGE" -> AgentIntent.SEND_MESSAGE
            "CREATE_TIMER" -> AgentIntent.CREATE_TIMER
            "CREATE_ALARM" -> AgentIntent.CREATE_ALARM
            "CHECK_MEMORY" -> AgentIntent.CHECK_MEMORY
            "CREATE_TASK" -> AgentIntent.CREATE_TASK
            "CREATE_REMINDER" -> AgentIntent.CREATE_REMINDER
            "CREATE_PROMISE" -> AgentIntent.CREATE_PROMISE
            "COMPLETE_TASK" -> AgentIntent.COMPLETE_TASK
            "DELETE_TASK" -> AgentIntent.DELETE_TASK
            "POSTPONE_TASK" -> AgentIntent.POSTPONE_TASK
            "REFLECTION" -> AgentIntent.START_REFLECTION
            "CHECK_CALENDAR" -> AgentIntent.CHECK_CALENDAR
            "CHECK_TASKS" -> AgentIntent.CHECK_TASKS
            "PLAN_DAY" -> AgentIntent.PLAN_DAY
            "WHAT_SHOULD_I_DO" -> AgentIntent.WHAT_SHOULD_I_DO
            else -> AgentIntent.UNKNOWN
        }

        private var cachedInstance: NluIntentClassifier? = null
        private var streamProviderOverride: (() -> InputStream?)? = null

        @Synchronized
        internal fun resetCacheForTesting(
            instance: NluIntentClassifier? = null,
            streamProvider: (() -> InputStream?)? = null
        ) {
            cachedInstance = instance
            streamProviderOverride = streamProvider
        }

        fun fromInputStream(stream: InputStream): NluIntentClassifier {
            val model = NluModelReader.read(BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)))
            return NluIntentClassifier(model)
        }

        @Synchronized
        fun getOrCreate(streamProvider: () -> InputStream?): NluIntentClassifier? {
            if (cachedInstance != null) return cachedInstance
            return try {
                val provider = streamProviderOverride ?: streamProvider
                val stream = provider() ?: return null
                val classifier = fromInputStream(stream)
                cachedInstance = classifier
                classifier
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class NluResult(
    val rawLabel: String,
    val confidence: Float,
    val accepted: Boolean,
    val agentIntent: AgentIntent,
    val probabilities: Map<String, Float>,
    val isUnderstoodByLm: Boolean,
)

class NluModel(
    val classes: List<String>,
    val intercepts: DoubleArray,
    val wordVocab: Map<String, Int>,
    val wordIdf: DoubleArray,
    val wordWeights: DoubleArray, // Flattened: numWordFeatures * numClasses
    val charVocab: Map<String, Int>,
    val charIdf: DoubleArray,
    val charWeights: DoubleArray, // Flattened: numCharFeatures * numClasses
    val readThreshold: Float = 0.24f,
    val writeThreshold: Float = 0.35f,
    /**
     * Untuned conservative threshold (0.50) for actions without human rows
     * (CREATE_ALARM, CREATE_TIMER, CALL_PERSON, SEND_MESSAGE, CHECK_MEMORY).
     */
    val uncalibratedThreshold: Float = 0.50f,
)

/**
 * Fast dependency-free JSON reader for the NLU model asset.
 */
internal object NluModelReader {

    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    fun read(reader: Reader): NluModel {
        val json = reader.readText()
        var pos = 0
        val length = json.length

        fun skipWhitespace() {
            while (pos < length && json[pos].isWhitespace()) pos++
        }

        fun expect(c: Char) {
            skipWhitespace()
            if (pos >= length || json[pos] != c) {
                error("Expected '$c' at $pos, found '${if (pos < length) json[pos] else "EOF"}'")
            }
            pos++
        }

        fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < length) {
                val c = json[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\' && pos < length) {
                    val esc = json[pos++]
                    when (esc) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = json.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            error("Unterminated string")
        }

        fun readNumber(): Double {
            skipWhitespace()
            val start = pos
            if (pos < length && (json[pos] == '-' || json[pos] == '+')) pos++
            while (pos < length && (json[pos].isDigit() || json[pos] == '.' ||
                    json[pos] == 'e' || json[pos] == 'E' || json[pos] == '-' || json[pos] == '+')) {
                pos++
            }
            return json.substring(start, pos).toDouble()
        }

        fun skipValue() {
            skipWhitespace()
            when (json[pos]) {
                '{' -> {
                    pos++
                    var depth = 1
                    while (pos < length && depth > 0) {
                        if (json[pos] == '{') depth++
                        else if (json[pos] == '}') depth--
                        else if (json[pos] == '"') { readString(); continue }
                        pos++
                    }
                }
                '[' -> {
                    pos++
                    var depth = 1
                    while (pos < length && depth > 0) {
                        if (json[pos] == '[') depth++
                        else if (json[pos] == ']') depth--
                        else if (json[pos] == '"') { readString(); continue }
                        pos++
                    }
                }
                '"' -> readString()
                else -> {
                    while (pos < length && json[pos] != ',' && json[pos] != '}' && json[pos] != ']' &&
                        !json[pos].isWhitespace()) pos++
                }
            }
        }

        var classes: List<String> = emptyList()
        var intercepts: DoubleArray = DoubleArray(0)
        var wordVocab: Map<String, Int> = emptyMap()
        var wordIdf: DoubleArray = DoubleArray(0)
        var wordWeights: DoubleArray = DoubleArray(0)
        var charVocab: Map<String, Int> = emptyMap()
        var charIdf: DoubleArray = DoubleArray(0)
        var charWeights: DoubleArray = DoubleArray(0)
        var readThreshold = 0.24f
        var writeThreshold = 0.35f
        var uncalibratedThreshold = 0.50f

        expect('{')
        while (true) {
            skipWhitespace()
            if (pos < length && json[pos] == '}') { pos++; break }
            val key = readString()
            expect(':')
            when (key) {
                "classes" -> {
                    expect('[')
                    val list = ArrayList<String>()
                    while (true) {
                        skipWhitespace()
                        if (json[pos] == ']') { pos++; break }
                        list.add(readString())
                        skipWhitespace()
                        if (json[pos] == ',') pos++
                    }
                    classes = list
                }
                "intercepts" -> {
                    expect('[')
                    val list = ArrayList<Double>()
                    while (true) {
                        skipWhitespace()
                        if (json[pos] == ']') { pos++; break }
                        list.add(readNumber())
                        skipWhitespace()
                        if (json[pos] == ',') pos++
                    }
                    intercepts = list.toDoubleArray()
                }
                "settings" -> {
                    expect('{')
                    while (true) {
                        skipWhitespace()
                        if (json[pos] == '}') { pos++; break }
                        val sKey = readString()
                        expect(':')
                        when (sKey) {
                            "read_threshold" -> readThreshold = readNumber().toFloat()
                            "write_threshold" -> writeThreshold = readNumber().toFloat()
                            "uncalibrated_threshold" -> uncalibratedThreshold = readNumber().toFloat()
                            else -> skipValue()
                        }
                        skipWhitespace()
                        if (json[pos] == ',') pos++
                    }
                }
                "features" -> {
                    expect('{')
                    while (true) {
                        skipWhitespace()
                        if (json[pos] == '}') { pos++; break }
                        val fType = readString()
                        expect(':')
                        expect('{')
                        var vocab = LinkedHashMap<String, Int>()
                        var idf = ArrayList<Double>()
                        val numClasses = classes.size
                        var weights = DoubleArray(0)
                        while (true) {
                            skipWhitespace()
                            if (json[pos] == '}') { pos++; break }
                            val fKey = readString()
                            expect(':')
                            when (fKey) {
                                "vocabulary" -> {
                                    expect('{')
                                    while (true) {
                                        skipWhitespace()
                                        if (json[pos] == '}') { pos++; break }
                                        val vTerm = readString()
                                        expect(':')
                                        val vIdx = readNumber().toInt()
                                        vocab[vTerm] = vIdx
                                        skipWhitespace()
                                        if (json[pos] == ',') pos++
                                    }
                                }
                                "idf" -> {
                                    expect('[')
                                    while (true) {
                                        skipWhitespace()
                                        if (json[pos] == ']') { pos++; break }
                                        idf.add(readNumber())
                                        skipWhitespace()
                                        if (json[pos] == ',') pos++
                                    }
                                }
                                "weights" -> {
                                    expect('[')
                                    val totalFeats = vocab.size
                                    weights = DoubleArray(totalFeats * numClasses)
                                    var featIdx = 0
                                    while (true) {
                                        skipWhitespace()
                                        if (json[pos] == ']') { pos++; break }
                                        expect('[')
                                        var classIdx = 0
                                        while (true) {
                                            skipWhitespace()
                                            if (json[pos] == ']') { pos++; break }
                                            weights[featIdx * numClasses + classIdx] = readNumber()
                                            classIdx++
                                            skipWhitespace()
                                            if (json[pos] == ',') pos++
                                        }
                                        featIdx++
                                        skipWhitespace()
                                        if (json[pos] == ',') pos++
                                    }
                                }
                                else -> skipValue()
                            }
                            skipWhitespace()
                            if (json[pos] == ',') pos++
                        }
                        if (fType == "word") {
                            wordVocab = vocab
                            wordIdf = idf.toDoubleArray()
                            wordWeights = weights
                        } else if (fType == "char") {
                            charVocab = vocab
                            charIdf = idf.toDoubleArray()
                            charWeights = weights
                        }
                        skipWhitespace()
                        if (json[pos] == ',') pos++
                    }
                }
                else -> skipValue()
            }
            skipWhitespace()
            if (json[pos] == ',') pos++
        }

        return NluModel(
            classes = classes,
            intercepts = intercepts,
            wordVocab = wordVocab,
            wordIdf = wordIdf,
            wordWeights = wordWeights,
            charVocab = charVocab,
            charIdf = charIdf,
            charWeights = charWeights,
            readThreshold = readThreshold,
            writeThreshold = writeThreshold,
            uncalibratedThreshold = uncalibratedThreshold,
        )
    }
}

