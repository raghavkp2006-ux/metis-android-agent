package dev.metis.agent.data.storage

import org.json.JSONObject
import org.json.JSONTokener

internal object FoundationJson {
    fun validate(value: String) {
        require(value.length <= MAX_JSON_LENGTH)
        checkDepth(value)
        val parser = JSONTokener(value)
        require(parser.nextValue() is JSONObject)
        require(parser.nextClean() == 0.toChar())
    }

    private fun checkDepth(value: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        value.forEach { character ->
            when {
                escaped -> escaped = false
                quoted && character == '\\' -> escaped = true
                character == '"' -> quoted = !quoted
                !quoted && character in "{[" -> {
                    depth++
                    require(depth <= MAX_JSON_DEPTH)
                }
                !quoted && character in "}]" -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
    }

    private const val MAX_JSON_LENGTH = 4_000
    private const val MAX_JSON_DEPTH = 32
}
