package dev.metis.agent.domain.agent

import java.time.ZoneId

/** Closed, independent task-read grammar. This builds plans only; nothing is routed or read. */
class TaskReadRequestValidatorV17 : ReadRequestValidatorV17 {
    override fun validate(request: AgentRequest, proposedResource: ReadResourceV17,
                          zoneId: ZoneId): ReadValidationResultV17 {
        fun clarify(reason: ReadClarificationReasonV17) =
            ReadValidationResultV17.Clarify(request, proposedResource, reason)
        fun reject(reason: ReadRejectionReasonV17) =
            ReadValidationResultV17.Reject(request, proposedResource, reason)

        if (proposedResource != ReadResourceV17.TASKS) return reject(ReadRejectionReasonV17.UNSUPPORTED_RESOURCE)
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
        if (AMBIGUOUS_FOR.matches(text)) return clarify(ReadClarificationReasonV17.AMBIGUOUS_SCOPE)

        val match = TASK_READ.matchEntire(text)
            ?: return reject(if (READ_START.containsMatchIn(text)) ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT
                else ReadRejectionReasonV17.UNSUPPORTED_OPERATION)
        val subjectGroup = match.groups["subject"]
        val subject = subjectGroup?.value.orEmpty()
        if (subject.isNotEmpty() && !ReadSafetyV10.validSubject(subject, subject, "topic")) {
            return if (BARE_REFERENCE.matches(subject)) clarify(ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
                else reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        }
        val status = when (match.groups["status"]?.value?.lowercase()) {
            null, "open" -> "OPEN"
            "all" -> "ANY"
            "completed" -> "COMPLETED"
            "cancelled" -> "CANCELLED"
            else -> return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        }
        val dateGroup = match.groups["date"]
        val date = dateGroup?.let {
            if (!ReadDateV15.valid(it.value)) return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
            val field = if (match.groups["dateKind"]!!.value.startsWith("completed", true))
                ReadDateV15.Field.COMPLETED else ReadDateV15.Field.DUE
            ReadDateV15(field, it.value)
        }
        if (date?.field == ReadDateV15.Field.COMPLETED && status !in setOf("OPEN", "COMPLETED"))
            return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        if (date?.field == ReadDateV15.Field.COMPLETED &&
            match.groups["status"]?.value.equals("open", ignoreCase = true))
            return reject(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        val finalStatus = if (date?.field == ReadDateV15.Field.COMPLETED) "COMPLETED" else status
        val entities = buildList {
            subjectGroup?.let { add(ExtractedEntity(EntityKind.TASK, it.value, it.range.first,
                it.range.last + 1, 1.0)) }
            dateGroup?.let { add(ExtractedEntity(EntityKind.TIME, it.value, it.range.first,
                it.range.last + 1, 1.0)) }
        }
        return ReadValidationResultV17.Valid(ValidatedReadPlanV17(request, zoneId,
            ReadScopeV15.Tasks(subject, finalStatus, date), entities))
    }

    private companion object {
        private const val MAX_REQUEST = 512
        private const val PREFIX = "\\s*(?:(?:please\\s+)?(?:can|could|would)\\s+you\\s+(?:please\\s+)?|please\\s+)?"
        private const val READ = "(?:show|list|check|find)\\s+(?:my\\s+)?"
        private const val TASKS = "(?:(?<status>open|completed|cancelled|all)\\s+)?tasks"
        private const val END = "\\s*[?!.]?\\s*"
        private const val DATE = "(?:today|tomorrow|this\\s+week|\\d{4}-\\d{2}-\\d{2})"
        private val TASK_READ = Regex("^$PREFIX$READ$TASKS" +
            "(?:\\s+(?:about|related\\s+to)\\s+(?<subject>.+?))?" +
            "(?:\\s+(?<dateKind>due(?:\\s+on)?|completed\\s+on)\\s+(?<date>$DATE))?$END",
            RegexOption.IGNORE_CASE)
        private val MISSING_SUBJECT = Regex("^$PREFIX$READ$TASKS\\s+(?:about|related\\s+to)$END",
            RegexOption.IGNORE_CASE)
        private val MISSING_DATE = Regex("^$PREFIX$READ$TASKS(?:\\s+(?:about|related\\s+to)\\s+.+?)?" +
            "\\s+(?:due(?:\\s+on)?|completed\\s+on)$END", RegexOption.IGNORE_CASE)
        private val AMBIGUOUS_FOR = Regex("^$PREFIX$READ$TASKS\\s+for(?:\\s+.+)?$END",
            RegexOption.IGNORE_CASE)
        private val READ_START = Regex("^$PREFIX$READ", RegexOption.IGNORE_CASE)
        private val WRITE_START = Regex("^\\s*(?:please\\s+)?(?:create|add|update|edit|delete|remove|" +
            "complete|finish|mark|postpone|reschedule|remind|set|start|stop)\\b", RegexOption.IGNORE_CASE)
        private val COMPOUND_WRITE = Regex("\\b(?:and|then)\\s+(?:please\\s+)?(?:create|add|" +
            "update|edit|delete|remove|complete|finish|mark|postpone|reschedule|remind|set|" +
            "start|stop)\\b", RegexOption.IGNORE_CASE)
        private val BARE_REFERENCE = Regex("(?:it|this|that|these|those|them)", RegexOption.IGNORE_CASE)
    }
}
