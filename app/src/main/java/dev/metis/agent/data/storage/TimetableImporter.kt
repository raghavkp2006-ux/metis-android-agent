@file:Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "ComplexCondition",
    "MagicNumber",
    "TooGenericExceptionCaught",
    "ReturnCount",        // parser functions use multiple guard-clause returns
    "ThrowsCount",        // parseString throws for each validation case
    "UseRequire",         // throws carry context messages not expressible with require()
    "MaxLineLength",      // validation error messages and regexes
)

package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RoutineRule
import dev.metis.agent.domain.storage.RuleHardness
import dev.metis.agent.domain.storage.ScheduleBlock
import dev.metis.agent.domain.storage.ScheduleKind
import java.util.Locale
import java.util.UUID

data class TimetableValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val blocksCountByDay: Map<Int, Int>,
    val totalBlocksCount: Int,
    val totalRulesCount: Int,
    val blocks: List<ScheduleBlock>,
    val rules: List<RoutineRule>,
)

object TimetableImporter {
    fun parseAndValidate(jsonString: String): TimetableValidationResult {
        val errors = mutableListOf<String>()
        val parsedBlocks = mutableListOf<ScheduleBlock>()
        val parsedRules = mutableListOf<RoutineRule>()

        val root = try {
            val parsed = SimpleJsonParser(jsonString).parseValue()
            if (parsed !is Map<*, *>) {
                return TimetableValidationResult(
                    valid = false,
                    errors = listOf("Malformed JSON: root must be an object."),
                    blocksCountByDay = emptyMap(),
                    totalBlocksCount = 0,
                    totalRulesCount = 0,
                    blocks = emptyList(),
                    rules = emptyList(),
                )
            }
            @Suppress("UNCHECKED_CAST")
            parsed as Map<String, Any?>
        } catch (e: Exception) {
            return TimetableValidationResult(
                valid = false,
                errors = listOf("Malformed JSON: ${e.message ?: "Invalid JSON syntax"}"),
                blocksCountByDay = emptyMap(),
                totalBlocksCount = 0,
                totalRulesCount = 0,
                blocks = emptyList(),
                rules = emptyList(),
            )
        }

        val blocksList = (root["blocks"] as? List<*>) ?: emptyList<Any?>()
        for (i in blocksList.indices) {
            val item = blocksList[i] as? Map<*, *>
            if (item == null) {
                errors.add("Block #$i is not a valid JSON object.")
                continue
            }
            val lineNum = i + 1
            val id = (item["id"] as? String)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

            val rawWeekday = item["weekday"]
            val weekday = parseWeekday(rawWeekday)
            if (weekday == null || weekday !in 1..7) {
                errors.add("Block #$lineNum: unknown or invalid weekday '$rawWeekday'. Must be 1-7 or day name.")
            }

            val startMin = parseTimeToMinutes(item["start"] ?: item["startMin"])
            val endMin = parseTimeToMinutes(item["end"] ?: item["endMin"])

            if (startMin == null || startMin !in 0 until 1440) {
                errors.add("Block #$lineNum: invalid start time. Must be HH:mm or minute 0-1439.")
            }
            if (endMin == null || endMin !in 1..1440) {
                errors.add("Block #$lineNum: invalid end time. Must be HH:mm or minute 1-1440.")
            }
            if (startMin != null && endMin != null && endMin <= startMin) {
                errors.add("Block #$lineNum: end time (${formatMinutes(endMin)}) must be strictly after start time (${formatMinutes(startMin)}).")
            }

            val title = (item["title"] as? String)?.trim() ?: ""
            if (title.isBlank()) {
                errors.add("Block #$lineNum: title must not be blank.")
            }

            val rawKind = ((item["kind"] as? String) ?: "CLASS").trim().uppercase(Locale.ROOT)
            val kind = try {
                ScheduleKind.valueOf(rawKind)
            } catch (_: IllegalArgumentException) {
                errors.add("Block #$lineNum: unknown kind '$rawKind'. Allowed: CLASS, LAB, ROUTINE, FREE.")
                ScheduleKind.CLASS
            }

            val fixed = when (val f = item["fixed"]) {
                is Boolean -> f
                is String -> f.lowercase(Locale.ROOT) !in setOf("false", "no", "0")
                is Number -> f.toInt() != 0
                else -> true
            }
            val tag = (item["tag"] as? String)?.takeIf { it.isNotBlank() }

            if (weekday != null && startMin != null && endMin != null && endMin > startMin && title.isNotBlank()) {
                parsedBlocks.add(
                    ScheduleBlock(
                        id = id,
                        weekday = weekday,
                        startMin = startMin,
                        endMin = endMin,
                        title = title,
                        kind = kind,
                        fixed = fixed,
                        tag = tag,
                    )
                )
            }
        }

        val rulesList = (root["rules"] as? List<*>) ?: emptyList<Any?>()
        for (i in rulesList.indices) {
            val item = rulesList[i] as? Map<*, *>
            if (item == null) {
                errors.add("Rule #$i is not a valid JSON object.")
                continue
            }
            val lineNum = i + 1
            val id = (item["id"] as? String)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val key = (item["key"] as? String)?.trim() ?: ""
            if (key.isBlank()) {
                errors.add("Rule #$lineNum: key must not be blank.")
            }

            val startMin = parseTimeToMinutes(item["start"] ?: item["window_start"] ?: item["windowStartMin"])
            val endMin = parseTimeToMinutes(item["end"] ?: item["window_end"] ?: item["endMin"] ?: item["windowEndMin"])
            if (startMin == null || startMin !in 0 until 1440) {
                errors.add("Rule #$lineNum: invalid window start time.")
            }
            if (endMin == null || endMin !in 1..1440) {
                errors.add("Rule #$lineNum: invalid window end time.")
            }
            if (startMin != null && endMin != null && endMin <= startMin) {
                errors.add("Rule #$lineNum: window end (${formatMinutes(endMin)}) must be after window start (${formatMinutes(startMin)}).")
            }

            val weekdaysMask = parseWeekdaysMask(item["weekdays"], item["weekdaysMask"])
            if (weekdaysMask == null || weekdaysMask !in 1..127) {
                errors.add("Rule #$lineNum: invalid weekdays mask. Must cover at least one weekday 1-7.")
            }

            val rawHardness = ((item["hardness"] as? String) ?: "SOFT").trim().uppercase(Locale.ROOT)
            val hardness = try {
                RuleHardness.valueOf(rawHardness)
            } catch (_: IllegalArgumentException) {
                errors.add("Rule #$lineNum: unknown hardness '$rawHardness'. Allowed: SOFT, FIRM.")
                RuleHardness.SOFT
            }

            val note = (item["note"] as? String)?.takeIf { it.isNotBlank() }

            if (key.isNotBlank() && startMin != null && endMin != null && endMin > startMin && weekdaysMask != null && weekdaysMask in 1..127) {
                parsedRules.add(
                    RoutineRule(
                        id = id,
                        key = key,
                        windowStartMin = startMin,
                        windowEndMin = endMin,
                        weekdaysMask = weekdaysMask,
                        hardness = hardness,
                        note = note,
                    )
                )
            }
        }

        // Check for overlaps among fixed blocks
        val fixedBlocksByDay = parsedBlocks.filter { it.fixed }.groupBy { it.weekday }
        for ((day, blocks) in fixedBlocksByDay) {
            val sorted = blocks.sortedBy { it.startMin }
            for (i in 0 until sorted.size) {
                for (j in i + 1 until sorted.size) {
                    val b1 = sorted[i]
                    val b2 = sorted[j]
                    if (b1.overlaps(b2)) {
                        errors.add(
                            "Fixed blocks overlap on ${weekdayName(day)}: " +
                                "'${b1.title}' (${formatMinutes(b1.startMin)}-${formatMinutes(b1.endMin)}) and " +
                                "'${b2.title}' (${formatMinutes(b2.startMin)}-${formatMinutes(b2.endMin)})."
                        )
                    }
                }
            }
        }

        val countsByDay = (1..7).associateWith { day -> parsedBlocks.count { it.weekday == day } }

        return TimetableValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            blocksCountByDay = countsByDay,
            totalBlocksCount = parsedBlocks.size,
            totalRulesCount = parsedRules.size,
            blocks = if (errors.isEmpty()) parsedBlocks else emptyList(),
            rules = if (errors.isEmpty()) parsedRules else emptyList(),
        )
    }

    fun parseTimeToMinutes(raw: Any?): Int? = when (raw) {
        null -> null
        is Number -> {
            val v = raw.toInt()
            if (v in 0..1440) v else null
        }
        is String -> {
            val text = raw.trim()
            if (text.isEmpty()) null
            else if (text.matches(Regex("""^\d+$"""))) {
                val v = text.toIntOrNull()
                if (v != null && v in 0..1440) v else null
            } else {
                val match = Regex("""^(\d{1,2}):(\d{2})(?:\s*(am|pm))?$""", RegexOption.IGNORE_CASE).matchEntire(text)
                if (match != null) {
                    var h = match.groupValues[1].toInt()
                    val m = match.groupValues[2].toInt()
                    val ampm = match.groupValues[3].lowercase(Locale.ROOT)
                    if (ampm == "pm" && h < 12) h += 12
                    if (ampm == "am" && h == 12) h = 0
                    if (h in 0..24 && m in 0..59) {
                        val total = h * 60 + m
                        if (total in 0..1440) total else null
                    } else null
                } else null
            }
        }
        else -> null
    }

    fun parseWeekday(raw: Any?): Int? = when (raw) {
        null -> null
        is Number -> {
            val v = raw.toInt()
            if (v in 1..7) v else null
        }
        is String -> {
            val clean = raw.trim().lowercase(Locale.ROOT)
            when (clean) {
                "1", "mon", "monday", "somwar", "somvaar" -> 1
                "2", "tue", "tues", "tuesday", "mangalwar", "mangalvaar" -> 2
                "3", "wed", "wednesday", "budhwar", "budhvaar" -> 3
                "4", "thu", "thur", "thurs", "thursday", "guruwar", "guruvaar", "veervaar" -> 4
                "5", "fri", "friday", "shukrawar", "shukravaar" -> 5
                "6", "sat", "saturday", "shaniwar", "shanivaar" -> 6
                "7", "sun", "sunday", "ravivar", "ravivaar", "itwar" -> 7
                else -> null
            }
        }
        else -> null
    }

    private fun parseWeekdaysMask(weekdaysObj: Any?, maskObj: Any?): Int? {
        if (maskObj is Number) {
            val m = maskObj.toInt()
            if (m in 1..127) return m
        }
        if (weekdaysObj is List<*>) {
            var mask = 0
            for (item in weekdaysObj) {
                val d = parseWeekday(item) ?: return null
                mask = mask or (1 shl (d - 1))
            }
            return if (mask in 1..127) mask else null
        }
        if (weekdaysObj is String) {
            val lower = weekdaysObj.trim().lowercase(Locale.ROOT)
            if (lower in setOf("all", "daily", "everyday")) return 127
            if (lower in setOf("weekdays", "mon-fri", "mon to fri")) return 31 // 1+2+4+8+16
            if (lower in setOf("weekends", "sat-sun", "sat to sun")) return 96 // 32+64
            val parts = weekdaysObj.split(",", ";", " ")
            var mask = 0
            for (p in parts) {
                val trimmed = p.trim()
                if (trimmed.isEmpty()) continue
                val d = parseWeekday(trimmed) ?: return null
                mask = mask or (1 shl (d - 1))
            }
            return if (mask in 1..127) mask else null
        }
        return null
    }

    fun formatMinutes(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return String.format(Locale.ROOT, "%02d:%02d", h, m)
    }

    fun weekdayName(weekday: Int): String = when (weekday) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Day $weekday"
    }

    private class SimpleJsonParser(private val src: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected EOF")
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                in '0'..'9', '-' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected character '$c' at position $pos")
            }
        }

        fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = mutableMapOf<String, Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (pos < src.length) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == '}') {
                    pos++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at position $pos")
                }
            }
            return map
        }

        fun parseArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (pos < src.length) {
                val value = parseValue()
                list.add(value)
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == ']') {
                    pos++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' at position $pos")
                }
            }
            return list
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    if (pos >= src.length) throw IllegalArgumentException("Unterminated escape at position $pos")
                    val escaped = src[pos++]
                    when (escaped) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape at $pos")
                            val hex = src.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(escaped)
                    }
                } else {
                    sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (pos < src.length && src[pos] == '-') pos++
            while (pos < src.length && src[pos].isDigit()) pos++
            val isDouble = if (pos < src.length && src[pos] == '.') {
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
                true
            } else false
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            val text = src.substring(start, pos)
            return if (isDouble) text.toDouble() else text.toIntOrNull() ?: text.toLong()
        }

        private fun parseBoolean(): Boolean {
            if (src.startsWith("true", pos)) { pos += 4; return true }
            if (src.startsWith("false", pos)) { pos += 5; return false }
            throw IllegalArgumentException("Invalid boolean at position $pos")
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", pos)) { pos += 4; return null }
            throw IllegalArgumentException("Invalid null at position $pos")
        }

        private fun expect(c: Char) {
            skipWhitespace()
            if (pos >= src.length || src[pos] != c) {
                throw IllegalArgumentException("Expected '$c' at position $pos")
            }
            pos++
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }
}
