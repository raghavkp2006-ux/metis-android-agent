@file:Suppress(
    "MagicNumber",           // minute/hour arithmetic
    "CyclomaticComplexMethod", // query dispatch
    "LongMethod",            // query implementations
    "ReturnCount",           // multiple early returns for guard clauses
    "MaxLineLength",         // long formatted string literals
)

package dev.metis.agent.domain.agent

import dev.metis.agent.data.storage.TimetableImporter
import dev.metis.agent.data.storage.TimetableRepository
import dev.metis.agent.domain.storage.ScheduleKind
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

object TimetableQueries {
    const val NO_SCHEDULE_MSG = "No schedule imported."

    suspend fun nextClass(
        repository: TimetableRepository,
        now: Instant,
        zone: ZoneId,
    ): String {
        if (!repository.hasSchedule()) return NO_SCHEDULE_MSG

        val zonedDateTime = now.atZone(zone)
        val currentDay = zonedDateTime.dayOfWeek.value // 1..7 (Mon..Sun)
        val currentMin = zonedDateTime.hour * 60 + zonedDateTime.minute

        val todayBlocks = repository.getBlocksForWeekday(currentDay)
            .filter { it.kind in setOf(ScheduleKind.CLASS, ScheduleKind.LAB) }
            .sortedBy { it.startMin }

        val activeClass = todayBlocks.firstOrNull { it.startMin <= currentMin && currentMin < it.endMin }
        if (activeClass != null) {
            val nextToday = todayBlocks.firstOrNull { it.startMin >= activeClass.endMin }
            return if (nextToday != null) {
                "Current class: ${activeClass.title} (until ${TimetableImporter.formatMinutes(activeClass.endMin)}). " +
                    "Next class: ${nextToday.title} at ${TimetableImporter.formatMinutes(nextToday.startMin)}."
            } else {
                val nextUpcoming = findNextUpcomingClass(repository, currentDay)
                if (nextUpcoming != null) {
                    val (day, block) = nextUpcoming
                    "Current class: ${activeClass.title} (until ${TimetableImporter.formatMinutes(activeClass.endMin)}). " +
                        "No more classes today. Next class is ${block.title} on ${TimetableImporter.weekdayName(day)} at ${TimetableImporter.formatMinutes(block.startMin)}."
                } else {
                    "Current class: ${activeClass.title} (until ${TimetableImporter.formatMinutes(activeClass.endMin)}). " +
                        "No more classes scheduled."
                }
            }
        }

        // At boundary or before class starts
        val nextClassToday = todayBlocks.firstOrNull { it.startMin >= currentMin }
        if (nextClassToday != null) {
            return "Next class: ${nextClassToday.title} at ${TimetableImporter.formatMinutes(nextClassToday.startMin)} " +
                "(${TimetableImporter.formatMinutes(nextClassToday.startMin)} – ${TimetableImporter.formatMinutes(nextClassToday.endMin)})."
        }

        val nextUpcoming = findNextUpcomingClass(repository, currentDay)
        return if (nextUpcoming != null) {
            val (day, block) = nextUpcoming
            "No more classes today. Next class is ${block.title} on ${TimetableImporter.weekdayName(day)} at ${TimetableImporter.formatMinutes(block.startMin)}."
        } else {
            "No upcoming classes found on your schedule."
        }
    }

    private suspend fun findNextUpcomingClass(
        repository: TimetableRepository,
        currentDay: Int,
    ): Pair<Int, dev.metis.agent.domain.storage.ScheduleBlock>? {
        for (offset in 1..6) {
            val nextDay = ((currentDay - 1 + offset) % 7) + 1
            val blocks = repository.getBlocksForWeekday(nextDay)
                .filter { it.kind in setOf(ScheduleKind.CLASS, ScheduleKind.LAB) }
                .sortedBy { it.startMin }
            if (blocks.isNotEmpty()) {
                return Pair(nextDay, blocks.first())
            }
        }
        return null
    }

    suspend fun todayRemaining(
        repository: TimetableRepository,
        now: Instant,
        zone: ZoneId,
    ): String {
        if (!repository.hasSchedule()) return NO_SCHEDULE_MSG

        val zonedDateTime = now.atZone(zone)
        val currentDay = zonedDateTime.dayOfWeek.value
        val currentMin = zonedDateTime.hour * 60 + zonedDateTime.minute

        val remaining = repository.getBlocksForWeekday(currentDay)
            .filter { it.endMin > currentMin }
            .sortedBy { it.startMin }

        if (remaining.isEmpty()) {
            return "Nothing remaining on your schedule for today."
        }

        val lines = remaining.joinToString("\n") { block ->
            val tagPart = block.tag?.let { " [$it]" } ?: ""
            "• ${TimetableImporter.formatMinutes(block.startMin)} – ${TimetableImporter.formatMinutes(block.endMin)}: ${block.title} (${block.kind})$tagPart"
        }
        return "Remaining today (${TimetableImporter.weekdayName(currentDay)}):\n$lines"
    }

    suspend fun daySchedule(
        repository: TimetableRepository,
        targetDay: Int,
        dayLabel: String? = null,
    ): String {
        if (!repository.hasSchedule()) return NO_SCHEDULE_MSG

        val blocks = repository.getBlocksForWeekday(targetDay).sortedBy { it.startMin }
        val name = dayLabel ?: TimetableImporter.weekdayName(targetDay)
        if (blocks.isEmpty()) {
            return "No schedule blocks found for $name."
        }

        val lines = blocks.joinToString("\n") { block ->
            val flexPart = if (!block.fixed) " [flexible]" else ""
            val tagPart = block.tag?.let { " [$it]" } ?: ""
            "• ${TimetableImporter.formatMinutes(block.startMin)} – ${TimetableImporter.formatMinutes(block.endMin)}: ${block.title} (${block.kind})$tagPart$flexPart"
        }
        return "$name schedule:\n$lines"
    }

    suspend fun freeAt(
        repository: TimetableRepository,
        targetMin: Int,
        targetDay: Int,
    ): String {
        if (!repository.hasSchedule()) return NO_SCHEDULE_MSG

        val dayName = TimetableImporter.weekdayName(targetDay)
        val timeFormatted = TimetableImporter.formatMinutes(targetMin)

        val blocks = repository.getBlocksForWeekday(targetDay)
        val overlappingBlock = blocks.firstOrNull { it.startMin <= targetMin && targetMin < it.endMin }

        if (overlappingBlock != null) {
            val range = "${TimetableImporter.formatMinutes(overlappingBlock.startMin)} – ${TimetableImporter.formatMinutes(overlappingBlock.endMin)}"
            return if (overlappingBlock.fixed) {
                "You are not free at $timeFormatted on $dayName: you have ${overlappingBlock.title} ($range)."
            } else {
                "You have a flexible slot at $timeFormatted on $dayName: ${overlappingBlock.title} ($range)."
            }
        }

        val rules = repository.getRoutineRules().filter { it.matchesWeekday(targetDay) }
        val overlappingRule = rules.firstOrNull { it.windowStartMin <= targetMin && targetMin < it.windowEndMin }

        if (overlappingRule != null) {
            val range = "${TimetableImporter.formatMinutes(overlappingRule.windowStartMin)} – ${TimetableImporter.formatMinutes(overlappingRule.windowEndMin)}"
            return "You are in your scheduled ${overlappingRule.key} window ($range) at $timeFormatted on $dayName."
        }

        return "You are free at $timeFormatted on $dayName."
    }
}

