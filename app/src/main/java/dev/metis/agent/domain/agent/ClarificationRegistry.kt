package dev.metis.agent.domain.agent

import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

enum class ClarificationDomain {
    REMINDER,
    ALARM,
    CALL,
    PROMISE,
    TASK,
}

/**
 * Minimal in-memory, session-scoped pending clarification state.
 * Ephemeral (RAM only). Expires after ~2 turns or 120 seconds.
 */
data class PendingClarificationState(
    val domain: ClarificationDomain,
    val slot: String,
    val filledSlots: Map<String, String> = emptyMap(),
    val createdAt: Instant,
    val expiresAt: Instant,
    val turnsRemaining: Int = 2,
    val originalRequestId: UUID,
    val questionId: UUID = UUID.randomUUID(),
    val sessionId: UUID? = null,
    val prompt: String = "",
    val suggestedChoices: List<String> = emptyList(),
    val payload: Any? = null,
    val lastAnswerId: UUID? = null,
) {
    // Backwards compatibility helpers
    val originalIntent: AgentIntent
        get() = when (domain) {
            ClarificationDomain.REMINDER -> AgentIntent.CREATE_REMINDER
            ClarificationDomain.ALARM -> AgentIntent.CREATE_ALARM
            ClarificationDomain.CALL -> AgentIntent.CALL_PERSON
            ClarificationDomain.PROMISE -> AgentIntent.CREATE_PROMISE
            ClarificationDomain.TASK -> AgentIntent.CREATE_TASK
        }

    val missingSlot: String get() = slot

    fun isExpired(now: Instant, requestSessionId: UUID? = null): Boolean =
        turnsRemaining <= 0 || now >= expiresAt ||
            (sessionId != null && requestSessionId != null && sessionId != requestSessionId)

    fun decrementTurn(newQuestionId: UUID = UUID.randomUUID(), answerId: UUID? = null): PendingClarificationState =
        copy(turnsRemaining = turnsRemaining - 1, questionId = newQuestionId, lastAnswerId = answerId)

    @Suppress("CyclomaticComplexMethod")
    fun fitsSlot(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        val lower = clean.lowercase(java.util.Locale.ROOT)
        return when (domain) {
            ClarificationDomain.ALARM -> slot in setOf("am_pm", "period") && isAmPm(clean)
            ClarificationDomain.REMINDER -> when (slot) {
                "time" -> ReminderResolution.followUpTime(clean) != null
                "reminder_contact_choice", "reminder_contact_refine" ->
                    isChoiceOrOrdinal(clean, suggestedChoices)
                "reminder_contact_permission", "notification_permission" -> isPermissionReply(lower)
                else -> false
            }
            ClarificationDomain.CALL -> when (slot) {
                "person", "contact_choice" -> if (suggestedChoices.isNotEmpty()) {
                    isChoiceOrOrdinal(clean, suggestedChoices)
                } else lower !in PRONOUNS && clean.length <= MAX_NAME_LENGTH && isNonCommandText(clean)
                else -> false
            }
            ClarificationDomain.PROMISE -> when (slot) {
                "due_date", "date", "deadline" -> isPromiseDeadline(lower, clean)
                "person", "selection", "promise_contact_choice", "promise_contact_refine" ->
                    if (suggestedChoices.isNotEmpty()) isChoiceOrOrdinal(clean, suggestedChoices)
                    else lower !in PRONOUNS && clean.length <= MAX_NAME_LENGTH && isNonCommandText(clean)
                "project" -> if (suggestedChoices.isNotEmpty()) isChoiceOrOrdinal(clean, suggestedChoices)
                    else clean.length <= MAX_NAME_LENGTH && isNonCommandText(clean)
                "promise_contact_permission", "contact_permission" -> isPermissionReply(lower)
                else -> false
            }
            ClarificationDomain.TASK -> when (slot) {
                "project" -> if (suggestedChoices.isNotEmpty()) isChoiceOrOrdinal(clean, suggestedChoices)
                    else clean.length <= MAX_NAME_LENGTH && isNonCommandText(clean)
                else -> false
            }
        }
    }
}

private val AM_WORDS = setOf("am", "a.m.", "subah", "morning", "subah ka", "subah ki")
private val PM_WORDS = setOf(
    "pm", "p.m.", "shaam", "evening", "raat", "night", "dopahar", "afternoon", "shaam ka", "shaam ki", "raat ka"
)
private val PRONOUNS = setOf("him", "her", "them", "that one")
private val PERMISSION_WORDS = setOf(
    "skip", "no", "continue", "yes", "allow", "grant", "ok", "okay",
    "don't allow", "dont allow", "deny"
)
private val DEADLINE_NONE = setOf(
    "no deadline", "none", "skip", "no due date", "without deadline", "no date"
)
private val PROMISE_DAYS = setOf(
    "today", "tomorrow", "kal", "aaj",
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
)
private const val MAX_NAME_LENGTH = 100
private const val MAX_AM_PM_LENGTH = 50

@Suppress("ReturnCount", "MagicNumber")
private fun isAmPm(text: String): Boolean {
    val lower = text.trim().lowercase(java.util.Locale.ROOT)
    if (lower.length > MAX_AM_PM_LENGTH) return false
    if (AM_WORDS.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) return true
    if (PM_WORDS.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) return true
    val words = lower.split(Regex("\\s+"))
    return words.size <= 4 && (lower.contains(Regex("\\b(?:am|subah|morning)\\b")) ||
        lower.contains(Regex("\\b(?:pm|shaam|raat|evening|night|dopahar|afternoon)\\b")))
}

private fun isPermissionReply(lower: String): Boolean = lower in PERMISSION_WORDS

private fun isNonCommandText(text: String): Boolean {
    val req = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, Instant.EPOCH)
    return EnglishRules().parse(req).intent == AgentIntent.UNKNOWN
}

private fun isPromiseDeadline(lower: String, clean: String): Boolean =
    lower in DEADLINE_NONE || lower in PROMISE_DAYS ||
        lower.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) ||
        ReminderResolution.followUpTime(clean) != null

@Suppress("ReturnCount")
private fun isChoiceOrOrdinal(clean: String, choices: List<String>): Boolean {
    if (clean.toIntOrNull() != null) return true
    val lower = clean.lowercase(java.util.Locale.ROOT)
    val ordinals = setOf("first", "1st", "second", "2nd", "third", "3rd", "fourth", "4th", "fifth", "5th")
    if (lower in ordinals) return true
    return choices.any { it.equals(clean, ignoreCase = true) }
}

@Suppress("ReturnCount", "MagicNumber")
fun parseChoiceIndex(text: String, choices: List<String>): Int? {
    val clean = text.trim().lowercase(java.util.Locale.ROOT)
    clean.toIntOrNull()?.let { num ->
        if (num in 1..choices.size) return num - 1
    }
    val ordinals = mapOf(
        "first" to 0, "1st" to 0,
        "second" to 1, "2nd" to 1,
        "third" to 2, "3rd" to 2,
        "fourth" to 3, "4th" to 3,
        "fifth" to 4, "5th" to 4,
    )
    ordinals[clean]?.let { idx ->
        if (idx in choices.indices) return idx
    }
    val exactIdx = choices.indexOfFirst { it.equals(text.trim(), ignoreCase = true) }
    if (exactIdx >= 0) return exactIdx
    return null
}

/**
 * Session-scoped registry at orchestrator level holding at most ONE active pending clarification.
 * Agents request a slot and receive the reply; they do not hold their own state.
 */
class ClarificationRegistry(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val active = AtomicReference<PendingClarificationState?>(null)

    @Suppress("ReturnCount")
    fun current(): PendingClarificationState? {
        val entry = active.get() ?: return null
        if (entry.isExpired(clock.instant())) {
            active.compareAndSet(entry, null)
            return null
        }
        return entry
    }

    fun requestSlot(state: PendingClarificationState) {
        active.set(state)
    }

    fun consume(expected: PendingClarificationState): Boolean =
        active.compareAndSet(expected, null)

    fun replace(expected: PendingClarificationState, next: PendingClarificationState): Boolean =
        active.compareAndSet(expected, next)

    fun clear() {
        active.set(null)
    }

    fun clearDomain(domain: ClarificationDomain) {
        val current = active.get()
        if (current?.domain == domain) {
            active.compareAndSet(current, null)
        }
    }
}

