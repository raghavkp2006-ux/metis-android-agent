package dev.metis.agent

import dev.metis.agent.data.storage.TimetableImporter
import dev.metis.agent.domain.storage.RuleHardness
import dev.metis.agent.domain.storage.ScheduleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableImporterTest {

    @Test
    fun parseValidResourceFixture() {
        val stream = javaClass.classLoader?.getResourceAsStream("test_timetable.json")
            ?: error("test_timetable.json not found in test resources")
        val json = stream.bufferedReader().use { it.readText() }

        val result = TimetableImporter.parseAndValidate(json)
        assertTrue(result.errors.joinToString("\n"), result.valid)
        assertEquals(9, result.totalBlocksCount)
        assertEquals(2, result.totalRulesCount)
        assertEquals(3, result.blocksCountByDay[1]) // Monday: 3 blocks
        assertEquals(2, result.blocksCountByDay[2]) // Tuesday: 2 blocks
        assertEquals(0, result.blocksCountByDay[3]) // Wednesday: 0 blocks
        assertEquals(1, result.blocksCountByDay[4]) // Thursday: 1 block
        assertEquals(3, result.blocksCountByDay[5]) // Friday: 3 blocks
        assertEquals(0, result.blocksCountByDay[6]) // Saturday: 0 blocks
        assertEquals(0, result.blocksCountByDay[7]) // Sunday: 0 blocks

        val firstBlock = result.blocks.first()
        assertEquals(1, firstBlock.weekday)
        assertEquals(9 * 60, firstBlock.startMin)
        assertEquals(10 * 60 + 30, firstBlock.endMin)
        assertEquals(ScheduleKind.CLASS, firstBlock.kind)
        assertTrue(firstBlock.fixed)

        val gymRule = result.rules.first { it.key == "gym" }
        assertEquals(17 * 60, gymRule.windowStartMin)
        assertEquals(18 * 60 + 30, gymRule.windowEndMin)
        assertEquals(RuleHardness.SOFT, gymRule.hardness)
        assertTrue(gymRule.matchesWeekday(1))
        assertTrue(gymRule.matchesWeekday(5))
        assertFalse(gymRule.matchesWeekday(6))
    }

    @Test
    fun rejectOverlappingFixedBlocksOnSameDay() {
        val json = """
            {
                "version": 1,
                "blocks": [
                    { "weekday": 1, "start": "09:00", "end": "10:30", "title": "Math", "fixed": true },
                    { "weekday": 1, "start": "10:00", "end": "11:30", "title": "Physics", "fixed": true }
                ]
            }
        """.trimIndent()

        val result = TimetableImporter.parseAndValidate(json)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("overlap", ignoreCase = true) && it.contains("Monday") })
    }

    @Test
    fun allowOverlappingWhenNotFixed() {
        val json = """
            {
                "version": 1,
                "blocks": [
                    { "weekday": 1, "start": "09:00", "end": "10:30", "title": "Math", "fixed": true },
                    { "weekday": 1, "start": "10:00", "end": "11:30", "title": "Optional Study", "fixed": false }
                ]
            }
        """.trimIndent()

        val result = TimetableImporter.parseAndValidate(json)
        assertTrue(result.errors.joinToString(), result.valid)
        assertEquals(2, result.totalBlocksCount)
    }

    @Test
    fun rejectEndBeforeOrEqualStart() {
        val json = """
            {
                "version": 1,
                "blocks": [
                    { "weekday": 2, "start": "10:30", "end": "10:30", "title": "Zero Duration" },
                    { "weekday": 2, "start": "11:00", "end": "10:00", "title": "Negative Duration" }
                ]
            }
        """.trimIndent()

        val result = TimetableImporter.parseAndValidate(json)
        assertFalse(result.valid)
        assertTrue(result.errors.size >= 2)
        assertTrue(result.errors.any { it.contains("after start time") })
    }

    @Test
    fun rejectUnknownWeekday() {
        val json = """
            {
                "version": 1,
                "blocks": [
                    { "weekday": "funday", "start": "09:00", "end": "10:00", "title": "Party" }
                ]
            }
        """.trimIndent()

        val result = TimetableImporter.parseAndValidate(json)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("unknown or invalid weekday", ignoreCase = true) })
    }

    @Test
    fun rejectBlankTitle() {
        val json = """
            {
                "version": 1,
                "blocks": [
                    { "weekday": 1, "start": "09:00", "end": "10:00", "title": "   " }
                ]
            }
        """.trimIndent()

        val result = TimetableImporter.parseAndValidate(json)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("title must not be blank", ignoreCase = true) })
    }

    @Test
    fun rejectInvalidRoutineRule() {
        val json = """
            {
                "version": 1,
                "rules": [
                    { "key": "gym", "start": "18:00", "end": "17:00", "weekdays": "mon-fri" },
                    { "key": "sleep", "start": "23:00", "end": "07:00", "weekdays": [] }
                ]
            }
        """.trimIndent()

        val result = TimetableImporter.parseAndValidate(json)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("window end") })
        assertTrue(result.errors.any { it.contains("invalid weekdays mask") })
    }
}

