package dev.metis.agent.domain.agent

import dev.metis.agent.domain.storage.MemorySearchQuery

/** New complete forms take precedence over generic topic recall; no repository access. */
object ReadGrammarV12 {
    @Suppress("ReturnCount") // A reserved wrapper must never fall back to a broader memory subject.
    fun parse(text: String): LanguageParse? {
        ReadPatternsV12.forms.forEach { form ->
            val match = form.pattern.matchEntire(text) ?: return@forEach
            parse(text, form, match)?.let { return it }
        }
        if (ReadPatternsV12.reserved.containsMatchIn(text)) return null
        return ReadGrammarV10.parse(text)
    }

    @Suppress("ReturnCount") // Explicit failures precede any span construction.
    private fun parse(text: String, form: ReadFormV12, match: MatchResult): LanguageParse? {
        if (form.mode == "none") return LanguageParse(form.intent)
        val reference = if (form.pattern.pattern.contains("(?<reference>")) match.groups["reference"]!!
            else match.groups["query"]!!
        val query = if (form.mode in setOf("person", "calendar_person")) reference else match.groups["query"]!!
        val subject = if (form.mode == "topic") reference.value.replace(ReadPatternsV10.ARTICLE, "")
            else reference.value
        if (!validQuery(form.mode, query.value, subject, match) ||
            query.value.length > MemorySearchQuery.MAX_SEARCH_LENGTH) return null
        if (form.intent == AgentIntent.CHECK_CALENDAR) return LanguageParse(form.intent)
        val annotation = if (form.mode == "coordinated") query else reference
        val article = if (form.mode in setOf("person", "coordinated", "speech", "meeting")) 0
            else ReadPatternsV10.ARTICLE.find(annotation.value)!!.value.length
        val start = annotation.range.first + article
        val end = annotation.range.last + 1
        if (start >= end || end - start > if (form.mode == "person") MAX_PERSON else MAX_ANNOTATION) return null
        val raw = text.substring(start, end)
        val entity = ExtractedEntity(if (form.mode == "person") EntityKind.PERSON else EntityKind.MEMORY,
            raw, start, end, 1.0, query.value.takeIf { it != raw }?.let(NormalizedValue::Text))
        return LanguageParse(form.intent, listOf(entity))
    }

    private fun validQuery(mode: String, query: String, reference: String, match: MatchResult) = when (mode) {
        "coordinated" -> ReadSafetyV10.validSubject(reference, reference, "person") &&
            match.groups["subject"]!!.value.let { ReadSafetyV10.validSubject(it, it, "topic") }
        "speech", "meeting", "calendar_person" -> ReadSafetyV10.validSubject(reference, reference, "person")
        else -> ReadSafetyV10.validSubject(query, reference, if (mode == "person") "person" else "topic")
    }

    private const val MAX_PERSON = 100
    private const val MAX_ANNOTATION = 200
}
