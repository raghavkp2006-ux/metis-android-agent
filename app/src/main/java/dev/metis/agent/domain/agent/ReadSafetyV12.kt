package dev.metis.agent.domain.agent

/** Predictions and forged extraction values cannot bypass complete request validation. */
object ReadSafetyV12 {
    val intents = ReadSafetyV10.intents

    fun parse(input: AgentRequest): LanguageParse? =
        if (validEnvelope(input)) ReadGrammarV12.parse(input.text) else null

    fun allows(input: AgentRequest, intent: AgentIntent) = parse(input)?.intent == intent

    fun matches(input: AgentRequest, intent: AgentIntent, entities: List<ExtractedEntity>) =
        parse(input)?.let { it.intent == intent && it.entities == entities } == true

    fun validate(request: ParsedRequest): SpecialistResult.Clarification? =
        if (!request.prediction.negated && matches(request.request, request.prediction.intent, request.entities)) null
        else ReadSafetyV6.clarification(request.request)

    private fun validEnvelope(input: AgentRequest) = input.source in setOf(InputSource.TEXT, InputSource.VOICE) &&
        input.text.length <= MAX_REQUEST && !hasControl(input.text)

    private fun hasControl(text: String) = text.codePoints().anyMatch {
        Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
    }

    private const val MAX_REQUEST = 512
}
