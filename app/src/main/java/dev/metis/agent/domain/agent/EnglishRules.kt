@file:Suppress("MagicNumber", "MaxLineLength", "TooManyFunctions", "CyclomaticComplexMethod", "ReturnCount")

package dev.metis.agent.domain.agent

import java.time.ZoneId

/** Closed English grammar. Scores mark rule matches, not calibrated model probabilities. */
class EnglishRules(private val readCandidate: ReadCandidate = ReadCandidate.V12,
                   private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : IntentClassifier, EntityExtractor {
    override suspend fun classify(request: AgentRequest): IntentPrediction {
        val parsed = parse(request)
        if (parsed.negated || parsed.intent != AgentIntent.UNKNOWN) return parsed.prediction
        return mlFallback(request)?.prediction ?: parsed.prediction
    }

    override suspend fun extract(request: AgentRequest): List<ExtractedEntity> {
        val parsed = parse(request)
        if (parsed.negated || parsed.intent != AgentIntent.UNKNOWN) return parsed.entities
        return mlFallback(request)?.entities ?: parsed.entities
    }

    @Suppress("ReturnCount") // Explicit safe exits before entity resolution.
    fun parse(request: AgentRequest): LanguageParse {
        if (request.source !in setOf(InputSource.TEXT, InputSource.VOICE)) {
            return LanguageParse(AgentIntent.UNSUPPORTED)
        }
        val text = request.text
        // Only complete, validated read productions can scope negation as remembered
        // content or OPEN-task state. No validator calls this classifier recursively.
        readCandidate.parse(request)?.let { return it }
        if (NEGATION.containsMatchIn(text)) return LanguageParse(AgentIntent.UNKNOWN, negated = true)
        ReadGrammarV8.parse(text)?.let { return it }
        val match = RULES.firstNotNullOfOrNull { rule -> rule.pattern.matchEntire(text)?.let { rule to it } }
            ?: return GraphReadGrammarV18.parse(request) ?: LanguageParse(AgentIntent.UNKNOWN)
        val (rule, groups) = match
        return LanguageParse(rule.intent, parsedEntities(request, rule.intent, groups))
    }

    private fun parsedEntities(request: AgentRequest, intent: AgentIntent, groups: MatchResult) = when (intent) {
            AgentIntent.CREATE_REMINDER -> ReminderResolution.extract(request, groups, zone())
            AgentIntent.CREATE_PROMISE -> PromiseResolution.extract(request, zone())
            AgentIntent.CREATE_TASK, AgentIntent.COMPLETE_TASK,
                AgentIntent.DELETE_TASK -> listOf(entity(EntityKind.TITLE, groups.groups[1]!!))
            AgentIntent.UPDATE_TASK -> listOf(entity(EntityKind.TASK, groups.groups[1]!!),
                entity(EntityKind.TITLE, groups.groups[2]!!))
            AgentIntent.POSTPONE_TASK -> listOf(entity(EntityKind.TASK, groups.groups[1]!!),
                entity(EntityKind.TIME, groups.groups[2]!!,
                    ReminderResolution.resolve(request, groups.groupValues[2],
                        groups.groupValues[POSTPONE_TIME_GROUP], zone())
                        ?.let { NormalizedValue.Time(it) }))
            AgentIntent.WHAT_SHOULD_I_DO -> decisionEntities(groups)
            AgentIntent.CHECK_MEMORY -> listOf(entity(EntityKind.MEMORY, groups.groups[1]!!))
            AgentIntent.WHAT_DID_I_PROMISE -> listOf(entity(EntityKind.PERSON, groups.groups[1]!!))
            else -> externalEntities(request, intent, groups)
    }

    private fun externalEntities(
        request: AgentRequest,
        intent: AgentIntent,
        groups: MatchResult,
    ): List<ExtractedEntity> = when (intent) {
        AgentIntent.CREATE_TIMER -> timerEntities(groups)
        AgentIntent.CREATE_ALARM -> AlarmResolution.extract(request, groups, zone())
        AgentIntent.CALL_PERSON -> if (groups.groups.size > 1) {
            listOfNotNull(groups.groups[1]?.let { entity(EntityKind.PERSON, it) })
        } else emptyList()
        AgentIntent.SEND_MESSAGE -> if (groups.groups.size > 2) {
            listOfNotNull(
                groups.groups[1]?.let { entity(EntityKind.PERSON, it) },
                groups.groups[2]?.let { entity(EntityKind.TITLE, it) }
            )
        } else emptyList()
        else -> emptyList()
    }

    private fun timerEntities(match: MatchResult): List<ExtractedEntity> {
        val duration = match.groups[1]!!
        val amount = duration.value.toLongOrNull()
        val multiplier = if (match.groupValues[2].startsWith("minute", true)) SECONDS_PER_MINUTE else 1L
        val seconds = amount?.takeIf { it in 1..MAX_TIMER_SECONDS / multiplier }?.times(multiplier)
        return listOf(entity(EntityKind.DURATION, duration,
            seconds?.let { NormalizedValue.Duration(it) }))
    }

    private fun decisionEntities(match: MatchResult) = match.groups[1]?.let { duration ->
        val seconds = duration.value.toLongOrNull()?.takeIf { it in 1..MAX_TIMER_SECONDS / SECONDS_PER_MINUTE }
            ?.times(SECONDS_PER_MINUTE)
        listOf(entity(EntityKind.DURATION, duration, seconds?.let { NormalizedValue.Duration(it) }))
    }.orEmpty()

    private fun mlFallback(request: AgentRequest): LanguageParse? {
        if (!dev.metis.agent.ml.NluIntentClassifier.ML_FALLBACK_ENABLED) return null
        val classifier = dev.metis.agent.ml.NluIntentClassifier.getOrCreate {
            try {
                val cl = javaClass.classLoader
                cl?.getResourceAsStream("nlu_intent_model.json")
                    ?: cl?.getResourceAsStream("assets/nlu_intent_model.json")
                    ?: listOf(
                        java.io.File("src/main/assets/nlu_intent_model.json"),
                        java.io.File("app/src/main/assets/nlu_intent_model.json")
                    ).firstOrNull { it.exists() }?.inputStream()
            } catch (_: Exception) {
                null
            }
        } ?: return null

        val nlu = try {
            classifier.predict(request.text)
        } catch (_: Exception) {
            return null
        }

        if (!nlu.accepted || nlu.agentIntent == AgentIntent.UNKNOWN) return null
        if (nlu.rawLabel in dev.metis.agent.ml.NluIntentClassifier.WRITE_INTENTS) {
            if (INTERROGATIVE_QUERY.containsMatchIn(request.text)) return null
        }

        val entities = extractMlEntities(request, nlu.agentIntent, zone())
        return LanguageParse(
            intent = nlu.agentIntent,
            entities = entities,
            negated = false,
            confidence = nlu.confidence.toDouble(),
            isUnderstoodByLm = true,
        )
    }

    private fun extractMlEntities(request: AgentRequest, intent: AgentIntent, zone: java.time.ZoneId): List<ExtractedEntity> {
        val text = request.text
        return when (intent) {
            AgentIntent.CREATE_TIMER -> extractMlTimer(text)
            AgentIntent.CREATE_ALARM -> extractMlAlarm(request, text, zone)
            AgentIntent.CALL_PERSON -> extractMlCall(text)
            AgentIntent.SEND_MESSAGE -> extractMlMessage(text)
            AgentIntent.CHECK_MEMORY -> extractMlMemory(text)
            AgentIntent.CREATE_TASK -> extractMlTask(text)
            AgentIntent.COMPLETE_TASK, AgentIntent.DELETE_TASK,
            AgentIntent.POSTPONE_TASK, AgentIntent.UPDATE_TASK -> extractMlTaskMutation(text)
            AgentIntent.WHAT_DID_I_PROMISE -> extractMlPerson(text)
            else -> emptyList()
        }
    }

    private fun extractMlTimer(text: String): List<ExtractedEntity> {
        val match = ML_TIMER_REGEX.find(text) ?: return emptyList()
        val amount = match.groupValues[1].toLongOrNull() ?: return emptyList()
        val unit = match.groupValues[2].lowercase(java.util.Locale.ROOT)
        val multiplier = when {
            unit.startsWith("h") || unit.startsWith("ghant") -> 3600L
            unit.startsWith("m") -> 60L
            else -> 1L
        }
        val seconds = amount * multiplier
        val norm = if (seconds in 1..MAX_TIMER_SECONDS) NormalizedValue.Duration(seconds) else null
        return listOf(ExtractedEntity(EntityKind.DURATION, match.value, match.range.first, match.range.last + 1, 1.0, norm))
    }

    private fun extractMlAlarm(request: AgentRequest, text: String, zone: java.time.ZoneId): List<ExtractedEntity> {
        val match = ML_ALARM_TIME_REGEX.find(text) ?: return emptyList()
        val hour = match.groupValues[1].toIntOrNull() ?: return emptyList()
        val minute = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val rawSuffix = match.groupValues[3].lowercase(java.util.Locale.ROOT)

        val day = when {
            Regex("(?i)\\b(?:kal|tomorrow)\\b").containsMatchIn(text) -> "tomorrow"
            Regex("(?i)\\b(?:aaj|today)\\b").containsMatchIn(text) -> "today"
            else -> null
        }

        val period = when {
            rawSuffix == "am" || rawSuffix == "pm" -> rawSuffix
            Regex("(?i)\\b(?:subah|morning)\\b").containsMatchIn(text) -> "am"
            Regex("(?i)\\b(?:shaam|raat|dopahar|evening|night|afternoon)\\b").containsMatchIn(text) -> "pm"
            hour in 1..6 -> "am"
            hour in 7..11 -> if (Regex("(?i)\\b(?:raat|night)\\b").containsMatchIn(text)) "pm" else "am"
            else -> null
        }

        val timeStr = if (period != null) "$hour:$minute $period" else "$hour:$minute"
        val resolved = AlarmResolution.resolve(request, day, timeStr, zone)
        return listOf(ExtractedEntity(
            EntityKind.TIME, match.value, match.range.first, match.range.last + 1, 1.0,
            resolved?.let { NormalizedValue.Time(it) }
        ))
    }

    private fun extractMlCall(text: String): List<ExtractedEntity> {
        val match = ML_CALL_REGEX.find(text) ?: return emptyList()
        val rawName = (match.groups[1]?.value ?: match.groups[2]?.value)?.trim() ?: return emptyList()
        val cleanName = rawName.replace(Regex("(?i)^(?:ko|to|please)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:ko|lagao|karo|kar do|karna)$"), "").trim()
        if (cleanName.isBlank()) return emptyList()
        val start = text.indexOf(cleanName).takeIf { it >= 0 } ?: match.range.first
        return listOf(ExtractedEntity(EntityKind.PERSON, cleanName, start, start + cleanName.length, 1.0))
    }

    private fun extractMlMessage(text: String): List<ExtractedEntity> {
        val match = ML_MESSAGE_REGEX.find(text) ?: return emptyList()
        val recipient = (match.groups[1]?.value ?: match.groups[3]?.value)?.trim() ?: ""
        val body = (match.groups[2]?.value ?: match.groups[4]?.value)?.trim() ?: ""
        val list = mutableListOf<ExtractedEntity>()
        if (recipient.isNotBlank()) {
            val start = text.indexOf(recipient).takeIf { it >= 0 } ?: 0
            list.add(ExtractedEntity(EntityKind.PERSON, recipient, start, start + recipient.length, 1.0))
        }
        if (body.isNotBlank()) {
            val start = text.indexOf(body).takeIf { it >= 0 } ?: 0
            list.add(ExtractedEntity(EntityKind.TITLE, body, start, start + body.length, 1.0))
        }
        return list
    }

    private fun extractMlMemory(text: String): List<ExtractedEntity> {
        val match = ML_MEMORY_REGEX.find(text) ?: return emptyList()
        val query = (match.groups[1]?.value ?: match.groups[2]?.value)?.trim() ?: return emptyList()
        if (query.isBlank()) return emptyList()
        val start = text.indexOf(query).takeIf { it >= 0 } ?: match.range.first
        return listOf(ExtractedEntity(EntityKind.MEMORY, query, start, start + query.length, 1.0))
    }

    private fun extractMlTask(text: String): List<ExtractedEntity> {
        val clean = text.replace(Regex("(?i)^(?:add (?:a )?task (?:to )?|create task (?:to )?)"), "")
            .replace(Regex("(?i)\\s+(?:wala task|task)?\\s*(?:add|create|banao|kar do)\\s*$"), "").trim()
        if (clean.isBlank()) return emptyList()
        val start = text.indexOf(clean).takeIf { it >= 0 } ?: 0
        return listOf(ExtractedEntity(EntityKind.TITLE, clean, start, start + clean.length, 1.0))
    }

    private fun extractMlTaskMutation(text: String): List<ExtractedEntity> {
        val clean = text.replace(Regex("(?i)^(?:delete|remove|hata do|scrap|complete|finish|khatam|mark completed)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:ko\\s+hata do|hata de|complete kar do|mark completed)$"), "").trim()
        val target = if (clean.isNotBlank()) clean else text.trim()
        val start = text.indexOf(target).takeIf { it >= 0 } ?: 0
        return listOf(ExtractedEntity(EntityKind.TITLE, target, start, start + target.length, 1.0))
    }

    private fun extractMlPerson(text: String): List<ExtractedEntity> {
        val match = Regex("(?i)(?:what did i promise|commitments? (?:to|with)|ke saath)\\s+(.+)").find(text)
        val name = match?.groupValues?.get(1)?.trim() ?: return emptyList()
        val start = text.indexOf(name).takeIf { it >= 0 } ?: match.range.first
        return listOf(ExtractedEntity(EntityKind.PERSON, name, start, start + name.length, 1.0))
    }

    private companion object {
        val INTERROGATIVE_QUERY = Regex("^(?i)\\s*(?:did|have|do|was|is|can|could|would|should)\\s+i\\b")
        val ML_TIMER_REGEX = Regex("(?i)(\\d{1,10})\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|ghante?|sec|min|hr|h|m|s)\\b")
        val ML_ALARM_TIME_REGEX = Regex("(?i)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?")
        val ML_CALL_REGEX = Regex("(?i)(?:call|dial|phone|ring)\\s+(?:to\\s+)?(.+)|(.+?)\\s*(?:ko\\s*(?:call|phone|dial)|call|dial)")
        val ML_MESSAGE_REGEX = Regex("(?i)(?:send (?:a )?message to|message|text)\\s+(.+?)\\s*(?:saying|that|with|ki|:)\\s*(.+)|(.+?)\\s*ko\\s*message\\s*(?:kar\\s*do|bhejo|karo)?\\s*(?:ki|that|saying)?\\s*(.+)")
        val ML_MEMORY_REGEX = Regex("(?i)(?:maine\\s+)?(.+?)\\s*(?:kya\\s*(?:save|likha|note)\\s*kiya\\s*tha|kya\\s*tha)|(?:what do you know about|search (?:my )?memory for|what did i save about)\\s+(.+)")
        const val POSTPONE_TIME_GROUP = 3
        const val SECONDS_PER_MINUTE = 60L
        const val MAX_TIMER_SECONDS = 86_400L
        val NEGATION = Regex("\\b(?:not|never|no|don['’]?t|do\\s+not|cancel|stop|avoid|without)\\b",
            RegexOption.IGNORE_CASE)
        val RULES = listOf(
            rule(AgentIntent.PLAN_DAY, dev.metis.agent.domain.planner.PlannerWindow.EXPRESSION),
            rule(AgentIntent.WHAT_SHOULD_I_DO,
                "what should i do(?:(?: now| next)| with (\\d{1,10}) minutes?)?[?!.]?"),
            rule(AgentIntent.CHECK_TASKS, "(?:what tasks do i have|show (?:my )?tasks|list (?:my )?tasks)[?!.]?"),
            rule(AgentIntent.CHECK_MEMORY,
                "(?:what do you know about|search (?:my )?memory for|" +
                    "what did i (?:write(?: down)?|note|save) about) (?:my )?(.{1,200}?)[?!.]?"),
            rule(AgentIntent.WHAT_DID_I_PROMISE, "what did i promise (.{1,200}?)[?!.]?"),
            rule(AgentIntent.CREATE_PROMISE, "i (?:promised|owe)(?: .*)?[!.]?"),
            rule(AgentIntent.CREATE_PROMISE, "i told(?: .*)?[!.]?"),
            rule(AgentIntent.CREATE_PROMISE, "promise(?: .*)?[!.]?"),
            rule(AgentIntent.CHECK_CALENDAR, "(?:when am i free (?:today|tomorrow)|show (?:my )?calendar)[?!.]?"),
            rule(AgentIntent.CREATE_REMINDER,
                "remind me (?:(?:on )?(today|tomorrow|\\d{4}-\\d{2}-\\d{2}|" +
                    "monday|tuesday|wednesday|thursday|friday|saturday|sunday) )?at " +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)(?: to (.+?))?[!.]?"),
            rule(AgentIntent.CREATE_REMINDER,
                "remind me (?:(?:on )?(today|tomorrow|\\d{4}-\\d{2}-\\d{2}|" +
                    "monday|tuesday|wednesday|thursday|friday|saturday|sunday) )?to (.+?)[!.]?"),
            rule(AgentIntent.CREATE_TASK, "(?:create|add) (?:a )?task(?: to|:) (.+?)[!.]?"),
            rule(AgentIntent.COMPLETE_TASK, "complete (?:the )?task: (.+?)"),
            rule(AgentIntent.COMPLETE_TASK, "complete (that|it)"),
            rule(AgentIntent.UPDATE_TASK, "(?:rename|prioritize) task: (.+?) to: (.+?)"),
            rule(AgentIntent.DELETE_TASK, "delete task: (.+?)"),
            rule(AgentIntent.DELETE_TASK, "delete (that|it)"),
            rule(AgentIntent.POSTPONE_TASK,
                "postpone task: (.+?) until (today|tomorrow|\\d{4}-\\d{2}-\\d{2}) at " +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)"),
            rule(AgentIntent.POSTPONE_TASK,
                "postpone (that|it) until (today|tomorrow|\\d{4}-\\d{2}-\\d{2}) at " +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)"),
            rule(AgentIntent.CREATE_TIMER, "(?:set|start) (?:a )?timer for (\\d{1,10}) (seconds?|minutes?)[!.]?"),
            rule(AgentIntent.CREATE_ALARM,
                "(?:set|start) (?:an? )?alarm (?:(?:for|at) )?(?:(?:on )?(today|tomorrow) )?(?:at )?" +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)(?: (today|tomorrow))?" +
                    "(?: (?:for|called|labeled|named) (.+?))?[!.]?"),
            rule(AgentIntent.CREATE_ALARM,
                "wake me(?: up)? (?:(?:on )?(today|tomorrow) )?(?:at )?" +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)(?: (today|tomorrow))?" +
                    "(?: (?:for|called|labeled|named) (.+?))?[!.]?"),
            rule(AgentIntent.CALL_PERSON, "(?:call|dial|phone)(?: (.+?))?[!.]?"),
            rule(AgentIntent.SEND_MESSAGE,
                "(?:send (?:a )?message to|message|text) (.+?)(?::\\s*|\\s+that\\s+|\\s+saying\\s+)([\\s\\S]+?)[!.]?"),
            rule(AgentIntent.SEND_MESSAGE, "(?:send (?:a )?message|message|text)[!.]?"),
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
    val readScope: ReadScopeV15? = null,
    val confidence: Double = if (intent == AgentIntent.UNKNOWN) 0.0 else 1.0,
    val isUnderstoodByLm: Boolean = false,
) {
    val prediction get() = IntentPrediction(intent, confidence, negated, isUnderstoodByLm)
}

internal fun entity(type: EntityKind, group: MatchGroup, normalized: NormalizedValue? = null) =
    ExtractedEntity(type, group.value, group.range.first, group.range.last + 1, 1.0, normalized)
