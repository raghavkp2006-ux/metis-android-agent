package dev.metis.agent.domain.agent

/** Broader complete forms with the same pre-context boundary; no ML inference. */
object ReadSafetyV7 {
    val intents = ReadSafetyV6.intents

    fun validate(request: ParsedRequest): SpecialistResult.Clarification? {
        val parsed = ReadGrammarV7.parse(request.request.text)
        return if (allows(request.request, request.prediction.intent) && parsed?.entities == request.entities) null
        else ReadSafetyV6.clarification(request.request)
    }

    @Suppress("ReturnCount") // Fail before any context or repository operation.
    fun allows(input: AgentRequest, intent: AgentIntent): Boolean {
        if (!validEnvelope(input) || UNSAFE.containsMatchIn(input.text)) return false
        val parsed = ReadGrammarV7.parse(input.text) ?: return false
        if (parsed.intent != intent) return false
        return if (intent == AgentIntent.CHECK_MEMORY) {
            parsed.entities.singleOrNull()?.rawValue?.let(::validTopic) == true
        } else ReadSafetyV6.allows(input, intent)
    }

    private fun validEnvelope(input: AgentRequest) = input.source in setOf(InputSource.TEXT, InputSource.VOICE) &&
        input.text.length <= MAX_REQUEST_LENGTH &&
        input.text.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }

    private fun validTopic(topic: String) = topic.isNotBlank() && topic == topic.trim() &&
        topic.any(Char::isLetterOrDigit) && !GENERIC.matches(topic) &&
        topic.none { it in ".,?!:;\"“”‘`/\\|&=<>" } && !QUOTED.containsMatchIn(topic) &&
        !REFERENCE.containsMatchIn(topic) && !FILTER.containsMatchIn(topic) && !TIME.containsMatchIn(topic)

    private const val MAX_REQUEST_LENGTH = 512
    private val GENERIC = Regex("(?:the )?(?:chapter|topic|class|notes?|memory|memories)", RegexOption.IGNORE_CASE)
    private val QUOTED = Regex("(?<!\\p{L})['’]|['’](?!\\p{L})")
    private val UNSAFE = words("and|or|then|also|but|except|only|if|unless|remind|create|add|delete|erase|" +
        "wipe|remove|rename|postpone|send|message|call|buy|order|book|cancel|notify|alert|execute|" +
        "ignore|override|system|developer|suppose|imagine|pretend|not|never|without|stop|avoid|" +
        "publish|archive|email|text|share|export|mark|don['’]?t")
    private val REFERENCE = words("it|this|that|these|those|thing|something|anything|whatever|" +
        "everything|whichever|someone|him|her|them|somewhere|stuff")
    private val FILTER = words("today|tonight|tomorrow|yesterday|week|weeks|month|months|year|years|" +
        "day|days|hours?|minutes?|seconds?|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
        "january|february|march|april|may|june|july|august|september|october|november|december|" +
        "noon|midnight|morning|evening|recently|before|after|since|until|between|older|oldest|newest|" +
        "latest|last|next|urgent|priority|overdue|pending|incomplete|completed|done|status|due|deadline|" +
        "ago|kal|parso|old|new|recent|earlier|finished|resolved|where|whose|which|tagged|tags?|" +
        "containing|contains|excluding|exclude|limit|first|top|most|least|sort|sorted|by|from|during")
    private val TIME = Regex("\\b(?:\\d{4}|\\d{1,2}:\\d{2}|\\d{1,2} ?(?:am|pm)|at \\d+)\\b",
        RegexOption.IGNORE_CASE)

    private fun words(alternatives: String) = Regex("\\b(?:$alternatives)\\b", RegexOption.IGNORE_CASE)
}
