@file:Suppress("MagicNumber")

package dev.metis.agent.domain.storage

enum class ScheduleKind { CLASS, LAB, ROUTINE, FREE }
enum class RuleHardness { SOFT, FIRM }

data class ScheduleBlock(
    val id: String,
    val weekday: Int,
    val startMin: Int,
    val endMin: Int,
    val title: String,
    val kind: ScheduleKind,
    val fixed: Boolean,
    val tag: String? = null,
) {
    init {
        require(weekday in 1..7) { "Weekday must be between 1 (Monday) and 7 (Sunday)" }
        require(startMin in 0 until 1440) { "Start minute must be in [0, 1439]" }
        require(endMin in 1..1440) { "End minute must be in [1, 1440]" }
        require(endMin > startMin) { "End minute ($endMin) must be greater than start minute ($startMin)" }
        require(title.isNotBlank()) { "Title must not be blank" }
    }

    fun overlaps(other: ScheduleBlock): Boolean =
        weekday == other.weekday && startMin < other.endMin && other.startMin < endMin
}

data class RoutineRule(
    val id: String,
    val key: String,
    val windowStartMin: Int,
    val windowEndMin: Int,
    val weekdaysMask: Int,
    val hardness: RuleHardness,
    val note: String? = null,
) {
    init {
        require(key.isNotBlank()) { "Rule key must not be blank" }
        require(windowStartMin in 0 until 1440) { "Window start minute must be in [0, 1439]" }
        require(windowEndMin in 1..1440) { "Window end minute must be in [1, 1440]" }
        require(windowEndMin > windowStartMin) { "Window end minute must be greater than window start minute" }
        require(weekdaysMask in 1..127) { "Weekdays mask must be between 1 and 127" }
    }

    fun matchesWeekday(weekday: Int): Boolean {
        require(weekday in 1..7)
        return (weekdaysMask and (1 shl (weekday - 1))) != 0
    }
}

