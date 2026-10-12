package dev.metis.agent.domain.agent

internal data class ReadFormV15(val kind: AgentIntent, val pattern: Regex, val status: String = "",
    val dateField: ReadDateV15.Field? = null, val requestedField: String = "", val content: Boolean = false,
    val negative: Boolean = false, val meetings: Boolean = false)

/** Closed phrases are grammar, never a list of complete user examples. */
internal object ReadFormsV15 {
    private const val SUBJECT = "(?<subject>.{1,300}?)"
    private const val PERSON = "(?<person>.{1,100}?)"
    private const val DATE = "(?<date>today|tomorrow|this week|\\d{4}-\\d{2}-\\d{2})"
    private const val PREFIX = "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?"
    private const val END = "[?!.]?\\s*"
    val content = Regex("(?<negative>not )?(?:send|review|submit|prepare|" +
        "return|finish|help|bring|share|email|call|buy|" +
        "purchase|print|rewrite|edit|update|deploy|push|test|register|pay|reimburse|include|meet|check) " +
        "(?<object>.{1,220}?)(?: (?:today|tomorrow|this week))?", RegexOption.IGNORE_CASE)
    val reserved = Regex("^$PREFIX(?:did i promise|did i tell|did i save a note saying|find " +
        "(?:fulfilled|cancelled|open) promises)\\b",
        RegexOption.IGNORE_CASE)

    val forms = taskForms() + promiseForms() + memoryForms() + calendarForms()

    @Suppress("LongParameterList") // Declarative productions name independent typed fields explicitly.
    private fun form(kind: AgentIntent, text: String, status: String = "", date: ReadDateV15.Field? = null,
                     field: String = "", content: Boolean = false, negative: Boolean = false,
                     meetings: Boolean = false) = ReadFormV15(kind, Regex(PREFIX + text + END, RegexOption.IGNORE_CASE),
                         status, date, field, content, negative, meetings)

    private fun taskForms() = listOf(
        form(AgentIntent.CHECK_TASKS,
            "(?:have i (?:already )?created|did i create|do i have) (?:a |any )?task to $SUBJECT", "ANY",
                content = true),
        form(AgentIntent.CHECK_TASKS, "what tasks do i have (?:related to|about) $SUBJECT", "OPEN"),
        form(AgentIntent.CHECK_TASKS,
            "(?:find|show|list) (?<status>open|completed|cancelled) tasks (?:related to|about) $SUBJECT due " +
                "$DATE", date = ReadDateV15.Field.DUE),
        form(AgentIntent.CHECK_TASKS,
            "(?:find|show|list) (?<status>open|completed|cancelled) tasks (?:related to|about) $SUBJECT"),
        form(AgentIntent.CHECK_TASKS, "(?:find|show|list) tasks (?:related to|about) $SUBJECT due $DATE", "ANY",
            ReadDateV15.Field.DUE),
        form(AgentIntent.CHECK_TASKS,
            "(?:find|show|list) tasks (?:related to|about) $SUBJECT completed on $DATE", "COMPLETED",
                ReadDateV15.Field.COMPLETED),
        form(AgentIntent.CHECK_TASKS, "what is the (?<field>due date) of my task to $SUBJECT", "ANY",
            field = "due date", content = true),
        form(AgentIntent.CHECK_TASKS, "what is the (?<field>completion date) of my task to $SUBJECT", "ANY",
            field = "completion date", content = true),
    )

    private fun promiseForms() = listOf(
        form(AgentIntent.WHAT_DID_I_PROMISE, "did i (?:tell|promise) $PERSON i would (?<subject>not .{1,300}?)",
            "ANY", content = true, negative = true),
        form(AgentIntent.WHAT_DID_I_PROMISE, "did i tell $PERSON i would $SUBJECT", "ANY", content = true),
        form(AgentIntent.WHAT_DID_I_PROMISE, "did i promise $PERSON to $SUBJECT", "ANY", content = true),
        form(AgentIntent.WHAT_DID_I_PROMISE,
            "(?:find|show) (?<status>open|fulfilled|cancelled) promises to $PERSON about $SUBJECT"),
        form(AgentIntent.WHAT_DID_I_PROMISE, "(?:find|show) promises to $PERSON about $SUBJECT due $DATE",
            "ANY", ReadDateV15.Field.DUE),
    )

    private fun memoryForms() = listOf(
        form(AgentIntent.CHECK_MEMORY, "did i (?:save|record|mention) anything about $SUBJECT"),
        form(AgentIntent.CHECK_MEMORY, "what (?<field>email address) did i save for $SUBJECT", field = "email address"),
        form(AgentIntent.CHECK_MEMORY, "what (?<field>number) did i save for $SUBJECT", field = "number"),
        form(AgentIntent.CHECK_MEMORY, "did i save a note saying i would (?<subject>not .{1,300}?)",
            content = true, negative = true),
        form(AgentIntent.CHECK_MEMORY, "(?:find|show) notes about $SUBJECT saved (?:on )?$DATE",
            date = ReadDateV15.Field.CREATED),
    )

    private fun calendarForms() = listOf(
        form(AgentIntent.CHECK_CALENDAR, "did i save the $SUBJECT date"),
        form(AgentIntent.CHECK_CALENDAR, "(?:find|show) meetings with $PERSON about $SUBJECT on $DATE",
            date = ReadDateV15.Field.START, meetings = true),
        form(AgentIntent.CHECK_CALENDAR, "do i have a meeting related to $SUBJECT", meetings = true),
        form(AgentIntent.CHECK_CALENDAR, "(?:find|show) calendar events about $SUBJECT on $DATE",
            date = ReadDateV15.Field.START),
        form(AgentIntent.CHECK_CALENDAR, "(?:find|show) meetings with $PERSON $DATE",
            date = ReadDateV15.Field.START, meetings = true),
    )
}
