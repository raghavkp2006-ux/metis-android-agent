package dev.metis.agent.domain.agent

/** Generated from candidate_v14/grammar.py; complete productions only. */
internal object ReadPatternsV14 {
    val forms = listOf(
        ReadFormV14(Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?what was your " +
            "recollection of (?<query>my talk with (?<person>.{1,100}?))[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "speech"),
        ReadFormV14(Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?locate all the" +
            " notes i left on (?<query>(?<person>.{1,100}?) and (?<subject>.{1,220}" +
            "?))[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "person_topic"),
        ReadFormV14(Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?what can you r" +
            "ecall about (?<query>(?<subject>.{1,220}?) and (?<person>.{1,100}?))[?" +
            "!.]?\\s*",
            RegexOption.IGNORE_CASE), "topic_person"),
        ReadFormV14(Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?(?:what did i " +
            "(?:say|tell you) about|what (?:do you|did you) remember about|what can" +
            " you recall about|was any information saved from|find (?:some|any) mem" +
            "ory (?:about|involving)|look for (?:some|any) memory to do with|find a" +
            "nything saved about|find (?:some|any)(?: kind of)? reference on|locate" +
            " anything to do with) (?<query>.{1,300}?)[?!.]?\\s*",
            RegexOption.IGNORE_CASE), "topic")
    )
}

internal data class ReadFormV14(val pattern: Regex, val mode: String)
