package dev.metis.agent.domain.storage

import java.time.DayOfWeek
import java.time.LocalDate

/** DAILY is stored as 0; nonempty weekday masks use bits 0..6 for Monday..Sunday. */
data class HabitCadence(val weekdayMask: Int = 0) {
    init { require(weekdayMask in 0..ALL_WEEKDAYS) }
    fun matches(day: DayOfWeek) = weekdayMask == 0 || weekdayMask and (1 shl (day.value - 1)) != 0

    companion object {
        private const val ALL_WEEKDAYS = 127
        val DAILY = HabitCadence()
        fun weekdays(days: Set<DayOfWeek>): HabitCadence {
            require(days.isNotEmpty())
            return HabitCadence(days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) })
        }
    }
}

/** User-defined discipline habit, separate from the consent-gated inferred SavedHabit. */
data class HabitDefinition(
    val name: String,
    val startLocalDate: LocalDate,
    val cadence: HabitCadence = HabitCadence.DAILY,
    val description: String? = null,
    val archivedAt: LocalDate? = null,
    val metadata: RecordMetadata = RecordMetadata(),
) {
    init {
        require(name.isNotBlank() && name.length <= NAME_LIMIT)
        require(description == null || description.length <= FOUNDATION_CONTENT_LIMIT)
        DisciplineDates.requireCanonical(startLocalDate)
        archivedAt?.let { DisciplineDates.requireCanonical(it); require(it.isAfter(startLocalDate)) }
    }

    val active: Boolean get() = archivedAt == null

    fun isExpectedOn(date: LocalDate) = !date.isBefore(startLocalDate) &&
        (archivedAt == null || date.isBefore(archivedAt)) && cadence.matches(date.dayOfWeek)

    fun expectedDays(range: DisciplineDateRange): Int {
        var date = range.from
        var count = 0
        while (!date.isAfter(range.through)) {
            if (isExpectedOn(date)) count++
            date = date.plusDays(1)
        }
        return count
    }

    companion object { const val NAME_LIMIT = 200 }
}
