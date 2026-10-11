package dev.metis.agent.domain.agent

import dev.metis.agent.domain.storage.MemorySearchQuery

/** Labels, extracted spans and normalized subjects grant no authority by themselves. */
object ReadSafetyV10 {
    val intents = ReadSafetyV6.intents + AgentIntent.CHECK_CALENDAR

    fun parse(input: AgentRequest) = if (validEnvelope(input)) ReadGrammarV10.parse(input.text) else null

    fun allows(input: AgentRequest, intent: AgentIntent) = parse(input)?.intent == intent

    fun matches(input: AgentRequest, intent: AgentIntent, entities: List<ExtractedEntity>) =
        allows(input, intent) && ReadGrammarV10.parse(input.text)?.entities == entities

    fun validate(request: ParsedRequest): SpecialistResult.Clarification? =
        if (!request.prediction.negated && matches(request.request, request.prediction.intent, request.entities)) null
        else ReadSafetyV6.clarification(request.request)

    private fun validEnvelope(input: AgentRequest) = input.source in setOf(InputSource.TEXT, InputSource.VOICE) &&
        input.text.length <= MAX_REQUEST_LENGTH &&
        !hasControl(input.text)

    internal fun validSubject(query: String, reference: String, mode: String): Boolean {
        val bound = if (mode == "person") MAX_PERSON_LENGTH else MAX_ANNOTATION_LENGTH
        return reference.length <= bound && query.length <= MemorySearchQuery.MAX_SEARCH_LENGTH &&
            when (mode) {
                "person" -> NAME.matches(reference) && plain(reference) &&
                    !ReadPatternsV10.PERSON_EXCLUSIONS.containsMatchIn(reference)
                "attribution" -> validGroups(query, ReadPatternsV10.ATTRIBUTION, listOf("subject", "place"))
                "identity" -> validGroups(query, ReadPatternsV10.IDENTITY, listOf("place", "subject"))
                "street" -> ReadPatternsV10.STREET.matches(query)
                "hotel" -> validGroups(query, ReadPatternsV10.HOTEL, listOf("place"))
                else -> topic(query)
            }
    }

    private fun validGroups(query: String, pattern: Regex, groups: List<String>) = pattern.matchEntire(query)?.let {
        match -> groups.all { match.groups[it]?.value?.let(::plain) != false }
    } == true

    private fun topic(query: String): Boolean = plain(query) ||
        listOf(ReadPatternsV10.CONTENT to "activity", ReadPatternsV10.WEATHER to "place",
            ReadPatternsV10.HOURS to "place", ReadPatternsV10.PASSWORD_ARCHIVE to "subject").any { (pattern, group) ->
            pattern.matchEntire(query)?.let { plain(it.groups[group]!!.value) } == true
        }

    private fun plain(value: String) = value.isNotBlank() && value == value.trim() &&
        value.any(Char::isLetterOrDigit) && !ReadPatternsV10.GENERIC.matches(value) &&
        value.none { it in ".,?!:;\"“”‘`/\\|&=<>" } && !hasControl(value) &&
        !QUOTED.containsMatchIn(value) &&
        listOf(ReadPatternsV10.UNSAFE, ReadPatternsV10.REFERENCE, ReadPatternsV10.FILTER, ReadPatternsV10.TIME)
            .none { it.containsMatchIn(value) }

    private fun hasControl(value: String) = value.codePoints().anyMatch {
        Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
    }

    private const val MAX_REQUEST_LENGTH = 512
    private const val MAX_ANNOTATION_LENGTH = 200
    private const val MAX_PERSON_LENGTH = 100
    private val QUOTED = Regex("(?<!\\p{L})['’]|['’](?!\\p{L})")
    private val NAME = Regex("\\p{Lu}\\p{L}*(?:['’-]\\p{L}+)*" +
        "(?: \\p{Lu}\\p{L}*(?:['’-]\\p{L}+)*){0,2}(?: [\\p{So}\\p{M}]+)?")
}
