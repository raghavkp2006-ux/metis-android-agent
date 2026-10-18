package dev.metis.agent.domain.agent

import java.time.ZoneId

/** Closed calendar-read grammar. It constructs plans without routing or reading records. */
class CalendarReadRequestValidatorV17 : ReadRequestValidatorV17 {
    override fun validate(request: AgentRequest, proposedResource: ReadResourceV17,
                          zoneId: ZoneId): ReadValidationResultV17 {
        fun clarify(reason: ReadClarificationReasonV17) =
            ReadValidationResultV17.Clarify(request, proposedResource, reason)
        fun reject(reason: ReadRejectionReasonV17) =
            ReadValidationResultV17.Reject(request, proposedResource, reason)

        if (proposedResource != ReadResourceV17.CALENDAR)
            return reject(ReadRejectionReasonV17.UNSUPPORTED_RESOURCE)
        if (request.source !in setOf(InputSource.TEXT, InputSource.VOICE))
            return reject(ReadRejectionReasonV17.UNSUPPORTED_INPUT_SOURCE)
        val text = request.text
        if (text.length > MAX_REQUEST || text.codePoints().anyMatch {
                Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
            }) return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        if (WRITE_VERB.containsMatchIn(text))
            return reject(ReadRejectionReasonV17.UNSUPPORTED_OPERATION)
        if (MISSING_SUBJECT.matches(text)) return clarify(ReadClarificationReasonV17.MISSING_SUBJECT)
        if (MISSING_PERSON.matches(text)) return clarify(ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
        if (MISSING_DATE.matches(text) || AMBIGUOUS_DATE.containsMatchIn(text))
            return clarify(ReadClarificationReasonV17.AMBIGUOUS_DATE)

        val match = CALENDAR_READ.matchEntire(text)
            ?: return reject(if (READ_START.containsMatchIn(text)) ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT
                else ReadRejectionReasonV17.UNSUPPORTED_OPERATION)
        val subjectGroup = match.groups["subject"]
        val subject = subjectGroup?.value.orEmpty()
        if (subject.isNotEmpty() && !ReadSafetyV10.validSubject(subject, subject, "topic")) {
            return if (BARE_REFERENCE.matches(subject)) clarify(ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
                else reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        }
        val personGroup = match.groups["person"]
        val person = personGroup?.value.orEmpty()
        if (person.isNotEmpty() && !ReadSafetyV10.validSubject(person, person, "person")) {
            return if (BARE_REFERENCE.matches(person)) clarify(ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
                else reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        }
        val dateGroup = match.groups["date"] ?: match.groups["relativeDate"]
        val date = dateGroup?.let {
            if (!ReadDateV15.valid(it.value)) return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
            ReadDateV15(ReadDateV15.Field.START, it.value)
        }
        val kind = (match.groups["kind"] ?: match.groups["kindOnly"])?.value?.lowercase().orEmpty()
        val entities = buildList {
            subjectGroup?.let { add(ExtractedEntity(EntityKind.TITLE, it.value, it.range.first,
                it.range.last + 1, 1.0)) }
            personGroup?.let { add(ExtractedEntity(EntityKind.PERSON, it.value, it.range.first,
                it.range.last + 1, 1.0)) }
            dateGroup?.let { add(ExtractedEntity(EntityKind.TIME, it.value, it.range.first,
                it.range.last + 1, 1.0)) }
        }
        return ReadValidationResultV17.Valid(ValidatedReadPlanV17(request, zoneId,
            ReadScopeV15.Calendar(subject = subject, person = person, date = date,
                meetings = kind == "meetings", appointments = kind == "appointments"), entities))
    }

    private companion object {
        private const val MAX_REQUEST = 512
        private const val PREFIX = "\\s*(?:(?:please\\s+)?(?:can|could|would)\\s+you\\s+(?:please\\s+)?|please\\s+)?"
        private const val READ = "(?:show|list|check|find)\\s+(?:my\\s+)?"
        private const val RESOURCE = "(?:calendar(?:\\s+(?<kind>events|meetings|appointments))?|(?<kindOnly>events|meetings|appointments))"
        private const val DATE = "(?:today|tomorrow|this\\s+week|\\d{4}-\\d{2}-\\d{2})"
        private const val END = "\\s*[?!.]?\\s*"
        private val CALENDAR_READ = Regex("^$PREFIX$READ$RESOURCE" +
            "(?:\\s+(?:about|related\\s+to)\\s+(?<subject>.+?))?" +
            "(?:\\s+with\\s+(?<person>.+?))?" +
            "(?:\\s+(?:on|during)\\s+(?<date>$DATE)|\\s+(?<relativeDate>today|tomorrow|this\\s+week))?$END",
            RegexOption.IGNORE_CASE)
        private val MISSING_SUBJECT = Regex("^$PREFIX$READ$RESOURCE\\s+(?:about|related\\s+to)$END",
            RegexOption.IGNORE_CASE)
        private val MISSING_PERSON = Regex("^$PREFIX$READ$RESOURCE(?:\\s+(?:about|related\\s+to)\\s+.+?)?" +
            "\\s+with$END", RegexOption.IGNORE_CASE)
        private val MISSING_DATE = Regex("^$PREFIX$READ$RESOURCE(?:\\s+(?:about|related\\s+to)\\s+.+?)?" +
            "(?:\\s+with\\s+.+?)?\\s+(?:on|during)$END", RegexOption.IGNORE_CASE)
        private val AMBIGUOUS_DATE = Regex("\\b(?:next|last)\\s+(?:week|month|year|monday|tuesday|" +
            "wednesday|thursday|friday|saturday|sunday)\\b", RegexOption.IGNORE_CASE)
        private val READ_START = Regex("^$PREFIX$READ", RegexOption.IGNORE_CASE)
        private val WRITE_VERB = Regex("\\b(?:create|add|update|edit|reschedule|move|delete|remove|cancel|" +
            "book|schedule|set|remind|notify|alert)\\b", RegexOption.IGNORE_CASE)
        private val BARE_REFERENCE = Regex("(?:it|this|that|these|those|them|him|her)", RegexOption.IGNORE_CASE)
    }
}
