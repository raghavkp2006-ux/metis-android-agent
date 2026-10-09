package dev.metis.agent.domain.agent

import java.time.ZoneId

/** Closed English grammar. Scores mark rule matches, not calibrated model probabilities. */
class EnglishRules(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : IntentClassifier, EntityExtractor {
    override suspend fun classify(request: AgentRequest): IntentPrediction = parse(request).prediction
    override suspend fun extract(request: AgentRequest): List<ExtractedEntity> = parse(request).entities

    @Suppress("ReturnCount") // Explicit safe exits before entity resolution.
    fun parse(request: AgentRequest): LanguageParse {
        if (request.source != InputSource.TEXT) return LanguageParse(AgentIntent.UNSUPPORTED)
        val text = request.text
        if (NEGATION.containsMatchIn(text)) return LanguageParse(AgentIntent.UNKNOWN, negated = true)
        val match = RULES.firstNotNullOfOrNull { rule -> rule.pattern.matchEntire(text)?.let { rule to it } }
            ?: return LanguageParse(AgentIntent.UNKNOWN)
        val (rule, groups) = match
        val entities = when (rule.intent) {
            AgentIntent.CREATE_REMINDER -> ReminderResolution.extract(request, groups, zone())
            AgentIntent.CREATE_TASK, AgentIntent.COMPLETE_TASK,
                AgentIntent.DELETE_TASK -> listOf(entity(EntityKind.TITLE, groups.groups[1]!!))
            AgentIntent.UPDATE_TASK -> listOf(entity(EntityKind.TASK, groups.groups[1]!!),
                entity(EntityKind.TITLE, groups.groups[2]!!))
            AgentIntent.POSTPONE_TASK -> listOf(entity(EntityKind.TASK, groups.groups[1]!!),
                entity(EntityKind.TIME, groups.groups[2]!!,
                    ReminderResolution.resolve(request, groups.groupValues[2],
                        groups.groupValues[POSTPONE_TIME_GROUP], zone())
                        ?.let { NormalizedValue.Time(it) }))
            AgentIntent.CREATE_TIMER -> timerEntities(groups)
            AgentIntent.CHECK_MEMORY -> listOf(entity(EntityKind.MEMORY, groups.groups[1]!!))
            AgentIntent.WHAT_DID_I_PROMISE -> listOf(entity(EntityKind.PERSON, groups.groups[1]!!))
            else -> emptyList()
        }
        return LanguageParse(rule.intent, entities)
    }

    private fun timerEntities(match: MatchResult): List<ExtractedEntity> {
        val duration = match.groups[1]!!
        val amount = duration.value.toLongOrNull()
        val multiplier = if (match.groupValues[2].startsWith("minute", true)) SECONDS_PER_MINUTE else 1L
        val seconds = amount?.takeIf { it in 1..MAX_TIMER_SECONDS / multiplier }?.times(multiplier)
        return listOf(entity(EntityKind.DURATION, duration,
            seconds?.let { NormalizedValue.Duration(it) }))
    }

    private companion object {
        const val POSTPONE_TIME_GROUP = 3
        const val SECONDS_PER_MINUTE = 60L
        const val MAX_TIMER_SECONDS = 86_400L
        val NEGATION = Regex("\\b(?:not|never|no|don['’]?t|do\\s+not|cancel|stop|avoid|without)\\b",
            RegexOption.IGNORE_CASE)
        val RULES = listOf(
            rule(AgentIntent.CHECK_TASKS, "(?:what tasks do i have|show (?:my )?tasks|list (?:my )?tasks)[?!.]?"),
            rule(AgentIntent.CHECK_MEMORY,
                "(?:what do you know about|search (?:my )?memory for) (?:my )?(.{1,200}?)[?!.]?"),
            rule(AgentIntent.WHAT_DID_I_PROMISE, "what did i promise (.{1,200}?)[?!.]?"),
            rule(AgentIntent.CHECK_CALENDAR, "(?:when am i free (?:today|tomorrow)|show (?:my )?calendar)[?!.]?"),
            rule(AgentIntent.CREATE_REMINDER,
                "remind me (?:(today|tomorrow|\\d{4}-\\d{2}-\\d{2}) )?at " +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)(?: to (.+?))?[!.]?"),
            rule(AgentIntent.CREATE_TASK, "(?:create|add) (?:a )?task(?: to|:) (.+?)[!.]?"),
            rule(AgentIntent.COMPLETE_TASK, "complete (?:the )?task: (.+?)"),
            rule(AgentIntent.UPDATE_TASK, "(?:rename|prioritize) task: (.+?) to: (.+?)"),
            rule(AgentIntent.DELETE_TASK, "delete task: (.+?)"),
            rule(AgentIntent.POSTPONE_TASK,
                "postpone task: (.+?) until (today|tomorrow|\\d{4}-\\d{2}-\\d{2}) at " +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)"),
            rule(AgentIntent.CREATE_TIMER, "(?:set|start) (?:a )?timer for (\\d{1,10}) (seconds?|minutes?)[!.]?"),
        )
        fun rule(intent: AgentIntent, expression: String) = LanguageRule(intent,
            Regex("\\s*(?:please )?$expression\\s*", RegexOption.IGNORE_CASE))
    }
}

private data class LanguageRule(val intent: AgentIntent, val pattern: Regex)
data class LanguageParse(
    val intent: AgentIntent,
    val entities: List<ExtractedEntity> = emptyList(),
    val negated: Boolean = false,
) {
    val prediction get() = IntentPrediction(intent, if (intent == AgentIntent.UNKNOWN) 0.0 else 1.0, negated)
}

internal fun entity(type: EntityKind, group: MatchGroup, normalized: NormalizedValue? = null) =
    ExtractedEntity(type, group.value, group.range.first, group.range.last + 1, 1.0, normalized)
