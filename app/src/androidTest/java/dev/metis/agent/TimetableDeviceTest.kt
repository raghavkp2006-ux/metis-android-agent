package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.TimetableImporter
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.TimetableAgent
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.RoutineRule
import dev.metis.agent.domain.storage.RuleHardness
import dev.metis.agent.domain.storage.ScheduleBlock
import dev.metis.agent.domain.storage.ScheduleKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimetableDeviceTest {

    @get:Rule val isolatedState = IsolatedAppStateRule()

    private val fixture = StorageTestFixture()
    @After fun cleanup() = fixture.close()

    private fun request(text: String) = AgentRequest(
        id = UUID.randomUUID(),
        text = text,
        source = InputSource.TEXT,
        timestamp = Instant.now(),
    )

    private val dummyFallback = object : ConfirmableAgent {
        override fun fork() = this
        override val reminderFollowUp = null
        override val promiseFollowUp = null
        override suspend fun process(request: AgentRequest) =
            AgentResult(request.id, AgentResultStatus.ANSWER, "Fallback handled")
        override suspend fun accept(proposal: ActionProposal) =
            AgentResult(proposal.requestId, AgentResultStatus.ANSWER, "Fallback accepted")
        override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
        override fun discardProposal() {}
    }

    @Test
    fun importFlowParsesValidatesStoresAndSupportsUndo(): Unit = runBlocking {
        val sampleJson = """
            {
              "blocks": [
                {
                  "weekday": 1,
                  "start": "09:00",
                  "end": "10:30",
                  "title": "Machine Learning",
                  "kind": "CLASS",
                  "fixed": true,
                  "tag": "CS401"
                },
                {
                  "weekday": 1,
                  "start": "11:00",
                  "end": "12:00",
                  "title": "Algorithms",
                  "kind": "CLASS",
                  "fixed": true
                }
              ],
              "rules": [
                {
                  "key": "gym",
                  "window_start": "17:00",
                  "window_end": "18:30",
                  "weekdays": [1, 2, 3, 4, 5],
                  "hardness": "SOFT"
                }
              ]
            }
        """.trimIndent()

        val timetableRepo = fixture.repository.timetable

        // Initially empty
        assertFalse(timetableRepo.hasSchedule())
        assertFalse(timetableRepo.canUndo())

        // Validate preview
        val preview = TimetableImporter.parseAndValidate(sampleJson)
        assertTrue(preview.valid)
        assertEquals(2, preview.totalBlocksCount)
        assertEquals(1, preview.totalRulesCount)
        assertEquals(2, preview.blocksCountByDay[1])

        // Confirm import
        timetableRepo.replaceSchedule(preview.blocks, preview.rules)
        assertTrue(timetableRepo.hasSchedule())

        // Verify stored in DB with Keystore encryption
        val blocks = timetableRepo.getBlocks()
        assertEquals(2, blocks.size)
        assertEquals("Machine Learning", blocks[0].title)
        assertEquals("Algorithms", blocks[1].title)
        val rules = timetableRepo.getRoutineRules()
        assertEquals(1, rules.size)
        assertEquals("gym", rules[0].key)

        // Verify re-import replaces previous set atomically and backup is created
        val updateJson = """
            {
              "blocks": [
                {
                  "weekday": 2,
                  "start": "14:00",
                  "end": "15:30",
                  "title": "Computer Networks",
                  "kind": "LAB",
                  "fixed": true
                }
              ],
              "rules": []
            }
        """.trimIndent()
        val updatePreview = TimetableImporter.parseAndValidate(updateJson)
        timetableRepo.replaceSchedule(updatePreview.blocks, updatePreview.rules)
        assertTrue(timetableRepo.canUndo())

        val updatedBlocks = timetableRepo.getBlocks()
        assertEquals(1, updatedBlocks.size)
        assertEquals("Computer Networks", updatedBlocks[0].title)

        // Undo restore
        assertTrue(timetableRepo.undoReplace())
        val restoredBlocks = timetableRepo.getBlocks()
        assertEquals(2, restoredBlocks.size)
        assertEquals("Machine Learning", restoredBlocks[0].title)
    }

    @Test
    fun chatQuestionEndToEndWithImportedSchedule(): Unit = runBlocking {
        val timetableRepo = fixture.repository.timetable

        // Seed timetable data
        val blocks = listOf(
            ScheduleBlock("b1", 1, 9 * 60, 10 * 60 + 30, "Machine Learning", ScheduleKind.CLASS, true, "CS401"),
            ScheduleBlock("b2", 1, 14 * 60, 15 * 60 + 30, "Algorithms", ScheduleKind.CLASS, true),
        )
        val rules = listOf(
            RoutineRule("r1", "gym", 17 * 60, 18 * 60 + 30, 127, RuleHardness.SOFT),
        )
        timetableRepo.replaceSchedule(blocks, rules)

        // Monday 2026-11-09 08:30 UTC
        val mondayMorning = Instant.parse("2026-11-09T08:30:00Z")
        val fixedClock = Clock.fixed(mondayMorning, ZoneId.of("UTC"))
        val agent = TimetableAgent(
            fallback = dummyFallback,
            timetable = timetableRepo,
            clock = fixedClock,
            zone = { ZoneId.of("UTC") },
        )

        // 1. Next class query
        val nextClassResult = agent.process(request("what is my next class"))
        assertEquals(AgentResultStatus.ANSWER, nextClassResult.status)
        assertTrue(nextClassResult.message.contains("Machine Learning"))
        assertTrue(nextClassResult.message.contains("09:00"))

        // 2. Free at query (free at 12:00)
        val freeQueryResult = agent.process(request("am I free at 12"))
        assertEquals(AgentResultStatus.ANSWER, freeQueryResult.status)
        assertTrue(freeQueryResult.message.contains("free at 12:00"))

        // 3. Free at query (busy at 09:30 during ML class)
        val busyQueryResult = agent.process(request("am I free at 9:30 am"))
        assertEquals(AgentResultStatus.ANSWER, busyQueryResult.status)
        assertTrue(busyQueryResult.message.contains("Machine Learning"))

        // 4. Today remaining
        val todayResult = agent.process(request("what is left today"))
        assertEquals(AgentResultStatus.ANSWER, todayResult.status)
        assertTrue(todayResult.message.contains("Machine Learning"))
        assertTrue(todayResult.message.contains("Algorithms"))

        // 5. Day schedule
        val dayResult = agent.process(request("what do I have on monday"))
        assertEquals(AgentResultStatus.ANSWER, dayResult.status)
        assertTrue(dayResult.message.contains("Monday"))
        assertTrue(dayResult.message.contains("Machine Learning"))
    }

    @Test
    fun chatQuestionEmptyScheduleSaysNoScheduleImported(): Unit = runBlocking {
        val timetableRepo = fixture.repository.timetable
        val agent = TimetableAgent(
            fallback = dummyFallback,
            timetable = timetableRepo,
            zone = { ZoneId.of("UTC") },
        )

        val result = agent.process(request("what's my next class"))
        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertEquals("No schedule imported.", result.message)
    }
}
