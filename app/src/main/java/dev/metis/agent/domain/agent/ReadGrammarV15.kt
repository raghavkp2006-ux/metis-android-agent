package dev.metis.agent.domain.agent

/** Full productions capture fields before validating them; no corpus IDs or example lookup. */
object ReadGrammarV15 {
    @Suppress("ReturnCount") // Fail closed on a reserved but invalid new production before legacy fallback.
    fun plan(text: String): ReadPlanV15? {
        ReadFormsV15.forms.forEach { form ->
            val match = form.pattern.matchEntire(text) ?: return@forEach
            return build(form, match)
        }
        if (ReadFormsV15.reserved.containsMatchIn(text)) return null
        val parsed = ReadGrammarV14.parse(text) ?: return null
        val scope = when (parsed.intent) {
            AgentIntent.CHECK_TASKS -> ReadScopeV15.Tasks()
            AgentIntent.CHECK_MEMORY -> ReadScopeV15.Memories(parsed.entities.single().let {
                (it.normalizedValue as? NormalizedValue.Text)?.value ?: it.rawValue
            })
            AgentIntent.WHAT_DID_I_PROMISE -> ReadScopeV15.Promises(parsed.entities.single().rawValue)
            AgentIntent.CHECK_CALENDAR -> ReadScopeV15.Calendar(legacy = CalendarReadScope.parse(text))
            else -> return null
        }
        return ReadPlanV15(parsed.copy(readScope = scope), scope)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod") // Each typed production validates all its independent fields.
    private fun build(form: ReadFormV15, match: MatchResult): ReadPlanV15? {
        val subject = group(form, match, "subject")?.value.orEmpty()
        val person = group(form, match, "person")?.value.orEmpty()
        val dateText = group(form, match, "date")?.value.orEmpty()
        val status = group(form, match, "status")?.value?.uppercase() ?: form.status
        if (person.isNotEmpty() && !ReadSafetyV10.validSubject(person, person, "person")) return null
        if (subject.isNotEmpty() && !(if (form.content) validContent(subject, person, form.negative)
                else ReadSafetyV10.validSubject(subject, subject.replace(ReadPatternsV10.ARTICLE, ""),
                    "topic"))) return null
        if (subject.length > MAX_SUBJECT) return null
        val date = if (dateText.isEmpty()) null else {
            if (!ReadDateV15.valid(dateText)) return null
            ReadDateV15(requireNotNull(form.dateField), dateText)
        }
        val scope: ReadScopeV15 = when (form.kind) {
            AgentIntent.CHECK_TASKS -> {
                if (status !in setOf("ANY", "OPEN", "COMPLETED", "CANCELLED")) return null
                ReadScopeV15.Tasks(subject, status, date, form.requestedField)
            }
            AgentIntent.WHAT_DID_I_PROMISE -> {
                if (person.isEmpty() || status !in setOf("ANY", "OPEN", "FULFILLED", "CANCELLED")) return null
                ReadScopeV15.Promises(person, subject, status, date, form.negative)
            }
            AgentIntent.CHECK_MEMORY -> ReadScopeV15.Memories(subject, date, form.requestedField, form.negative)
            AgentIntent.CHECK_CALENDAR -> ReadScopeV15.Calendar(subject, person, date, form.meetings)
            else -> return null
        }
        val entities = buildList {
            group(form, match, "person")?.let { add(entity(EntityKind.PERSON, it)) }
            group(form, match, "subject")?.let { group ->
                val skip = ReadPatternsV10.ARTICLE.find(group.value)!!.value.length
                val start = group.range.first + skip
                if (group.value.length - skip !in 1..MAX_ANNOTATION) return null
                add(ExtractedEntity(if (form.kind == AgentIntent.CHECK_TASKS) EntityKind.TASK
                    else if (form.kind == AgentIntent.CHECK_MEMORY) EntityKind.MEMORY else EntityKind.TITLE,
                    group.value.substring(skip), start, group.range.last + 1, 1.0,
                    group.value.takeIf { skip > 0 }?.let(NormalizedValue::Text)))
            }
            group(form, match, "date")?.let { add(entity(EntityKind.TIME, it)) }
            group(form, match, "field")?.let { add(entity(EntityKind.TITLE, it)) }
        }
        val parsed = LanguageParse(form.kind, entities, readScope = scope)
        return ReadPlanV15(parsed, scope)
    }

    private fun validContent(subject: String, person: String, negative: Boolean): Boolean {
        val match = ReadFormsV15.content.matchEntire(subject) ?: return false
        val raw = match.groups["object"]!!.value
        val reference = if (person.isNotEmpty()) raw.replace(Regex("^(?:her|his|their) ",
            RegexOption.IGNORE_CASE), "") else raw
        return (match.groups["negative"] != null) == negative &&
            !Regex("\\b(?:afterwards?|later)\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) &&
            ReadSafetyV10.validSubject(reference, reference.replace(ReadPatternsV10.ARTICLE, ""), "topic")
    }

    private fun group(form: ReadFormV15, match: MatchResult, name: String): MatchGroup? =
        if (form.pattern.pattern.contains("(?<$name>")) match.groups[name] else null

    private const val MAX_SUBJECT = 256
    private const val MAX_ANNOTATION = 200
}
