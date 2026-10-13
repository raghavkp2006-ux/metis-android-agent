package dev.metis.agent.domain.agent

/** Full request captures preserve literal subjects separately from UTF-16 annotations. */
object ReadGrammarV16 {
    fun plan(text: String): ReadPlanV15? {
        ReadFormsV16.forms.forEach { form ->
            form.pattern.matchEntire(text)?.let { return build(form, it) }
        }
        return ReadGrammarV15.plan(text)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod") // Each captured typed field has a fail-closed check.
    private fun build(form: ReadFormV16, match: MatchResult): ReadPlanV15? {
        val subject = group(form, match, "subject")?.value.orEmpty()
        val person = group(form, match, "person")?.value.orEmpty()
        if (subject.length > MAX_SUBJECT || (subject.isNotEmpty() && !validReference(subject, form.mode))) return null
        if (person.isNotEmpty() && !ReadSafetyV10.validSubject(person, person, "person")) return null
        val scope = when (form.kind) {
            AgentIntent.CHECK_TASKS -> ReadScopeV15.Tasks(subject, form.status, requestedField = form.field)
            AgentIntent.CHECK_MEMORY -> ReadScopeV15.Memories(subject, negative = form.negative)
            AgentIntent.WHAT_DID_I_PROMISE -> {
                if (person.isEmpty()) return null
                ReadScopeV15.Promises(person, subject, form.status, negative = form.negative)
            }
            AgentIntent.CHECK_CALENDAR -> ReadScopeV15.Calendar(subject, person, meetings = form.meetings,
                legacy = if (form.upcoming) CalendarReadScope(CalendarReadScope.Kind.MEETINGS_ABOUT_TOPIC,
                    subject = subject) else null, appointments = form.appointments)
            else -> return null
        }
        val entities = buildList {
            group(form, match, "person")?.let {
                add(ExtractedEntity(EntityKind.PERSON, it.value, it.range.first, it.range.last + 1, 1.0))
            }
            group(form, match, "subject")?.let {
                val skip = ReadPatternsV10.ARTICLE.find(it.value)!!.value.length
                if (it.value.length - skip !in 1..MAX_ANNOTATION) return null
                add(ExtractedEntity(if (form.kind == AgentIntent.CHECK_TASKS) EntityKind.TASK
                    else if (form.kind == AgentIntent.CHECK_MEMORY) EntityKind.MEMORY else EntityKind.TITLE,
                    it.value.substring(skip), it.range.first + skip, it.range.last + 1, 1.0,
                    it.value.takeIf { skip > 0 }?.let(NormalizedValue::Text)))
            }
        }
        return ReadPlanV15(LanguageParse(form.kind, entities, readScope = scope), scope)
    }

    private fun validReference(subject: String, mode: String): Boolean {
        val reference = when (mode) {
            "activity", "activity_or_plain" -> {
                val activity = ReadFormsV16.activity.matchEntire(subject)
                if (activity == null && mode == "activity") return false
                activity?.let { activityReference(subject, it.groups["object"]!!.value) } ?: subject
            }
            "revision" -> subject.replace(Regex("^topics of ", RegexOption.IGNORE_CASE), "")
            "possession" -> subject.replace(Regex("^already have ", RegexOption.IGNORE_CASE), "")
            "negative_role" -> subject.replace(Regex(" was not my responsibility$", RegexOption.IGNORE_CASE), "")
            else -> subject
        }
        return !Regex("\\b(?:afterwards?|later)\\b", RegexOption.IGNORE_CASE).containsMatchIn(reference) &&
            ReadSafetyV10.validSubject(reference, reference.replace(ReadPatternsV10.ARTICLE, ""), "topic")
    }

    private fun activityReference(subject: String, value: String): String {
        val base = if (subject.startsWith("add ", true) && value.endsWith(" to my portfolio", true))
            value.dropLast(" to my portfolio".length) else value
        val reference = base.replace(ReadFormsV16.modifier, "")
        val book = Regex("(?:(?:the|a|an|my) )?book(?: about (?<topic>.{1,200}?))?", RegexOption.IGNORE_CASE)
            .matchEntire(reference)
        return if (book != null) book.groups["topic"]?.value ?: "volume" else reference
    }

    private fun group(form: ReadFormV16, match: MatchResult, name: String) =
        if (form.pattern.pattern.contains("(?<$name>")) match.groups[name] else null

    private const val MAX_SUBJECT = 256
    private const val MAX_ANNOTATION = 200
}
