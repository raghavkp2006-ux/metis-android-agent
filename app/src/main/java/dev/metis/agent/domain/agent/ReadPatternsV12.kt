package dev.metis.agent.domain.agent

/** Generated from candidate_v12/v12_grammar.py; complete productions only. */
internal object ReadPatternsV12 {
    val reserved = Regex(
        "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?remind me of (?:(?:my |the )?" +
        "notes (?:about|on|regarding) |(?:the|my) promises i made to )", RegexOption.IGNORE_CASE)
    val forms = listOf(
        ReadFormV12(AgentIntent.WHAT_DID_I_PROMISE, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?remind me of (?:the|my) promi" +
            "ses i made to (?<reference>.{1,100}?)[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "person"),
        ReadFormV12(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?remind me of (?:my |the )?not" +
            "es (?:about|on|regarding) (?<query>.{1,300}?)[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "topic"),
        ReadFormV12(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?can you remember what i told " +
            "you in (?:the )?(?<query>.{1,300}?)[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "topic"),
        ReadFormV12(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?look for anything i saved on " +
            "(?<query>(?<reference>.{1,100}?) and (?<subject>.{1,200}?))[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "coordinated"),
        ReadFormV12(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?so, what can you recall from " +
            "(?<query>what i said to (?<reference>.{1,100}?))[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "speech"),
        ReadFormV12(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?did i leave any notes behind " +
            "for a (?<query>meeting with (?<reference>.{1,100}?))[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "meeting"),
        ReadFormV12(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?do i already have (?:some |an" +
            "y )?plans for (?:today|tomorrow)(?: (?:morning|afternoon|evening))?[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "none"),
        ReadFormV12(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?which meetings are coming up " +
            "next[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "none"),
        ReadFormV12(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?have (?:i got )?any meetings " +
            "with (?<reference>.{1,100}?) this week[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "calendar_person"),
        ReadFormV12(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?when do i have to meet again " +
            "concerning (?:the )?(?<query>.{1,200}?)[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "calendar_topic")
    )
}

internal data class ReadFormV12(val intent: AgentIntent, val pattern: Regex, val mode: String)
