package dev.metis.agent.domain.agent

/** Classifier confidence never replaces full-request and exact typed-scope validation. */
object ReadSafetyV16 {
    fun plan(request: AgentRequest): ReadPlanV15? =
        if (validEnvelope(request)) ReadGrammarV16.plan(request.text) else null

    private fun validEnvelope(request: AgentRequest) =
        request.source in setOf(InputSource.TEXT, InputSource.VOICE) && request.text.length <= MAX_REQUEST &&
            request.text.codePoints().noneMatch(::control)

    private fun control(value: Int) =
        Character.isISOControl(value) || Character.getType(value) == Character.FORMAT.toInt()

    fun validate(request: ParsedRequest): SpecialistResult.Clarification? {
        val expected = plan(request.request)
        return if (!request.prediction.negated && request.context.now == request.request.timestamp &&
            matches(request, expected)) null else ReadSafetyV6.clarification(request.request)
    }

    private fun matches(request: ParsedRequest, expected: ReadPlanV15?) = expected?.let {
        it.parsed.intent == request.prediction.intent && it.parsed.entities == request.entities &&
            it.scope == request.readScope
    } == true

    private const val MAX_REQUEST = 512
}
