@file:Suppress("MaxLineLength") // long strings in fixture tables

package dev.metis.agent

import dev.metis.agent.data.storage.TimetableImporter
import dev.metis.agent.data.storage.TimetableRepository
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.TimetableAgent
import dev.metis.agent.domain.agent.TimetableQueries
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.RoutineRule
import dev.metis.agent.domain.storage.ScheduleBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class TimetableAgentTest {

    private lateinit var timetableRepo: FakeTimetableRepository
    private val zone = ZoneId.of("Asia/Kolkata")
    private val baseMonday = LocalDate.of(2026, 11, 9) // 2026-11-09 is Monday

    @Before
    fun setUp() {
        val stream = javaClass.classLoader?.getResourceAsStream("test_timetable.json")
            ?: error("test_timetable.json not found in test resources")
        val json = stream.bufferedReader().use { it.readText() }
        val parsed = TimetableImporter.parseAndValidate(json)
        timetableRepo = FakeTimetableRepository(parsed.blocks, parsed.rules)
    }

    private fun agentAt(dateTime: LocalDateTime): TimetableAgent {
        val instant = dateTime.atZone(zone).toInstant()
        val fixedClock = Clock.fixed(instant, zone)
        val dummyFallback = object : ConfirmableAgent {
            override fun fork() = this
            override val reminderFollowUp = null
            override val promiseFollowUp = null
            override suspend fun process(request: AgentRequest) =
                AgentResult(request.id, AgentResultStatus.ANSWER, "Fallback handled")
            override suspend fun accept(proposal: ActionProposal) =
                AgentResult(proposal.requestId, AgentResultStatus.ANSWER, "Fallback accepted")
            override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
            override fun discardProposal() = Unit
        }
        return TimetableAgent(dummyFallback, timetableRepo, fixedClock) { zone }
    }

    private fun request(text: String, at: LocalDateTime): AgentRequest =
        AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, at.atZone(zone).toInstant())

    @Test
    fun grammarClassifiesTimetableIntentsCorrectly() {
        val rules = EnglishRules { zone }
        assertEquals(AgentIntent.NEXT_CLASS, rules.parse(request("what's my next class", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.NEXT_CLASS, rules.parse(request("next class", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.NEXT_CLASS, rules.parse(request("agla class kab hai", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.NEXT_CLASS, rules.parse(request("meri agli class kaunsi hai", baseMonday.atTime(9, 0))).intent)

        assertEquals(AgentIntent.FREE_AT, rules.parse(request("am I free at 4", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.FREE_AT, rules.parse(request("am i free at 4pm", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.FREE_AT, rules.parse(request("kya main 4 baje free hoon", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.FREE_AT, rules.parse(request("4 baje free hu kya", baseMonday.atTime(9, 0))).intent)

        assertEquals(AgentIntent.TODAY_REMAINING, rules.parse(request("what's left today", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.TODAY_REMAINING, rules.parse(request("what is remaining today", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.TODAY_REMAINING, rules.parse(request("aaj kya bacha hai", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.TODAY_REMAINING, rules.parse(request("aaj aur kya hai", baseMonday.atTime(9, 0))).intent)

        assertEquals(AgentIntent.DAY_SCHEDULE, rules.parse(request("what do I have on friday", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.DAY_SCHEDULE, rules.parse(request("schedule for monday", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.DAY_SCHEDULE, rules.parse(request("friday ka schedule", baseMonday.atTime(9, 0))).intent)
        assertEquals(AgentIntent.DAY_SCHEDULE, rules.parse(request("shukrawar ko kya hai", baseMonday.atTime(9, 0))).intent)
    }

    @Test
    fun nextClassDuringActiveClass() = runBlocking {
        val dt = baseMonday.atTime(9, 30) // Monday 09:30 (during 09:00 - 10:30 Math)
        val agent = agentAt(dt)
        val result = agent.process(request("what's my next class", dt))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Current class: Mathematics (until 10:30)"))
        assertTrue(result.message.contains("Next class: Physics Lab at 11:00"))
    }

    @Test
    fun nextClassAtBlockBoundary() = runBlocking {
        val dt = baseMonday.atTime(10, 30) // Exactly when Math ends at 10:30
        val agent = agentAt(dt)
        val result = agent.process(request("what's my next class", dt))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Next class: Physics Lab at 11:00"))
    }

    @Test
    fun nextClassEndOfDayTransitionsToNextDay() = runBlocking {
        val dt = baseMonday.atTime(16, 0) // After all Monday classes
        val agent = agentAt(dt)
        val result = agent.process(request("what's my next class", dt))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("No more classes today"))
        assertTrue(result.message.contains("Tuesday at 10:00"))
    }

    @Test
    fun nextClassOnEmptyDayFindsUpcomingDay() = runBlocking {
        val dt = baseMonday.plusDays(2).atTime(10, 0) // Wednesday (empty day)
        val agent = agentAt(dt)
        val result = agent.process(request("what is my next class", dt))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Software Engineering on Thursday at 11:00"))
    }

    @Test
    fun nextClassOnWeekendFindsMonday() = runBlocking {
        val dt = baseMonday.plusDays(5).atTime(14, 0) // Saturday 14:00
        val agent = agentAt(dt)
        val result = agent.process(request("what's my next class", dt))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Mathematics on Monday at 09:00"))
    }

    @Test
    fun nextClassAtMidnight() = runBlocking {
        val dt = baseMonday.atTime(0, 0) // Monday midnight
        val agent = agentAt(dt)
        val result = agent.process(request("next class", dt))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Mathematics at 09:00"))
    }

    @Test
    fun freeAtChecksFixedBlocksAndRoutineWindows() = runBlocking {
        val mondayTime = baseMonday.atTime(9, 0)
        val agent = agentAt(mondayTime)

        // Busy with fixed class Mathematics (09:00-10:30)
        val r1 = agent.process(request("am I free at 9:30", mondayTime))
        assertTrue(r1.message.contains("not free", ignoreCase = true))
        assertTrue(r1.message.contains("Mathematics"))

        // Inside routine rule window (gym 17:00-18:30)
        val r2 = agent.process(request("am I free at 17:30", mondayTime))
        assertTrue(r2.message.contains("scheduled gym window", ignoreCase = true))

        // Truly free time (16:30)
        val r3 = agent.process(request("am I free at 16:30", mondayTime))
        assertTrue(r3.message.contains("You are free at 16:30 on Monday"))

        // Tuesday flexible slot (14:30)
        val tuesdayTime = baseMonday.plusDays(1).atTime(12, 0)
        val tuesdayAgent = agentAt(tuesdayTime)
        val r4 = tuesdayAgent.process(request("am I free at 2pm", tuesdayTime))
        assertTrue(r4.message.contains("flexible slot", ignoreCase = true))
        assertTrue(r4.message.contains("Design Workshop"))
    }

    @Test
    fun freeAtHinglishPhrases() = runBlocking {
        val mondayTime = baseMonday.atTime(9, 0)
        val agent = agentAt(mondayTime)

        val r1 = agent.process(request("kya main 4 baje free hoon", mondayTime))
        assertTrue(r1.message.contains("You are free at 16:00 on Monday"))

        val r2 = agent.process(request("9 baje koi class hai kya", mondayTime))
        assertTrue(r2.message.contains("not free", ignoreCase = true))
        assertTrue(r2.message.contains("Mathematics"))
    }

    @Test
    fun todayRemainingListsRemainingBlocks() = runBlocking {
        val dt = baseMonday.atTime(10, 0) // 10:00 AM on Monday
        val agent = agentAt(dt)
        val result = agent.process(request("what's left today", dt))

        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Physics Lab"))
        assertTrue(result.message.contains("Data Structures"))

        // Hinglish
        val rHinglish = agent.process(request("aaj kya bacha hai", dt))
        assertTrue(rHinglish.message.contains("Physics Lab"))
    }

    @Test
    fun dayScheduleAndTypoTolerance() = runBlocking {
        val dt = baseMonday.atTime(9, 0)
        val agent = agentAt(dt)

        // Exact day
        val rFri = agent.process(request("what do I have on friday", dt))
        assertTrue(rFri.message.contains("Algorithms"))
        assertTrue(rFri.message.contains("Machine Learning"))
        assertTrue(rFri.message.contains("Weekly Seminar"))

        // Typo tolerance: "fridy"
        val rFriTypo = agent.process(request("fridy schedule", dt))
        assertTrue(rFriTypo.message.contains("Algorithms"))

        // Hinglish: "shukrawar ko kya hai"
        val rFriHinglish = agent.process(request("shukrawar ko kya hai", dt))
        assertTrue(rFriHinglish.message.contains("Algorithms"))

        // Empty day
        val rWed = agent.process(request("what do I have on wednesday", dt))
        assertTrue(rWed.message.contains("No schedule blocks found for Wednesday"))
    }

    @Test
    fun noScheduleImportedReturnsStandardMessage() = runBlocking {
        val emptyRepo = FakeTimetableRepository(emptyList(), emptyList())
        val dt = baseMonday.atTime(9, 0)
        val agent = TimetableAgent(
            fallback = object : ConfirmableAgent {
                override fun fork() = this
                override val reminderFollowUp = null
                override val promiseFollowUp = null
                override suspend fun process(request: AgentRequest) = AgentResult(request.id, AgentResultStatus.ANSWER, "Fallback")
                override suspend fun accept(proposal: ActionProposal) = AgentResult(proposal.requestId, AgentResultStatus.ANSWER, "Fallback")
                override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
                override fun discardProposal() = Unit
            },
            timetable = emptyRepo,
            clock = Clock.fixed(dt.atZone(zone).toInstant(), zone),
        ) { zone }

        assertEquals(TimetableQueries.NO_SCHEDULE_MSG, agent.process(request("what's my next class", dt)).message)
        assertEquals(TimetableQueries.NO_SCHEDULE_MSG, agent.process(request("am I free at 4", dt)).message)
        assertEquals(TimetableQueries.NO_SCHEDULE_MSG, agent.process(request("what's left today", dt)).message)
        assertEquals(TimetableQueries.NO_SCHEDULE_MSG, agent.process(request("what do I have on friday", dt)).message)
    }

    private class FakeTimetableRepository(
        private var blocks: List<ScheduleBlock>,
        private var rules: List<RoutineRule>,
    ) : TimetableRepository {
        private var backupBlocks = listOf<ScheduleBlock>()
        private var backupRules = listOf<RoutineRule>()

        override suspend fun getBlocks() = blocks
        override suspend fun getBlocksForWeekday(weekday: Int) = blocks.filter { it.weekday == weekday }
        override suspend fun getRoutineRules() = rules
        override fun observeBlocks(): Flow<List<ScheduleBlock>> = flowOf(blocks)
        override fun observeRules(): Flow<List<RoutineRule>> = flowOf(rules)
        override suspend fun replaceSchedule(blocks: List<ScheduleBlock>, rules: List<RoutineRule>) {
            backupBlocks = this.blocks
            backupRules = this.rules
            this.blocks = blocks
            this.rules = rules
        }
        override suspend fun undoReplace(): Boolean {
            if (backupBlocks.isEmpty() && backupRules.isEmpty()) return false
            blocks = backupBlocks
            rules = backupRules
            backupBlocks = emptyList()
            backupRules = emptyList()
            return true
        }
        override suspend fun hasSchedule(): Boolean = blocks.isNotEmpty() || rules.isNotEmpty()
        override suspend fun canUndo(): Boolean = backupBlocks.isNotEmpty() || backupRules.isNotEmpty()
    }
}
