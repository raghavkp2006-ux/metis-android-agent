package dev.metis.agent.domain.agent

/** Complete read productions only. Topic prepositions do not grant filter or action authority. */
object ReadGrammarV7 {
    const val VERSION = "phase15-read-coverage-v7"

    fun parse(text: String): LanguageParse? = FORMS.firstNotNullOfOrNull { form ->
        form.pattern.matchEntire(text)?.let { match ->
            LanguageParse(form.intent, form.kind?.let { listOf(entity(it, match.groups[1]!!)) }.orEmpty())
        }
    }

    private val FORMS = listOf(
        form(AgentIntent.CHECK_TASKS,
            "(?:what tasks do i have|(?:show|list) (?:my )?(?:(?:open|pending|unfinished|incomplete) )?tasks|" +
                "what tasks are (?:open|pending|unfinished|incomplete)|" +
                "do i have (?:any )?(?:open|pending|unfinished|incomplete) tasks|" +
                "what is (?:still )?pending on my task list)"),
        form(AgentIntent.CHECK_MEMORY,
            "(?:what do you know about|search (?:my )?memory for|" +
                "what did i (?:write(?: down)?|note|save) about|" +
                "what have i (?:written|noted|saved|recorded) about|" +
                "(?:show|find|retrieve|look up) (?:my )?(?:saved )?(?:notes|memories|records|details) " +
                "(?:about|on|regarding)|(?:do i have|are there) (?:any )?(?:saved )?(?:notes|memories) " +
                "(?:about|on|regarding)) (?:my )?(.{1,200}?)", EntityKind.MEMORY),
        form(AgentIntent.WHAT_DID_I_PROMISE,
            "(?:what did i promise (?:to )?|what have i promised (?:to )?|what do i owe |" +
                "(?:show|list) (?:my )?promises (?:to|for) )(.{1,200}?)", EntityKind.PERSON),
    )

    private fun form(intent: AgentIntent, expression: String, kind: EntityKind? = null) = ReadFormV7(
        intent, Regex("\\s*(?:please )?$expression[?!.]?\\s*", RegexOption.IGNORE_CASE), kind)
}

private data class ReadFormV7(val intent: AgentIntent, val pattern: Regex, val kind: EntityKind?)
