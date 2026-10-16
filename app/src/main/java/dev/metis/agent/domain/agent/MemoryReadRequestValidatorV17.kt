package dev.metis.agent.domain.agent

import java.time.ZoneId

/** Independent, complete-request memory grammar; constructs plans without executing reads. */
class MemoryReadRequestValidatorV17 : ReadRequestValidatorV17 {
    override fun validate(request: AgentRequest, proposedResource: ReadResourceV17,
                          zoneId: ZoneId): ReadValidationResultV17 {
        fun clarify(reason: ReadClarificationReasonV17) =
            ReadValidationResultV17.Clarify(request, proposedResource, reason)
        fun reject(reason: ReadRejectionReasonV17) =
            ReadValidationResultV17.Reject(request, proposedResource, reason)

        if (proposedResource != ReadResourceV17.MEMORY)
            return reject(ReadRejectionReasonV17.UNSUPPORTED_RESOURCE)
        if (request.source !in setOf(InputSource.TEXT, InputSource.VOICE))
            return reject(ReadRejectionReasonV17.UNSUPPORTED_INPUT_SOURCE)
        val text = request.text
        if (text.length > MAX_REQUEST || text.codePoints().anyMatch {
                Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
            }) return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        if (WRITE_START.containsMatchIn(text) || COMPOUND_WRITE.containsMatchIn(text))
            return reject(ReadRejectionReasonV17.UNSUPPORTED_OPERATION)
        if (MISSING_SUBJECT.matches(text)) return clarify(ReadClarificationReasonV17.MISSING_SUBJECT)
        if (MISSING_DATE.matches(text)) return clarify(ReadClarificationReasonV17.AMBIGUOUS_DATE)

        val match = MEMORY_READ.matchEntire(text)
            ?: return reject(if (READ_START.containsMatchIn(text)) ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT
                else ReadRejectionReasonV17.UNSUPPORTED_OPERATION)
        val subjectGroup = match.groups["subject"]!!
        val subject = subjectGroup.value
        if (WRITE_SUBJECT.containsMatchIn(subject)) return reject(ReadRejectionReasonV17.UNSUPPORTED_OPERATION)
        if (ReadPatternsV10.REFERENCE.containsMatchIn(subject) || POSSESSIVE_REFERENCE.containsMatchIn(subject))
            return clarify(ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
        // Memories have no person identity, kind, origin, or ownership filter. Do not flatten
        // such clauses into subject text and thereby change the requested query semantics.
        if (UNSUPPORTED_CLAUSE.containsMatchIn(subject) ||
            !ReadSafetyV10.validSubject(subject, subject, "topic"))
            return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        val dateGroup = match.groups["date"]
        val date = dateGroup?.let {
            if (!ReadDateV15.valid(it.value)) return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
            ReadDateV15(ReadDateV15.Field.CREATED, it.value)
        }
        val field = match.groups["field"]?.value?.lowercase().orEmpty()
        val entities = buildList {
            add(ExtractedEntity(EntityKind.MEMORY, subject, subjectGroup.range.first,
                subjectGroup.range.last + 1, 1.0))
            dateGroup?.let { add(ExtractedEntity(EntityKind.TIME, it.value, it.range.first,
                it.range.last + 1, 1.0)) }
        }
        return ReadValidationResultV17.Valid(ValidatedReadPlanV17(request, zoneId,
            ReadScopeV15.Memories(subject, date, field), entities))
    }

    private companion object {
        private const val MAX_REQUEST = 512
        private const val PREFIX = "\\s*(?:(?:please\\s+)?(?:can|could|would)\\s+you\\s+(?:please\\s+)?|please\\s+)?"
        private const val TOPIC_HEAD = "(?:(?:show|check|find)\\s+what\\s+i\\s+remember\\s+about|" +
            "(?:show|check|find)\\s+(?:my\\s+)?(?:saved\\s+)?memories\\s+about|" +
            "recall(?:\\s+(?:a|the)\\s+saved\\s+(?:fact|topic)\\s+about)?)"
        private const val FIELD_HEAD = "(?:show|check|find)\\s+(?:the\\s+)?saved\\s+" +
            "(?<field>number|email address)\\s+for"
        private const val HEAD = "(?:$TOPIC_HEAD|$FIELD_HEAD)"
        private const val DATE = "(?:today|tomorrow|this week|\\d{4}-\\d{2}-\\d{2})"
        private const val END = "\\s*[?!.]?\\s*"
        private const val WRITE = "(?:save|remember|record|store|create|add|update|change|edit|delete|forget|erase|" +
            "wipe|remove|set|prefer|keep|enable|disable)"
        private val MEMORY_READ = Regex("^$PREFIX$HEAD\\s+(?<subject>.+?)" +
            "(?:\\s+(?:saved|created)(?:\\s+on)?\\s+(?<date>$DATE))?$END", RegexOption.IGNORE_CASE)
        private val MISSING_SUBJECT = Regex("^$PREFIX(?:$HEAD|" +
            "recall\\s+(?:a|the)\\s+saved\\s+(?:fact|topic)|" +
            "(?:show|check|find)\\s+(?:my\\s+)?(?:saved\\s+)?memories|" +
            "(?:show|check|find)\\s+(?:the\\s+)?saved\\s+(?:number|email address))$END", RegexOption.IGNORE_CASE)
        private val MISSING_DATE = Regex("^$PREFIX$HEAD\\s+.+?\\s+(?:saved|created)(?:\\s+on)?$END",
            RegexOption.IGNORE_CASE)
        private val WRITE_START = Regex("^$PREFIX$WRITE\\b", RegexOption.IGNORE_CASE)
        private val COMPOUND_WRITE = Regex("(?:\\b(?:and|then|also)|[,;])\\s+(?:also\\s+)?(?:please\\s+)?$WRITE\\b",
            RegexOption.IGNORE_CASE)
        private val WRITE_SUBJECT = Regex("\\b$WRITE\\b", RegexOption.IGNORE_CASE)
        private val READ_START = Regex("^$PREFIX(?:show|check|find|recall)\\b", RegexOption.IGNORE_CASE)
        private val POSSESSIVE_REFERENCE = Regex("\\b(?:its|their|his|hers)\\b", RegexOption.IGNORE_CASE)
        private val UNSUPPORTED_CLAUSE = Regex("\\b(?:saved|created|with|type|kind|category|categories|owner|owned|person|people|" +
            "source|origin|private|shared|archived|pinned)\\b", RegexOption.IGNORE_CASE)
    }
}
