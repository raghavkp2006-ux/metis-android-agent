package dev.metis.agent.domain.agent

/** The complete literal query is distinct from its annotated person/topic span. */
object ReadGrammarV14 {
    fun parse(text: String): LanguageParse? = ReadPatternsV14.forms.firstNotNullOfOrNull { form ->
        form.pattern.matchEntire(text)?.let { match -> parse(text, form.mode, match) }
    } ?: ReadGrammarV12.parse(text)

    @Suppress("ReturnCount") // All bounds and component validation precede entity construction.
    private fun parse(text: String, mode: String, match: MatchResult): LanguageParse? {
        val query = requireNotNull(match.groups["query"])
        if (query.value.length > MAX_QUERY) return null
        val annotation = if (mode == "speech") requireNotNull(match.groups["person"]) else query
        val article = if (mode in setOf("speech", "person_topic")) 0
            else requireNotNull(ReadPatternsV10.ARTICLE.find(annotation.value)).value.length
        val start = annotation.range.first + article
        val end = annotation.range.last + 1
        if (start >= end || end - start > MAX_ANNOTATION) return null
        val raw = text.substring(start, end)
        if (mode == "topic") {
            if (!ReadSafetyV10.validSubject(query.value, raw, "topic")) return null
        } else {
            val person = requireNotNull(match.groups["person"]).value
            if (!ReadSafetyV10.validSubject(person, person, "person")) return null
            if (mode != "speech") {
                val topic = requireNotNull(match.groups["subject"]).value
                if (!ReadSafetyV10.validSubject(topic, topic.replace(ReadPatternsV10.ARTICLE, ""), "topic")) return null
            }
        }
        return LanguageParse(AgentIntent.CHECK_MEMORY, listOf(ExtractedEntity(EntityKind.MEMORY,
            raw, start, end, 1.0, query.value.takeIf { it != raw }?.let(NormalizedValue::Text))))
    }

    private const val MAX_QUERY = 256
    private const val MAX_ANNOTATION = 200
}
