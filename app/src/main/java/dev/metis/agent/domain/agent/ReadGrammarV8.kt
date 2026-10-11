package dev.metis.agent.domain.agent

/** Annotation spans and literal retrieval text have separate roles. No model inference. */
object ReadGrammarV8 {
    const val VERSION = "phase15-read-coverage-v8"

    fun body(text: String): Pair<String, Int> {
        val offset = PREFIX.find(text)!!.value.length
        return text.substring(offset) to offset
    }

    fun parse(text: String): LanguageParse? {
        val (body, offset) = body(text)
        val memory = MEMORY.matchEntire(body)
        return if (memory != null) {
            val query = memory.groups[1]!!
            val article = ARTICLE.find(query.value)!!.value.length
            val start = offset + query.range.first + article
            val end = offset + query.range.last + 1
            if (start >= end) null else LanguageParse(AgentIntent.CHECK_MEMORY,
                listOf(ExtractedEntity(EntityKind.MEMORY, text.substring(start, end), start, end, 1.0,
                    query.value.takeIf { it != text.substring(start, end) }?.let(NormalizedValue::Text))))
        } else ReadGrammarV7.parse(body)?.takeIf { it.intent != AgentIntent.CHECK_MEMORY }?.let { parsed ->
            parsed.copy(entities = parsed.entities.map {
                it.copy(startOffset = it.startOffset + offset, endOffset = it.endOffset + offset)
            })
        }
    }

    fun recallPrefix(text: String): Int = RECALL.find(text)?.value?.length ?: 0

    private val PREFIX = Regex("^\\s*(?:please )?(?:(?:can|could) you (?:please )?)?", RegexOption.IGNORE_CASE)
    // Articles are excluded only from the annotation. The original query is never shortened.
    private val ARTICLE = Regex("^(?:my )?(?:(?:the|a|an)(?: |$))?", RegexOption.IGNORE_CASE)
    private val RECALL = Regex("^remind me of ", RegexOption.IGNORE_CASE)
    private val MEMORY = Regex(
        "(?:what do you know about|search (?:my )?memory for|" +
            "what did i (?:write(?: down)?|note|save) about|" +
            "what have i (?:written|noted|saved|recorded) about|" +
            "(?:show|find|retrieve|look up) (?:me )?(?:my )?(?:saved )?(?:notes|memories|records|details) " +
            "(?:about|on|regarding)|(?:do i have|do you have|are there) (?:any )?(?:saved )?" +
            "(?:notes|memories) (?:about|on|regarding)|remind me of) ((?:my )?.{1,200}?)[?!.]?\\s*",
        RegexOption.IGNORE_CASE)
}
