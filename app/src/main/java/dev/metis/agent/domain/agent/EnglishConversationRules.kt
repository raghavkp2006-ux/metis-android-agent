package dev.metis.agent.domain.agent

import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.Locale

internal const val REFERENCE_TURN_LIMIT = 3

/** Ephemeral typed mentions. Values are never included in diagnostic strings. */
internal class ReferenceTurn(val sessionId: UUID, val at: Instant, val mentions: List<ReferenceMention>) {
    override fun toString() = "ReferenceTurn(redacted)"
}

internal class ReferenceMention(val person: Boolean, val value: String, val task: Boolean = !person) {
    override fun toString() = "ReferenceMention(redacted)"
}

internal class ReferenceQuestion(val request: AgentRequest, val pronoun: String, val choices: List<String>) {
    val person = pronoun.lowercase(Locale.ROOT) in setOf("him", "her", "them")
    val question get() = if (person) "Who do you mean?" else "Which item do you mean?"
    override fun toString() = "ReferenceQuestion(redacted)"
}

internal class ConversationInterpretation(val requests: List<AgentRequest>, val tooMany: Boolean = false)

/** Preparation only: every resulting request still goes through the existing action and policy path. */
internal object EnglishConversationRules {
    private val boundary = Regex("(?:\\s+and\\s+|,\\s*then\\s+)" +
        "(?=(?:remind|add|create|complete|postpone|delete|promise)\\b)",
        RegexOption.IGNORE_CASE)
    private val pronouns = Regex("\\b(him|her|them|that|it)\\b", RegexOption.IGNORE_CASE)
    private const val REFERENCE_MINUTES = 10L
    private const val MAX_REFERENCE_TEXT = 100
    private val expiry = Duration.ofMinutes(REFERENCE_MINUTES)

    fun split(request: AgentRequest): ConversationInterpretation {
        val boundaries = boundary.findAll(request.text).filter { match ->
            request.text.take(match.range.first).count { it == '"' || it == '“' || it == '”' } % 2 == 0
        }.toList()
        if (boundaries.size > 1) return ConversationInterpretation(emptyList(), tooMany = true)
        val parts = boundaries.singleOrNull()?.let {
            listOf(request.text.substring(0, it.range.first), request.text.substring(it.range.last + 1))
        } ?: listOf(request.text)
        return ConversationInterpretation(if (parts.any { it.isBlank() }) listOf(request)
            else parts.map { request.copy(text = it.trim()) })
    }

    fun question(request: AgentRequest, history: List<ReferenceTurn>): ReferenceQuestion? {
        val pronoun = referencePronoun(request) ?: return null
        val person = pronoun.lowercase(Locale.ROOT) in setOf("him", "her", "them")
        val taskRequired = EnglishRules().parse(request).intent in TASK_MUTATIONS
        val recent = history.takeLast(REFERENCE_TURN_LIMIT).filter {
            it.sessionId == request.conversationId && !it.at.isAfter(request.timestamp) &&
                Duration.between(it.at, request.timestamp) < expiry
        }
        val choices = recent.lastOrNull { it.mentions.any { mention -> mention.person == person } }?.mentions.orEmpty()
            .filter { it.person == person && (!taskRequired || it.task) }.map { it.value }.distinct()
        return ReferenceQuestion(request, pronoun, choices)
    }

    fun resolve(question: ReferenceQuestion, answer: String): AgentRequest? {
        val selected = answer.toIntOrNull()?.let { question.choices.getOrNull(it - 1) }
            ?: question.choices.singleOrNull { it.equals(answer.trim(), true) }
            ?: answer.trim().takeIf { question.choices.isEmpty() && it.isNotBlank() &&
                it.length <= MAX_REFERENCE_TEXT && it.toIntOrNull() == null && it.none(Char::isISOControl) &&
                !pronouns.containsMatchIn(it) &&
                !boundary.containsMatchIn(it) }
        return selected?.let { value ->
            val text = pronouns.replace(question.request.text) {
                if (it.value.equals(question.pronoun, true)) value else it.value
            }
            val shortTask = Regex("^(complete|delete|postpone)\\s+(?:that|it)\\b", RegexOption.IGNORE_CASE)
                .find(question.request.text)
            question.request.copy(text = if (shortTask == null) text else
                text.replaceFirst(Regex("^(complete|delete|postpone)\\s+", RegexOption.IGNORE_CASE), "$1 task: "))
        }
    }

    fun mentions(request: AgentRequest): List<ReferenceMention> {
        val parsed = EnglishRules().parse(request)
        if (parsed.intent in setOf(AgentIntent.UNKNOWN, AgentIntent.UNSUPPORTED) || parsed.negated) return emptyList()
        val names = parsed.entities.filter { it.type == EntityKind.PERSON }.map { it.rawValue } +
            if (parsed.intent in setOf(AgentIntent.CREATE_REMINDER, AgentIntent.CREATE_TASK)) parsed.entities
                .firstOrNull { it.type == EntityKind.TITLE }?.rawValue?.let(ReminderPersonName::fromTitle)
                ?.let(::listOf).orEmpty() else emptyList()
        val items = parsed.entities.filter { it.type in setOf(EntityKind.TASK, EntityKind.TITLE) }
            .filterNot { pronouns.containsMatchIn(it.rawValue) }
            .map { ReferenceMention(false, it.rawValue, it.type == EntityKind.TASK ||
                parsed.intent in TASK_MUTATIONS + setOf(AgentIntent.CREATE_TASK, AgentIntent.CHECK_TASKS)) }
        return names.filterNot(pronouns::containsMatchIn).map { ReferenceMention(true, it) } +
            items
    }

    fun proposalMentions(result: AgentResult): List<ReferenceMention> = result.proposals.flatMap {
        when (val action = it.action) {
            is CreatePromiseAction -> listOf(ReferenceMention(true, action.personName),
                ReferenceMention(false, action.description, task = false))
            is CreateReminderAction -> listOfNotNull(
                ReminderPersonName.fromTitle(action.title)?.let { name -> ReferenceMention(true, name) },
                ReferenceMention(false, action.title, task = false))
            is TaskAction -> (action.mutation as? TaskMutation.Create)?.let { value ->
                listOf(ReferenceMention(false, value.title))
            }.orEmpty()
            else -> emptyList()
        }
    }.filterNot { pronouns.containsMatchIn(it.value) }

    fun withProposalMentions(mentions: List<ReferenceMention>, result: AgentResult): List<ReferenceMention> {
        val canonical = proposalMentions(result)
        return mentions.filter { mention -> canonical.none { it.person == mention.person } } + canonical
    }

    /** Resolve only typed slots, not incidental words inside arbitrary task or reminder text. */
    private fun referencePronoun(request: AgentRequest): String? {
        pronouns.matchEntire(request.text.trim())?.let { return it.value }
        val parsed = EnglishRules().parse(request)
        val person = parsed.entities.firstOrNull { it.type == EntityKind.PERSON }?.rawValue
            ?: if (parsed.intent in setOf(AgentIntent.CREATE_REMINDER, AgentIntent.CREATE_TASK)) parsed.entities
                .firstOrNull { it.type == EntityKind.TITLE }?.rawValue?.let(ReminderPersonName::fromTitle) else null
        val item = parsed.entities.firstOrNull { it.type in setOf(EntityKind.TASK, EntityKind.TITLE) }?.rawValue
        return person?.takeIf { it.lowercase(Locale.ROOT) in setOf("him", "her", "them") }
            ?: item?.takeIf { it.lowercase(Locale.ROOT) in setOf("that", "it") }
            ?: if (parsed.intent in setOf(AgentIntent.CREATE_REMINDER, AgentIntent.CREATE_TASK)) item?.let {
                Regex("^do (that|it)$", RegexOption.IGNORE_CASE).matchEntire(it)?.groupValues?.get(1)
            } else null
    }

    private val TASK_MUTATIONS = setOf(AgentIntent.COMPLETE_TASK, AgentIntent.DELETE_TASK,
        AgentIntent.POSTPONE_TASK, AgentIntent.UPDATE_TASK)

    fun isBareReference(text: String) = pronouns.matches(text.trim())
}
