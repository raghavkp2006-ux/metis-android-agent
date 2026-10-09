package dev.metis.agent.domain.insights

import dev.metis.agent.domain.storage.SavedEvent
import java.time.Instant
import java.time.ZoneId

private val BEHAVIORAL_EVENT_TYPES = setOf("TASK_COMPLETED", "TASK_POSTPONED", "FOCUS_COMPLETED")

/** An intentionally small, explainable reflection built only from normalized event type and time. */
data class BehavioralReflection(
    val period: ReflectionPeriod,
    val startAt: Long,
    val endAt: Long,
    val evidenceStartAt: Long,
    val evidenceEndAt: Long,
    val completedTasks: Int,
    val postponedTasks: Int,
    val completedFocusSessions: Int,
    val repeatedPatterns: List<RepeatedPattern>,
    val sourceWatermark: Long,
    val methodVersion: String = BehavioralReflectionEngine.METHOD_VERSION,
) {
    init {
        require(endAt >= startAt)
        require(evidenceEndAt >= evidenceStartAt)
        require(completedTasks >= 0 && postponedTasks >= 0 && completedFocusSessions >= 0)
        require(sourceWatermark >= 0)
    }
}

enum class ReflectionPeriod { DAILY, WEEKLY }

data class RepeatedPattern(
    val eventType: String,
    val weekday: Int,
    val sampleCount: Int,
    val distinctDays: Int,
) {
    init {
        require(eventType in BEHAVIORAL_EVENT_TYPES)
        require(weekday in java.time.DayOfWeek.MONDAY.value..java.time.DayOfWeek.SUNDAY.value)
        require(sampleCount >= BehavioralReflectionEngine.MIN_PATTERN_SAMPLES)
        require(distinctDays >= BehavioralReflectionEngine.MIN_PATTERN_DAYS)
    }
}

object BehavioralReflectionEngine {
    const val METHOD_VERSION = "EVENT_WEEKDAY_FREQUENCY_V1"
    const val MIN_PATTERN_SAMPLES = 3
    const val MIN_PATTERN_DAYS = 3
    const val MAX_WEEKLY_SAMPLES_IN_WINDOW = 4

    private const val PATTERN_LOOKBACK_OFFSET_DAYS = 27L

    /**
     * Summarizes the requested calendar day/week. Patterns use up to 28 days of repeated evidence.
     * The caller must gate this operation on explicit behavioral-analysis consent.
     */
    fun reflect(
        events: List<SavedEvent>,
        period: ReflectionPeriod,
        now: Long,
        zoneId: ZoneId,
        consented: Boolean,
    ): BehavioralReflection? {
        if (!consented) return null
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val startDate = when (period) {
            ReflectionPeriod.DAILY -> today
            ReflectionPeriod.WEEKLY -> today.minusDays((today.dayOfWeek.value - 1).toLong())
        }
        val endExclusive = when (period) {
            ReflectionPeriod.DAILY -> startDate.plusDays(1)
            ReflectionPeriod.WEEKLY -> startDate.plusWeeks(1)
        }
        val startAt = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endAt = endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        val periodEvents = events.filter {
            it.timestamp in startAt..minOf(endAt, now) && it.type in BEHAVIORAL_EVENT_TYPES
        }
        val evidenceStart = today.minusDays(PATTERN_LOOKBACK_OFFSET_DAYS)
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
        val evidence = events.asSequence()
            .filter { it.timestamp in evidenceStart..now && it.type in BEHAVIORAL_EVENT_TYPES }
            .map { it to Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }
            .toList()
        val evidenceWatermark = evidence.maxOfOrNull { it.first.timestamp } ?: 0L
        val patterns = evidence.groupBy { (event, date) -> event.type to date.dayOfWeek.value }
            .mapNotNull { (key, rows) ->
                val distinctDays = rows.map { it.second }.distinct().size
                if (rows.size < MIN_PATTERN_SAMPLES || distinctDays < MIN_PATTERN_DAYS) null
                else RepeatedPattern(key.first, key.second, rows.size, distinctDays)
            }
            .sortedWith(compareByDescending<RepeatedPattern> { it.distinctDays }
                .thenByDescending { it.sampleCount }.thenBy { it.eventType }.thenBy { it.weekday })
        return BehavioralReflection(
            period = period,
            startAt = startAt,
            endAt = endAt,
            evidenceStartAt = evidenceStart,
            evidenceEndAt = now,
            completedTasks = periodEvents.count { it.type == "TASK_COMPLETED" },
            postponedTasks = periodEvents.count { it.type == "TASK_POSTPONED" },
            completedFocusSessions = periodEvents.count { it.type == "FOCUS_COMPLETED" },
            repeatedPatterns = patterns,
            sourceWatermark = evidenceWatermark,
        )
    }
}
