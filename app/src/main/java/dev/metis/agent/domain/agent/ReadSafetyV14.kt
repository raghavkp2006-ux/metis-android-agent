package dev.metis.agent.domain.agent

/** No classifier, context builder or repository is called while validating read authority. */
object ReadSafetyV14 {
    val intents = ReadSafetyV12.intents

    fun parse(request: AgentRequest): LanguageParse? =
        if (validEnvelope(request)) ReadGrammarV14.parse(request.text) else null

    private fun validEnvelope(request: AgentRequest) =
        request.source in setOf(InputSource.TEXT, InputSource.VOICE) && request.text.length <= MAX_REQUEST &&
            !hasControl(request.text)

    private fun hasControl(text: String) = text.codePoints().anyMatch {
        Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
    }

    fun allows(request: AgentRequest, intent: AgentIntent) = parse(request)?.intent == intent

    fun matches(request: AgentRequest, intent: AgentIntent, entities: List<ExtractedEntity>) =
        parse(request)?.let { it.intent == intent && it.entities == entities } == true

    fun validate(request: ParsedRequest): SpecialistResult.Clarification? =
        if (!request.prediction.negated && matches(request.request, request.prediction.intent, request.entities)) null
        else ReadSafetyV6.clarification(request.request)

    private const val MAX_REQUEST = 512
}

/** Explicit selection keeps historical constructors reproducible; the Android app selects V16. */
enum class ReadCandidate {
    V12, V14, V15, V16;

    fun parse(request: AgentRequest) = when (this) {
        V12 -> ReadSafetyV12.parse(request)
        V14 -> ReadSafetyV14.parse(request)
        V15 -> ReadSafetyV15.plan(request)?.parsed
        V16 -> ReadSafetyV16.plan(request)?.parsed
    }

    fun allows(request: AgentRequest, intent: AgentIntent) = parse(request)?.intent == intent

    fun matches(request: AgentRequest, intent: AgentIntent, entities: List<ExtractedEntity>) =
        parse(request)?.let { it.intent == intent && it.entities == entities } == true

    fun validate(request: ParsedRequest): SpecialistResult.Clarification? =
        if (this == V16) ReadSafetyV16.validate(request)
        else if (this == V15) ReadSafetyV15.validate(request)
        else if (!request.prediction.negated && matches(request.request, request.prediction.intent,
            request.entities)) null
        else ReadSafetyV6.clarification(request.request)

    val scoped: Boolean get() = this == V15 || this == V16

    fun scopedPlan(request: AgentRequest) = when (this) {
        V15 -> ReadSafetyV15.plan(request)
        V16 -> ReadSafetyV16.plan(request)
        else -> null
    }
}
