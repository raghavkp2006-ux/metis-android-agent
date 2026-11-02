@file:Suppress("MagicNumber", "MaxLineLength", "TooManyFunctions", "CyclomaticComplexMethod", "ReturnCount")

package dev.metis.agent.domain.agent

import java.time.LocalDateTime
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
        if (isNegated(text)) return LanguageParse(AgentIntent.UNKNOWN, negated = true)
        ReadGrammarV8.parse(text)?.let { return it }
        if (isBareHinglishReminder(text)) return LanguageParse(AgentIntent.UNKNOWN)
        val match = RULES.firstNotNullOfOrNull { rule -> rule.pattern.matchEntire(text)?.let { rule to it } }
            ?: return GraphReadGrammarV18.parse(request) ?: LanguageParse(AgentIntent.UNKNOWN)
        val (rule, groups) = match
        return LanguageParse(rule.intent, parsedEntities(request, rule.intent, groups, rule.pattern.pattern))
    }

    private fun parsedEntities(
        request: AgentRequest,
        intent: AgentIntent,
        groups: MatchResult,
        patternStr: String = "",
    ) = when (intent) {
        AgentIntent.CREATE_REMINDER -> extractReminderEntities(request, groups, patternStr)
        AgentIntent.CREATE_PROMISE -> PromiseResolution.extract(request, zone())
        AgentIntent.CREATE_TASK, AgentIntent.COMPLETE_TASK,
        AgentIntent.DELETE_TASK -> extractTaskEntities(groups)
        AgentIntent.UPDATE_TASK -> extractUpdateEntities(groups)
        AgentIntent.POSTPONE_TASK -> extractPostponeEntities(request, groups)
        AgentIntent.WHAT_SHOULD_I_DO -> decisionEntities(groups)
        AgentIntent.CHECK_MEMORY -> listOf(entity(EntityKind.MEMORY, groups.groups[1]!!))
        AgentIntent.WHAT_DID_I_PROMISE -> listOf(entity(EntityKind.PERSON, groups.groups[1]!!))
        else -> externalEntities(request, intent, groups)
    }

    private fun extractReminderEntities(
        request: AgentRequest,
        match: MatchResult,
        patternStr: String = "",
    ): List<ExtractedEntity> {
        val text = request.text
        if (patternStr.contains("remind me in", ignoreCase = true)) {
            val durationGroup = match.groups[1]
            val titleGroup = if (match.groups.size > 2) match.groups[2] else null
            if (durationGroup != null) {
                val sec = DurationParser.parseToSeconds(durationGroup.value)
                val resolved = sec?.let {
                    val instant = request.timestamp.plusSeconds(it)
                    val local = LocalDateTime.ofInstant(instant, zone())
                    ResolvedTime(instant, local, zone())
                }
                return listOfNotNull<ExtractedEntity>(
                    entity(EntityKind.TIME, durationGroup, resolved?.let { NormalizedValue.Time(it) }),
                    titleGroup?.let { entity(EntityKind.TITLE, it) }
                )
            }
        }
        if (patternStr.contains("to (.+?) at", ignoreCase = true) && match.groups.size >= 4) {
            val dayGroup = match.groups[1]
            val titleGroup = match.groups[2]
            val timeGroup = match.groups[3]
            val resolved = timeGroup?.let { ReminderResolution.resolve(request, dayGroup?.value, it.value, zone()) }
            return listOfNotNull<ExtractedEntity>(
                timeGroup?.let { entity(EntityKind.TIME, it, resolved?.let { res -> NormalizedValue.Time(res) }) },
                titleGroup?.let { entity(EntityKind.TITLE, it) }
            )
        }
        if (patternStr.contains("yaad", ignoreCase = true) || patternStr.contains("remind\\s*kar", ignoreCase = true)) {
            val hinglishTime = HINGLISH_REMINDER_TIME_REGEX.find(text)
            if (hinglishTime != null) {
                val dayStr = hinglishTime.groups[1]?.value
                val timeStr = hinglishTime.groups[2]?.value ?: "08:00"
                val resolved = ReminderResolution.resolve(request, dayStr, timeStr, zone())
                val timeStart = hinglishTime.range.first
                val timeEnd = hinglishTime.range.last + 1
                val timeEntity = ExtractedEntity(EntityKind.TIME, text.substring(timeStart, timeEnd), timeStart, timeEnd, 1.0, resolved?.let { NormalizedValue.Time(it) })
                val rawGroup = (1 until match.groups.size).mapNotNull { match.groups[it] }.firstOrNull()
                val titleEntity = rawGroup?.let {
                    ExtractedEntity(EntityKind.TITLE, it.value, it.range.first, it.range.last + 1, 1.0)
                }
                return listOfNotNull<ExtractedEntity>(timeEntity, titleEntity)
            }
            val rawGroup = (1 until match.groups.size).mapNotNull { match.groups[it] }.firstOrNull()
            if (rawGroup != null) {
                return listOf(ExtractedEntity(EntityKind.TITLE, rawGroup.value, rawGroup.range.first, rawGroup.range.last + 1, 1.0))
            }
        }
        return ReminderResolution.extract(request, match, zone())
    }

    private fun extractTaskEntities(groups: MatchResult): List<ExtractedEntity> {
        val nonNullGroup = (1 until groups.groups.size).mapNotNull { groups.groups[it] }.firstOrNull()
            ?: return emptyList()
        val start = nonNullGroup.range.first
        val end = nonNullGroup.range.last + 1
        return listOf(ExtractedEntity(EntityKind.TITLE, nonNullGroup.value, start, end, 1.0))
    }

    private fun extractUpdateEntities(groups: MatchResult): List<ExtractedEntity> {
        val nonNullGroups = (1 until groups.groups.size).mapNotNull { groups.groups[it] }
        if (nonNullGroups.size >= 2) {
            val taskGroup = nonNullGroups[0]
            val titleGroup = nonNullGroups[1]
            return listOf(
                entity(EntityKind.TASK, taskGroup),
                entity(EntityKind.TITLE, titleGroup)
            )
        }
        return emptyList()
    }

    private fun extractPostponeEntities(request: AgentRequest, groups: MatchResult): List<ExtractedEntity> {
        val nonNullGroups = (1 until groups.groups.size).mapNotNull { groups.groups[it] }
        if (nonNullGroups.size >= 2 && groups.groups.size > POSTPONE_TIME_GROUP && groups.groups[POSTPONE_TIME_GROUP] != null) {
            return listOf(
                entity(EntityKind.TASK, groups.groups[1]!!),
                entity(EntityKind.TIME, groups.groups[2]!!,
                    ReminderResolution.resolve(request, groups.groupValues[2],
                        groups.groupValues[POSTPONE_TIME_GROUP], zone())
                        ?.let { NormalizedValue.Time(it) })
            )
        }
        if (nonNullGroups.isNotEmpty()) {
            val taskGroup = nonNullGroups[0]
            val timeGroup = nonNullGroups.getOrNull(1)
            val timeString = timeGroup?.value ?: "tomorrow"
            val resolved = ReminderResolution.resolveDate(request, timeString, zone())?.let { date ->
                val time = ReminderResolution.resolveOnDate(request, date, "09:00", zone())
                    ?: ResolvedTime(date.atTime(9, 0).atZone(zone()).toInstant(), date.atTime(9, 0), zone())
                NormalizedValue.Time(time)
            }
            val timeEntity = timeGroup?.let { entity(EntityKind.TIME, it, resolved) } ?: run {
                val start = request.text.indexOf(timeString).takeIf { it >= 0 } ?: 0
                ExtractedEntity(EntityKind.TIME, timeString, start, start + timeString.length, 1.0, resolved)
            }
            return listOf(entity(EntityKind.TASK, taskGroup), timeEntity)
        }
        return emptyList()
    }

    private fun externalEntities(
        request: AgentRequest,
        intent: AgentIntent,
        groups: MatchResult,
    ): List<ExtractedEntity> = when (intent) {
        AgentIntent.CREATE_TIMER -> timerEntities(groups)
        AgentIntent.CREATE_ALARM -> AlarmResolution.extract(request, groups, zone())
        AgentIntent.CALL_PERSON -> {
            val raw = (1 until groups.groups.size).mapNotNull { groups.groups[it] }.firstOrNull()?.value?.trim().orEmpty()
            val clean = raw.replace(Regex("(?i)^(?:ko|to|please)\\s+"), "")
                .replace(Regex("(?i)\\s*(?:ko\\s*(?:call\\s*karo|phone\\s*laga\\s*do|call\\s*laga\\s*do|call|phone)|call\\s*karo|phone\\s*laga\\s*do|call\\s*laga\\s*do)$"), "")
                .trim()
            if (clean.isNotBlank()) {
                val start = request.text.indexOf(clean).takeIf { it >= 0 } ?: 0
                listOf(ExtractedEntity(EntityKind.PERSON, clean, start, start + clean.length, 1.0))
            } else emptyList()
        }
        AgentIntent.SEND_MESSAGE -> {
            val nonNull = (1 until groups.groups.size).mapNotNull { groups.groups[it] }
            if (nonNull.size >= 2) {
                val recipient = nonNull[0].value.trim().replace(Regex("(?i)\\s*ko$"), "")
                val body = nonNull[1].value.trim()
                val list = mutableListOf<ExtractedEntity>()
                if (recipient.isNotBlank()) {
                    val start = request.text.indexOf(recipient).takeIf { it >= 0 } ?: 0
                    list.add(ExtractedEntity(EntityKind.PERSON, recipient, start, start + recipient.length, 1.0))
                }
                if (body.isNotBlank()) {
                    val start = request.text.indexOf(body).takeIf { it >= 0 } ?: 0
                    list.add(ExtractedEntity(EntityKind.TITLE, body, start, start + body.length, 1.0))
                }
                list
            } else emptyList()
        }
        else -> emptyList()
    }

    private fun timerEntities(match: MatchResult): List<ExtractedEntity> {
        if (match.groups.size > 2 && match.groups[2] != null) {
            val duration = match.groups[1]!!
            val amount = duration.value.toLongOrNull()
            val multiplier = if (match.groupValues[2].startsWith("minute", true)) SECONDS_PER_MINUTE else 1L
            val seconds = amount?.takeIf { it in 1..MAX_TIMER_SECONDS / multiplier }?.times(multiplier)
            return listOf(entity(EntityKind.DURATION, duration,
                seconds?.let { NormalizedValue.Duration(it) }))
        }
        val duration = match.groups[1]!!
        val seconds = DurationParser.parseToSeconds(duration.value)
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
            if (isInterrogative(request.text)) return null
        }
        if (nlu.rawLabel in dev.metis.agent.ml.NluIntentClassifier.DESTRUCTIVE_INTENTS) {
            if (isCompoundReadPlusAction(request.text)) return null
        }
        if (nlu.rawLabel in setOf("CREATE_PROMISE", "CREATE_REMINDER")) {
            if (isRetrospectiveNarrative(request.text)) return null
        }
        if (nlu.rawLabel == "RENAME_TASK") {
            if (isNounPhraseMemoryQuery(request.text)) return null
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
        val clean = text.replace(Regex("(?i)^(?:delete|delet|remove|hata do|scrap|complete|finish|khatam|mark completed|move|postpone|reschedule|mark|make|rename)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:ko\\s+hata do|hata de|complete kar do|mark completed|to\\s+(?:tomorrow|today|next week)|until\\s+(?:tomorrow|today)|done|as\\s+done)$"), "").trim()
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

    companion object {
        fun isInterrogative(text: String): Boolean =
            TRAILING_QUESTION.containsMatchIn(text) ||
                AUX_PRONOUN.containsMatchIn(text) ||
                POLITE_PREFIX.containsMatchIn(text) ||
                EMBEDDED_INQUIRY.containsMatchIn(text)

        fun isCompoundReadPlusAction(text: String): Boolean =
            STARTS_WITH_READ_VERB.containsMatchIn(text) ||
                CHAINED_DESTRUCTIVE_CLAUSE.containsMatchIn(text)

        fun isRetrospectiveNarrative(text: String): Boolean =
            RETROSPECTIVE_NARRATIVE.containsMatchIn(text) && !IMPERATIVE_VERB.containsMatchIn(text)

        fun isNounPhraseMemoryQuery(text: String): Boolean =
            THE_NAME_OF.containsMatchIn(text)

        private val RETROSPECTIVE_NARRATIVE = Regex(
            """^\s*(?:the|that|my)\s+(?:commitment|promise|plan)\s+(?:here\s+)?(?:was|had\s+been)\b|""" +
                """^\s*i\s+(?:had\s+)?(?:committed|promised|agreed|intended|planned|was\s+supposed)\b""", 
            RegexOption.IGNORE_CASE
        )
        private val IMPERATIVE_VERB = Regex(
            """\b(?:remind|note|record|save|set|add|make|put)\b""", 
            RegexOption.IGNORE_CASE
        )
        private val THE_NAME_OF = Regex(
            """^\s*the\s+name\s+of\b""", 
            RegexOption.IGNORE_CASE
        )

        private val TRAILING_QUESTION = Regex("""\?[\s"'’”\u200B\uFE0F\uFFFD\u00A0<>]*$""")
        private val AUX_PRONOUN = Regex(
            """(?:^|[.?!;]\s*)(?:(?:could|can|would)\s+you(?:\s+please)?|please(?:\s+(?:can|could)\s+you)?)\s+""" +
                """(?:am|is|are|was|were|have|has|had|do|does|did|can|could|would|should)\s+(?:i|there|you|we|it|he|she|they)\b|""" +
                """(?:^|[.?!;]\s*)(?:am|is|are|was|were|have|has|had|do|does|did|can|could|would|should)\s+(?:i|there|you|we|it|he|she|they)\b""",
            RegexOption.IGNORE_CASE
        )
        private val POLITE_PREFIX = Regex(
            """(?:^|[.?!;]\s*)(?:could\s+you\s+please|can\s+you\s+please|please\s+can\s+you|please\s+could\s+you|could\s+you|can\s+you)\b""",
            RegexOption.IGNORE_CASE
        )
        private val EMBEDDED_INQUIRY = Regex(
            """\b(?:have\s+i\s+already\s+created|did\s+i\s+(?:already\s+)?(?:create|promise|save|write|agree|set)|""" +
                """was\s+there\s+a\s+task|do\s+i\s+have\s+a\s+task|have\s+i\s+agreed|""" +
                """what\s+(?:is|was|are|were|email|number|did)\b|which\s+of\s+my\b|""" +
                """where\s+(?:is|was|are|were)\b|when\s+(?:is|was|are|were|am)\b|""" +
                """how\s+(?:many|much|is|are|do|does|did)\b)""",
            RegexOption.IGNORE_CASE
        )
        private val STARTS_WITH_READ_VERB = Regex(
            """^\s*(?:(?:could\s+you\s+please|can\s+you\s+please|please\s+can\s+you|could\s+you|can\s+you|please)\s+)?""" +
                """(?:find|show|list|locate|retrieve|what|which|where|who)\b""",
            RegexOption.IGNORE_CASE
        )
        private val CHAINED_DESTRUCTIVE_CLAUSE = Regex(
            """\band\s+(?:delete|remove|clear|erase|cancel)\b""",
            RegexOption.IGNORE_CASE
        )
        val ML_TIMER_REGEX = Regex("(?i)(\\d{1,10})\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|ghante?|sec|min|hr|h|m|s)\\b")
        val ML_ALARM_TIME_REGEX = Regex("(?i)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?")
        val ML_CALL_REGEX = Regex("(?i)(?:call|dial|phone|ring)\\s+(?:to\\s+)?(.+)|(.+?)\\s*(?:ko\\s*(?:call|phone|dial)|call|dial)")
        val ML_MESSAGE_REGEX = Regex("(?i)(?:send (?:a )?message to|message|text)\\s+(.+?)\\s*(?:saying|that|with|ki|:)\\s*(.+)|(.+?)\\s*ko\\s*message\\s*(?:kar\\s*do|bhejo|karo)?\\s*(?:ki|that|saying)?\\s*(.+)")
        val ML_MEMORY_REGEX = Regex("(?i)(?:maine\\s+)?(.+?)\\s*(?:kya\\s*(?:save|likha|note)\\s*kiya\\s*tha|kya\\s*tha)|(?:what do you know about|search (?:my )?memory for|what did i save about)\\s+(.+)")
        const val POSTPONE_TIME_GROUP = 3
        const val SECONDS_PER_MINUTE = 60L
        const val MAX_TIMER_SECONDS = 86_400L

        val NEGATION = Regex(
            """\b(?:not|never|no|don['’]?t|dont|dnt|do\s+not|cancel|stop|avoid|without)\b|""" +
                """\b(?:never\s*mind|nvm|forget\s+it|forget\s+ab(?:ou)?t\s+it)\b|""" +
                """\b(?:mat\s+karo|mat\s+lagana|mat\s+delete|mat\s+delete\s+karna|mat\s+lgana|delete\s+mat|mat|mt\s+kr|mt\s+krna|delete\s+mt|mt)\b|""" +
                """\b(?:rehne\s+do|rehne\s+de|nahi\s+chahiye|nahi\s+nahi|chhod\s+yaar|kuch\s+m[at]?\s*kr|vendam|vidu)\b""",
            RegexOption.IGNORE_CASE
        )
        private val NEGATION_EXCEPTIONS = listOf(
            Regex("""^\s*(?:please\s+)?remind\s+me\b.*?\bnot\s+to\s+forget\b""", RegexOption.IGNORE_CASE),
            Regex("""^\s*(?:please\s+)?(?:don['’]?t|dont|dnt)\s+forget\s+to\b""", RegexOption.IGNORE_CASE),
        )

        fun isNegated(text: String): Boolean {
            if (NEGATION_EXCEPTIONS.any { it.containsMatchIn(text) }) return false
            return NEGATION.containsMatchIn(text)
        }

        private fun isBareHinglishReminder(text: String): Boolean =
            Regex("""^\s*(?:kal|aaj)\s+yaad\s+(?:dilao|dilana|dila\s+dena)\s*[!.]?$""", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)

        private val HINGLISH_REMINDER_TIME_REGEX = Regex(
            """(?i)\b(?:(kal|aaj)\s+)?(\d{1,2}(?::\d{2})?)\s*(?:baje)?(?:\s+(am|pm))?\b"""
        )

        private val RULES = listOf(
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

            // Reminders (Original Strict Rules First)
            rule(AgentIntent.CREATE_REMINDER,
                "remind me (?:(?:on )?(today|tomorrow|\\d{4}-\\d{2}-\\d{2}|" +
                    "monday|tuesday|wednesday|thursday|friday|saturday|sunday) )?at " +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)(?: to (.+?))?[!.]?"),
            rule(AgentIntent.CREATE_REMINDER,
                "remind me (?:(?:on )?(today|tomorrow|\\d{4}-\\d{2}-\\d{2}|" +
                    "monday|tuesday|wednesday|thursday|friday|saturday|sunday) )?to (.+?)[!.]?"),
            // In X Duration Reminder
            rule(AgentIntent.CREATE_REMINDER,
                "remind me in (.+?) to (.+?)[!.]?"),
            // Typo / inverted Reminder
            rule(AgentIntent.CREATE_REMINDER,
                "(?:remind|remnd) me (?:(?:on )?(today|tomorrow|\\d{4}-\\d{2}-\\d{2}|" +
                    "monday|tuesday|wednesday|thursday|friday|saturday|sunday) )?to (.+?)" +
                    " at (\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)[!.]?"),
            rule(AgentIntent.CREATE_REMINDER,
                "(?:don['’]?t|dont|dnt) forget to (.+?)[!.]?"),
            // Hinglish Reminder
            rule(AgentIntent.CREATE_REMINDER,
                "(.+?)\\s+(?:yaad\\s+(?:dilao|dilana|dila\\s+dena)|remind\\s*kar\\s*(?:do|de|karo))[!.]?"),
            rule(AgentIntent.CREATE_REMINDER,
                "remind\\s*kar\\s*(?:do|de|karo)\\s+(.+?)[!.]?"),
            rule(AgentIntent.CREATE_REMINDER,
                "(?:(.+?)\\s+)?yaad\\s+(?:dilao|dilana|dila\\s+dena)\\s+(.+?)[!.]?"),

            // Task Create (Original Strict Rules First)
            rule(AgentIntent.CREATE_TASK, "(?:create|add) (?:a )?task(?: to|:) (.+?)[!.]?"),
            rule(AgentIntent.CREATE_TASK, "add task (.+?)[!.]?"),
            rule(AgentIntent.CREATE_TASK,
                "(.+?)\\s+(?:(?:ek )?task\\s+bana\\s*(?:do|de|karo)|list\\s+mein\\s+daal\\s*(?:do|de|karo)|add\\s*kar\\s*(?:do|de|karo))[!.]?"),

            // Task Complete (Original Strict Rules First)
            rule(AgentIntent.COMPLETE_TASK, "complete (?:the )?task: (.+?)"),
            rule(AgentIntent.COMPLETE_TASK, "complete (that|it)"),
            rule(AgentIntent.COMPLETE_TASK,
                "(.+?)\\s*(?:task\\s*)?(?:ho\\s*gaya|kar\\s*liya|done\\s*kar\\s*(?:do|de|karo))[!.]?"),

            // Task Update & Rename & Prioritize (Original Strict Rule First)
            rule(AgentIntent.UPDATE_TASK, "(?:rename|prioritize) task: (.+?) to: (.+?)"),

            // Task Delete (Original Strict Rules First)
            rule(AgentIntent.DELETE_TASK, "delete task: (.+?)"),
            rule(AgentIntent.DELETE_TASK, "delete (that|it)"),
            rule(AgentIntent.DELETE_TASK,
                "(.+?)\\s*(?:task\\s*)?(?:hata\\s*(?:do|de|karo)|delete\\s*kar\\s*(?:do|de|karo))[!.]?"),

            // Task Postpone (Original Strict Rules First)
            rule(AgentIntent.POSTPONE_TASK,
                "postpone task: (.+?) until (today|tomorrow|\\d{4}-\\d{2}-\\d{2}) at " +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)"),
            rule(AgentIntent.POSTPONE_TASK,
                "postpone (that|it) until (today|tomorrow|\\d{4}-\\d{2}-\\d{2}) at " +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)"),

            // Timers (Original Strict Rule First)
            rule(AgentIntent.CREATE_TIMER, "(?:set|start) (?:a )?timer for (\\d{1,10}) (seconds?|minutes?)[!.]?"),
            rule(AgentIntent.CREATE_TIMER, "(?:set|start) (?:a )?timer (?:for )?(.+?)[!.]?"),
            rule(AgentIntent.CREATE_TIMER, "timer (?:for )?(.+?)[!.]?"),
            rule(AgentIntent.CREATE_TIMER, "(.+?)\\s+timer[!.]?"),

            // Alarms (Original Strict Rules First)
            rule(AgentIntent.CREATE_ALARM,
                "(?:set|start) (?:an? )?alarm (?:(?:for|at) )?(?:(?:on )?(today|tomorrow) )?(?:at )?" +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)(?: (today|tomorrow))?" +
                    "(?: (?:for|called|labeled|named) (.+?))?[!.]?"),
            rule(AgentIntent.CREATE_ALARM,
                "wake me(?: up)? (?:(?:on )?(today|tomorrow) )?(?:at )?" +
                    "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)(?: (today|tomorrow))?" +
                    "(?: (?:for|called|labeled|named) (.+?))?[!.]?"),
            rule(AgentIntent.CREATE_ALARM, "alarm (?:(?:for|at) )?(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)[!.]?"),
            rule(AgentIntent.CREATE_ALARM, "(\\d{1,2}(?::\\d{2})?(?: ?(?:am|pm))?)\\s+alarm[!.]?"),
            rule(AgentIntent.CREATE_ALARM,
                "(?:(?:kal|aaj) )?(\\d{1,2}(?::\\d{2})?)\\s*baje(?: ka)? alarm(?: (?:laga\\s*(?:do|de|karo)|set\\s*(?:kar\\s*do|karo)))?[!.]?"),
            rule(AgentIntent.CREATE_ALARM,
                "(?:(?:kal|aaj) )?(?:subah|shaam|raat|dopahar) (\\d{1,2}(?::\\d{2})?)\\s*baje\\s*utha\\s*(?:dena|do|de)[!.]?"),

            // Calls (Original Strict Rule First)
            rule(AgentIntent.CALL_PERSON, "(?:call|dial|phone)(?: (.+?))?[!.]?"),
            rule(AgentIntent.CALL_PERSON, "(.+?)\\s*(?:ko\\s*)?(?:call\\s*karo|phone\\s*laga\\s*do|call\\s*laga\\s*do)[!.]?"),

            // Messages (Original Strict Rules First)
            rule(AgentIntent.SEND_MESSAGE,
                "(?:send (?:a )?message to|message|text) (.+?)(?::\\s*|\\s+that\\s+|\\s+saying\\s+)([\\s\\S]+?)[!.]?"),
            rule(AgentIntent.SEND_MESSAGE, "(?:send (?:a )?message|message|text)[!.]?"),
            rule(AgentIntent.SEND_MESSAGE, "text (.+?)\\s+([\\s\\S]+?)[!.]?"),
            rule(AgentIntent.SEND_MESSAGE,
                "(.+?)\\s*(?:ko\\s*)?(?:message\\s*kar\\s*do|text\\s*bhej\\s*do|bata\\s*do\\s*ki)\\s+(?:ki\\s+)?([\\s\\S]+?)[!.]?"),
        )
        private fun rule(intent: AgentIntent, expression: String) = LanguageRule(intent,
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
