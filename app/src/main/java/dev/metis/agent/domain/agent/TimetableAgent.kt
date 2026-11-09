@file:Suppress(
    "ReturnCount",        // parser/dispatch style with multiple early returns
    "MaxLineLength",      // long regex/string literals
    "MagicNumber",        // hour/minute arithmetic constants (12, 6, 59, etc.)
    "UnusedParameter",    // resolveTargetMinute may not use all params in future stubs
)

package dev.metis.agent.domain.agent

import dev.metis.agent.data.storage.TimetableImporter
import dev.metis.agent.data.storage.TimetableRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TimetableAgent(
    private val fallback: ConfirmableAgent,
    private val timetable: TimetableRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ConfirmableAgent {

    override fun fork(): ConfirmableAgent? = fallback.fork()?.let {
        TimetableAgent(it, timetable, clock, zone)
    }

    override val reminderFollowUp get() = fallback.reminderFollowUp
    override val promiseFollowUp get() = fallback.promiseFollowUp

    override suspend fun process(request: AgentRequest): AgentResult {
        val parsed = EnglishRules { zone() }.parse(request)
        if (parsed.intent !in TIMETABLE_INTENTS) {
            return fallback.process(request)
        }

        discardProposal()
        val now = clock.instant()
        val currentZone = zone()

        if (!timetable.hasSchedule()) {
            return AgentResult(request.id, AgentResultStatus.ANSWER, TimetableQueries.NO_SCHEDULE_MSG)
        }

        return when (parsed.intent) {
            AgentIntent.NEXT_CLASS -> {
                val answer = TimetableQueries.nextClass(timetable, now, currentZone)
                AgentResult(request.id, AgentResultStatus.ANSWER, answer)
            }
            AgentIntent.TODAY_REMAINING -> {
                val answer = TimetableQueries.todayRemaining(timetable, now, currentZone)
                AgentResult(request.id, AgentResultStatus.ANSWER, answer)
            }
            AgentIntent.DAY_SCHEDULE -> {
                val targetDay = resolveTargetDay(request.text, now, currentZone)
                val answer = TimetableQueries.daySchedule(timetable, targetDay)
                AgentResult(request.id, AgentResultStatus.ANSWER, answer)
            }
            AgentIntent.FREE_AT -> {
                val targetMin = resolveTargetMinute(request.text, now, currentZone)
                if (targetMin == null) {
                    AgentResult(
                        request.id,
                        AgentResultStatus.ANSWER,
                        "Specify a valid time to check availability (e.g., 'am I free at 4pm' or '4 baje free hoon kya')."
                    )
                } else {
                    val targetDay = resolveTargetDayForFreeAt(request.text, now, currentZone)
                    val answer = TimetableQueries.freeAt(timetable, targetMin, targetDay)
                    AgentResult(request.id, AgentResultStatus.ANSWER, answer)
                }
            }
            else -> fallback.process(request)
        }
    }

    override suspend fun accept(proposal: ActionProposal): AgentResult = fallback.accept(proposal)
    override suspend fun undo(receipt: ActionReceipt): UndoResult = fallback.undo(receipt)
    override fun discardProposal() = fallback.discardProposal()

    companion object {
        val TIMETABLE_INTENTS = setOf(
            AgentIntent.NEXT_CLASS,
            AgentIntent.FREE_AT,
            AgentIntent.TODAY_REMAINING,
            AgentIntent.DAY_SCHEDULE,
        )

        fun resolveTargetDay(text: String, now: Instant, zone: ZoneId): Int {
            val lower = text.lowercase(Locale.ROOT)
            val zonedDateTime = now.atZone(zone)
            val currentDay = zonedDateTime.dayOfWeek.value

            if (Regex("""\b(?:today|aaj)\b""").containsMatchIn(lower)) {
                return currentDay
            }
            if (Regex("""\b(?:tomorrow|kal)\b""").containsMatchIn(lower)) {
                return (currentDay % 7) + 1
            }

            // Typo-tolerant and Hinglish weekday matching
            if (Regex("""\b(?:mon|mondy|monday|somwar|somvaar)\b""").containsMatchIn(lower)) return 1
            if (Regex("""\b(?:tue|tues|tueday|tuesday|mangalwar|mangalvaar)\b""").containsMatchIn(lower)) return 2
            if (Regex("""\b(?:wed|wensday|weds|wednesday|budhwar|budhvaar)\b""").containsMatchIn(lower)) return 3
            if (Regex("""\b(?:thu|thur|thurs|thursdy|thursday|guruwar|guruvaar|veervaar)\b""").containsMatchIn(lower)) return 4
            if (Regex("""\b(?:fri|fridy|friday|shukrawar|shukravaar)\b""").containsMatchIn(lower)) return 5
            if (Regex("""\b(?:sat|satday|saturday|shaniwar|shanivaar)\b""").containsMatchIn(lower)) return 6
            if (Regex("""\b(?:sun|sundy|sunday|ravivar|ravivaar|itwar)\b""").containsMatchIn(lower)) return 7

            return currentDay
        }

        private fun resolveTargetDayForFreeAt(text: String, now: Instant, zone: ZoneId): Int {
            val lower = text.lowercase(Locale.ROOT)
            val currentDay = now.atZone(zone).dayOfWeek.value
            return if (Regex("""\b(?:tomorrow|kal)\b""").containsMatchIn(lower)) {
                (currentDay % 7) + 1
            } else {
                resolveTargetDay(text, now, zone)
            }
        }

        fun resolveTargetMinute(text: String, now: Instant, zone: ZoneId): Int? {
            val timeRegex = Regex("""(?i)\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(?:baje)?(?:\s*(am|pm))?\b""")
            val matches = timeRegex.findAll(text).toList()
            for (match in matches) {
                val hourStr = match.groupValues[1]
                var hour = hourStr.toIntOrNull() ?: continue
                val minStr = match.groupValues[2]
                val minute = if (minStr.isNotEmpty()) minStr.toIntOrNull() ?: 0 else 0
                val ampm = match.groupValues[3].lowercase(Locale.ROOT)

                if (ampm == "pm" && hour < 12) hour += 12
                else if (ampm == "am" && hour == 12) hour = 0
                else if (ampm.isEmpty()) {
                    // Contextual interpretation: 1..6 typically PM in student day, or compare with current
                    if (hour in 1..6) hour += 12
                }

                if (hour in 0..23 && minute in 0..59) {
                    return hour * 60 + minute
                }
            }
            return null
        }
    }
}

