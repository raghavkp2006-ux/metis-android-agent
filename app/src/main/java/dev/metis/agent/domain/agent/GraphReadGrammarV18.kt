package dev.metis.agent.domain.agent

import java.util.Locale

enum class GraphReadKindV18 { PROJECT_PENDING, PERSON_PENDING, OPEN_PROMISES }

/** A typed slot, never authority to read. Diagnostic output deliberately omits the name. */
internal class GraphReadFormV18(
    val kind: GraphReadKindV18,
    val name: String,
    val start: Int,
    val end: Int,
) {
    val pronoun get() = name.lowercase(Locale.ROOT) in setOf("him", "her", "them")
    override fun toString() = "GraphReadFormV18(redacted)"
}

/** Additive full-request productions. Historical rules retain precedence in EnglishRules. */
object GraphReadGrammarV18 {
    @Suppress("ReturnCount") // Each invalid envelope, production or slot stops before any read.
    internal fun form(request: AgentRequest): GraphReadFormV18? {
        if (!envelope(request)) return null
        val matched = forms.firstNotNullOfOrNull { (kind, pattern) ->
            pattern.matchEntire(request.text)?.let { kind to it }
        } ?: return null
        val name = requireNotNull(matched.second.groups["name"])
        val person = matched.first != GraphReadKindV18.PROJECT_PENDING
        val pronoun = person && name.value.lowercase(Locale.ROOT) in setOf("him", "her", "them")
        if (!pronoun && !ReadSafetyV10.validSubject(name.value, name.value, if (person) "person" else "topic"))
            return null
        // Historical topic reads allow a few content-negation/date productions. Graph names do not.
        if (!pronoun && listOf(ReadPatternsV10.UNSAFE, ReadPatternsV10.REFERENCE,
                ReadPatternsV10.FILTER, ReadPatternsV10.TIME).any { it.containsMatchIn(name.value) }) return null
        // Reject unsupported clauses even when they look like part of a project name.
        if (extraConstraint.containsMatchIn(name.value)) return null
        return GraphReadFormV18(matched.first, name.value, name.range.first, name.range.last + 1)
    }

    fun parse(request: AgentRequest): LanguageParse? = form(request)?.let { form ->
        val person = form.kind != GraphReadKindV18.PROJECT_PENDING
        LanguageParse(if (person) AgentIntent.WHAT_DID_I_PROMISE else AgentIntent.CHECK_TASKS,
            if (person) listOf(ExtractedEntity(EntityKind.PERSON, form.name, form.start, form.end, 1.0))
            else emptyList()) // A project name must not become a task referent for "complete it".
    }

    internal fun claims(text: String) = claimed.containsMatchIn(text)

    internal fun envelope(request: AgentRequest) =
        request.source in setOf(InputSource.TEXT, InputSource.VOICE) && request.text.length <= MAX_REQUEST &&
            request.text.codePoints().noneMatch {
                Character.isISOControl(it) || Character.getType(it) in
                    setOf(Character.FORMAT.toInt(), Character.SURROGATE.toInt())
            }

    private const val MAX_REQUEST = 512
    private const val PREFIX = "\\s*(?:please\\s+)?"
    private const val END = "\\s*[?!.]?\\s*"
    private val forms = listOf(
        GraphReadKindV18.PROJECT_PENDING to Regex(
            "^${PREFIX}what is pending in (?<name>.+?)$END", RegexOption.IGNORE_CASE),
        GraphReadKindV18.PERSON_PENDING to Regex(
            "^${PREFIX}what is pending with (?<name>.+?)$END", RegexOption.IGNORE_CASE),
        GraphReadKindV18.OPEN_PROMISES to Regex(
            "^${PREFIX}what did i promise (?<name>.+?) that is (?:still open|pending)$END",
            RegexOption.IGNORE_CASE),
        GraphReadKindV18.OPEN_PROMISES to Regex(
            "^${PREFIX}what promises to (?<name>.+?) are still open$END", RegexOption.IGNORE_CASE),
    )
    private val claimed = Regex("^${PREFIX}what (?:is pending\\b|did i promise\\b|promises to\\b)",
        RegexOption.IGNORE_CASE)
    private val extraConstraint = Regex("\\b(?:who|when|how|where|which|having|has|have|must|" +
        "should|could|would|can|is|are|was|were|than|under|over|greater|less|ascending|descending|" +
        "complete|finish|set|start|run|grant|revoke|permission|select|insert|update|drop|sql)\\b",
        RegexOption.IGNORE_CASE)
}
