package dev.metis.agent.domain.agent

/** Full original request validation before context; rechecked before narrow read dispatch. */
object ReadSafetyV8 {
    val intents = ReadSafetyV6.intents

    fun matches(input: AgentRequest, intent: AgentIntent, entities: List<ExtractedEntity>) =
        allows(input, intent) && ReadGrammarV8.parse(input.text)?.entities == entities

    fun validate(request: ParsedRequest): SpecialistResult.Clarification? =
        if (matches(request.request, request.prediction.intent, request.entities)) null
        else ReadSafetyV6.clarification(request.request)

    @Suppress("ReturnCount") // No context or repository calls on these exits.
    fun allows(input: AgentRequest, intent: AgentIntent): Boolean {
        if (!validEnvelope(input)) return false
        val parsed = ReadGrammarV8.parse(input.text) ?: return false
        if (parsed.intent != intent) return false
        val body = ReadGrammarV8.body(input.text).first
        if (intent != AgentIntent.CHECK_MEMORY) return ReadSafetyV7.allows(input.copy(text = body), intent)
        val entity = parsed.entities.single()
        val query = (entity.normalizedValue as? NormalizedValue.Text)?.value ?: entity.rawValue
        // Only the recognized recall prefix is exempt from the word 'remind' prohibition.
        // Both complete literal query and annotated reference must pass retained v7 restrictions.
        val suffix = body.substring(ReadGrammarV8.recallPrefix(body))
        return UNSAFE.containsMatchIn(suffix).not() &&
            ReadSafetyV7.allows(input.copy(text = "Search memory for $query"), intent) &&
            ReadSafetyV7.allows(input.copy(text = "Search memory for ${entity.rawValue}"), intent)
    }

    private fun validEnvelope(input: AgentRequest) = input.source in setOf(InputSource.TEXT, InputSource.VOICE) &&
        input.text.length <= MAX_LENGTH &&
        input.text.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }

    private const val MAX_LENGTH = 512
    private val UNSAFE = Regex("\\b(?:and|or|then|also|but|except|only|if|unless|remind|create|add|delete|" +
        "erase|wipe|remove|rename|postpone|send|message|call|buy|order|book|cancel|notify|alert|execute|" +
        "ignore|override|system|developer|suppose|imagine|pretend|no|not|never|without|stop|avoid|" +
        "publish|archive|email|text|share|export|mark|don['’]?t)\\b", RegexOption.IGNORE_CASE)
}
