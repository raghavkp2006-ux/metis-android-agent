package dev.metis.agent.domain.agent

/** Deterministic full-request grammar. It neither resolves contacts nor constructs context. */
object ReadGrammarV10 {
    const val VERSION = "phase15-read-coverage-v10"

    fun parse(text: String): LanguageParse? = ReadPatternsV10.forms.firstNotNullOfOrNull { form ->
        form.pattern.matchEntire(text)?.let { match -> parse(text, form, match) }
    }

    @Suppress("ReturnCount") // Explicit exits for entity-free forms and empty article-only captures.
    private fun parse(text: String, form: ReadFormV10, match: MatchResult): LanguageParse? {
        if (form.mode == "none") return LanguageParse(form.intent)
        val query = match.groups[if (form.mode == "person") "reference" else "query"]!!
        val reference = if (form.pattern.pattern.contains("(?<reference>")) match.groups["reference"]!! else query
        val literalReference = form.mode in setOf("person", "street") ||
            (form.mode == "attribution" && reference.value.startsWith("my ", true))
        val article = if (literalReference) 0 else ReadPatternsV10.ARTICLE.find(reference.value)!!.value.length
        val start = reference.range.first + article
        val end = reference.range.last + 1
        if (start >= end) return null
        val raw = text.substring(start, end)
        val kind = if (form.mode == "person") EntityKind.PERSON else EntityKind.MEMORY
        val entity = ExtractedEntity(kind, raw, start, end, 1.0,
            query.value.takeIf { it != raw }?.let(NormalizedValue::Text))
        return LanguageParse(form.intent, listOf(entity)).takeIf {
            ReadSafetyV10.validSubject(query.value, raw, form.mode)
        }
    }
}
