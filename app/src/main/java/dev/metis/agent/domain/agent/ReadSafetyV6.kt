package dev.metis.agent.domain.agent

/** Phase 15 v6: a label never authorizes a repository read. No model dependency. */
object ReadSafetyV6 {
    const val VERSION = "phase15-read-safety-v6"
    val intents = setOf(AgentIntent.CHECK_TASKS, AgentIntent.CHECK_MEMORY, AgentIntent.WHAT_DID_I_PROMISE)

    /** Reparse the entire original request; never trust a classifier's label or extracted noun phrase. */
    fun validate(request: ParsedRequest): SpecialistResult.Clarification? {
        val input = request.request
        val parsed = EnglishRules { request.context.zoneId }.parse(input)
        return if (allows(input, request.prediction.intent) && parsed.entities == request.entities) null
        else clarification(input)
    }

    /** Runs before context construction, which may itself access saved settings/records. */
    fun allows(input: AgentRequest, intent: AgentIntent): Boolean {
        val parsed = EnglishRules().parse(input)
        return input.text.length <= MAX_REQUEST_LENGTH &&
            input.text.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() } &&
            !UNSAFE.containsMatchIn(input.text) &&
            !parsed.negated && parsed.intent == intent && referencesValid(parsed)
    }

    fun clarification(input: AgentRequest) = SpecialistResult.Clarification(
            "I cannot safely read the saved records for this request: ${input.text}\n" +
                "Specify one supported read with a named person or a concrete memory topic. " +
                "Date, age, subject, priority and other filters need clarification before searching.",
            setOf("read scope / person / topic / filters"))

    private fun referencesValid(parsed: LanguageParse): Boolean = when (parsed.intent) {
        AgentIntent.CHECK_TASKS -> parsed.entities.isEmpty()
        AgentIntent.CHECK_MEMORY -> parsed.entities.singleOrNull()?.let {
            validReference(it.rawValue, TOPIC_EXCLUSIONS) && it.rawValue.any(Char::isLetterOrDigit) &&
                !GENERIC_TOPIC.matches(it.rawValue)
        } == true
        AgentIntent.WHAT_DID_I_PROMISE -> parsed.entities.singleOrNull()?.let {
            val person = it.rawValue
            person.length <= MAX_PERSON_LENGTH &&
                person.split(' ').count { word -> word.any(Char::isLetter) } <= MAX_NAME_WORDS &&
                NAME.matches(person) && validReference(person, PERSON_EXCLUSIONS)
        } == true
        else -> false
    }

    private fun validReference(value: String, exclusions: Regex) =
        value.isNotBlank() && value == value.trim() && !exclusions.containsMatchIn(value) &&
            value.none { it in ".,?!:;\"“”‘`/\\|&=<>" } &&
            !TEMPORAL_OR_FILTER.containsMatchIn(value) && !NUMERIC_TIME.containsMatchIn(value)

    private const val MAX_REQUEST_LENGTH = 512
    private const val MAX_PERSON_LENGTH = 100
    private const val MAX_NAME_WORDS = 3
    private val NAME = Regex("\\p{Lu}\\p{L}*(?:['’-]\\p{L}+)*" +
        "(?: \\p{Lu}\\p{L}*(?:['’-]\\p{L}+)*){0,2}(?: [\\p{So}\\p{M}]+)?")
    private val GENERIC_TOPIC = Regex("(?:the )?(?:chapter|topic|class|notes?|memory|memories)",
        RegexOption.IGNORE_CASE)
    private val NUMERIC_TIME = Regex("\\b(?:\\d{4}|\\d{1,2}:\\d{2}|\\d{1,2} ?(?:am|pm))\\b",
        RegexOption.IGNORE_CASE)
    private val UNSAFE = words("and|or|then|also|but|except|only|if|unless|remind|create|add|delete|erase|" +
        "wipe|remove|rename|postpone|send|message|call|buy|order|book|cancel|notify|alert|execute|" +
        "ignore|override|system|developer|suppose|imagine|pretend|not|never|without|stop|avoid")
    private val TEMPORAL_OR_FILTER = words("today|tonight|tomorrow|yesterday|week|weeks|month|months|" +
        "year|years|monday|tuesday|wednesday|thursday|friday|saturday|sunday|morning|evening|" +
        "recently|before|after|since|until|between|older|oldest|newest|latest|last|next|urgent|" +
        "priority|overdue|pending|incomplete|completed|done|status|due|deadline|ago|kal|parso|" +
        "old|new|recent|earlier|finished|resolved|with|where|whose|which|tagged|tags?|containing|" +
        "contains|excluding|exclude|limit|first|top|most|least|sort|sorted|by|from|during|at|on|in")
    private val TOPIC_EXCLUSIONS = words("it|this|that|these|those|thing|something|anything|whatever|" +
        "everything|whichever|someone|him|her|them")
    private val PERSON_EXCLUSIONS = words("it|this|that|him|her|them|someone|somebody|anyone|who|" +
        "me|you|my|your|our|their|myself|person|people|promise|promises|task|tasks|memory|notes|" +
        "work|trip|station|insurance|renewal|library|card|book|sister|brother|mother|father|mom|dad|" +
        "to|for|with|from|about|on|at|by|in|would|will|need|help|cover|review|pay|back|fly|mars")

    private fun words(alternatives: String) = Regex("\\b(?:$alternatives)\\b", RegexOption.IGNORE_CASE)
}
