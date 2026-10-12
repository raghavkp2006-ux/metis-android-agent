package dev.metis.agent.domain.agent

object ReadSafetyV15 {
    fun plan(request: AgentRequest): ReadPlanV15? =
        if (validEnvelope(request)) ReadGrammarV15.plan(request.text) else null

    private fun validEnvelope(request: AgentRequest) =
        request.source in setOf(InputSource.TEXT, InputSource.VOICE) && request.text.length <= MAX_REQUEST &&
            !request.text.codePoints().anyMatch(::control)

    private fun control(value: Int) =
        Character.isISOControl(value) || Character.getType(value) == Character.FORMAT.toInt()

    fun validate(request: ParsedRequest): SpecialistResult.Clarification? {
        val expected = plan(request.request)
        return if (!request.prediction.negated && request.context.now == request.request.timestamp &&
            matches(request, expected)) null
        else ReadSafetyV6.clarification(request.request)
    }

    private fun matches(request: ParsedRequest, expected: ReadPlanV15?) = expected?.let {
        it.parsed.intent == request.prediction.intent && it.parsed.entities == request.entities &&
            it.scope == request.readScope
    } == true

    fun clarification(request: AgentRequest): SpecialistResult.Clarification {
        val capability = when {
            Regex("^\\s*did i assign myself\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(request.text) -> "Explicit task-assignee lookup is unavailable."
            Regex("^\\s*(?:is there already a|find (?:an?|any)) reminder\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(request.text) -> "Scheduled-reminder lookup is unavailable."
            Regex("^\\s*find any activity\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(request.text) -> "Search across saved record types is unavailable."
            else -> null
        }
        if (capability != null) return SpecialistResult.Clarification(
            "$capability Your requested scope is retained: ${request.text}", setOf("capability"))
        val missingRecipient = Regex("\\b(?:did i promise to|have i mentioned that i['’]ll|did i say i['’]d)\\b",
            RegexOption.IGNORE_CASE).containsMatchIn(request.text)
        return if (missingRecipient) SpecialistResult.Clarification(
            "Specify the saved recipient before looking up that commitment: ${request.text}", setOf("person"))
        else ReadSafetyV6.clarification(request)
    }

    private const val MAX_REQUEST = 512
}
