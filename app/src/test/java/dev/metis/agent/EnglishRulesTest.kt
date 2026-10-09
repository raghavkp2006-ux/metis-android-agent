package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.languageAgentOrchestrator
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishRulesTest {
    @Test
    fun taskEditGrammarPreservesTitlesAndResolvesOnlyExplicitTimes() {
        val rename = rules.parse(request("rename task: Study 😀! to: Study physics!"))
        assertEquals(AgentIntent.UPDATE_TASK, rename.intent)
        assertEquals(listOf("Study 😀!", "Study physics!"), rename.entities.map { it.rawValue })
        val priority = rules.parse(request("prioritize task: Study 😀! to: 3"))
        assertEquals(AgentIntent.UPDATE_TASK, priority.intent)
        assertEquals(AgentIntent.DELETE_TASK, rules.parse(request("delete task: Study 😀!")).intent)
        val postponed = rules.parse(request("postpone task: Study 😀! until tomorrow at 08:00"))
        assertEquals(AgentIntent.POSTPONE_TASK, postponed.intent)
        assertTrue(postponed.entities.last().normalizedValue is NormalizedValue.Time)
        assertNull(rules.parse(request("postpone task: Study until tomorrow at 8")).entities.last().normalizedValue)
        assertTrue(rules.parse(request("don't delete task: Study")).negated)
        listOf("rename Study to physics", "delete all tasks", "postpone task: Study until soon").forEach {
            assertEquals(AgentIntent.UNKNOWN, rules.parse(request(it)).intent)
        }
    }
    @Test
    fun completionGrammarPreservesExactTitlePunctuationAndUtf16Offsets() {
        val input = request(" Please complete the task: Study 😀! ")
        val parsed = rules.parse(input)
        assertEquals(AgentIntent.COMPLETE_TASK, parsed.intent)
        val title = parsed.entities.single()
        assertEquals("Study 😀!", title.rawValue)
        assertEquals(title.rawValue, input.text.substring(title.startOffset, title.endOffset))
        assertEquals(AgentIntent.UNKNOWN, rules.parse(request("finish study")).intent)
    }

    private val zone = ZoneId.of("Asia/Kolkata")
    private val rules = EnglishRules { zone }

    @Test
    fun versionedDevelopmentCorpusMatchesIntentAndSafeOutcome(): Unit = runBlocking {
        val file = File(System.getProperty("metis.projectDir"), "../docs/evaluation/english-rules-v1.txt")
        val fixtures = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(45, fixtures.size)
        val pipeline = languageAgentOrchestrator(TestReads()) { zone }
        fixtures.forEach { row ->
            val fields = row.split('|', limit = 4)
            val input = request(fields[3])
            assertEquals(fields[0], AgentIntent.valueOf(fields[1]), rules.parse(input).intent)
            val result = pipeline.process(input)
            assertEquals(fields[0], AgentResultStatus.valueOf(fields[2]), result.status)
            assertTrue(fields[0], result.proposals.isEmpty() && result.completedActions.isEmpty())
        }
    }

    @Test
    fun anchoredGrammarRejectsQuotesHypotheticalsCompoundRequestsAndUnsupportedLanguage() {
        listOf("if i say remind me tomorrow at 8 am to study", "\"show my tasks\"", "show my tasks and delete them",
            "tell me a joke", "kal yaad dilana", "add tasks", "when am i free tomorrow and call Rahul").forEach {
            assertEquals(it, AgentIntent.UNKNOWN, rules.parse(request(it)).intent)
        }
        InputSource.entries.filter { it != InputSource.TEXT }.forEach {
            assertEquals(AgentIntent.UNSUPPORTED, rules.parse(request("show my tasks").copy(source = it)).intent)
        }
    }

    @Test
    fun negationAlwaysStopsBeforeAnyRead(): Unit = runBlocking {
        val reads = TestReads()
        listOf("don't show my tasks", "Do not remind me tomorrow at 8 am to study", "never set a timer for 5 minutes",
            "remind me tomorrow at 8 am to not call Rahul", "stop showing my tasks",
            "don’t search memory for exam").forEach {
            val result = languageAgentOrchestrator(reads) { zone }.process(request(it))
            assertEquals(AgentResultStatus.DENIED, result.status)
            assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
        }
        assertEquals(0, reads.calls)
    }

    @Test
    fun extractionPreservesOriginalUtf16AndReminderContainsCallOnlyAsTitle() {
        val request = request("Please remind me tomorrow at 8 pm to call Rahul 😀")
        val parsed = rules.parse(request)
        assertEquals(AgentIntent.CREATE_REMINDER, parsed.intent)
        parsed.entities.forEach { assertEquals(it.rawValue, request.text.substring(it.startOffset, it.endOffset)) }
        assertEquals("call Rahul 😀", parsed.entities.single { it.type == EntityKind.TITLE }.rawValue)
        val resolved = (parsed.entities.first().normalizedValue as NormalizedValue.Time).value
        assertEquals(Instant.parse("2026-10-10T14:30:00Z"), resolved.instant)
        assertEquals(zone, resolved.zone)
    }

    @Test
    fun relativeDateUsesRequestTimeAndZoneAcrossMidnight() {
        val input = request("remind me tomorrow at 12 am to study")
            .copy(timestamp = Instant.parse("2026-10-09T20:00:00Z"))
        val time = rules.parse(input).entities.first().normalizedValue as NormalizedValue.Time
        assertEquals(Instant.parse("2026-10-10T18:30:00Z"), time.value.instant)
        val noon = rules.parse(request("remind me tomorrow at 12 pm to study"))
        assertEquals(Instant.parse("2026-10-10T06:30:00Z"),
            (noon.entities.first().normalizedValue as NormalizedValue.Time).value.instant)
    }

    @Test
    fun missingDateAmbiguousTimeInvalidDateAndPastTimeStayUnresolved(): Unit = runBlocking {
        listOf("tomorrow at 8", "at 8 am", "2026-02-30 at 8 am", "tomorrow at 13 pm", "tomorrow at 25:00",
            "tomorrow at 8:65", "today at 8 am", "2020-01-01 at 08:00").forEach {
            val input = request("remind me $it to study")
            assertNull(it, rules.parse(input).entities.first().normalizedValue)
            val result = languageAgentOrchestrator(TestReads()) { zone }.process(input)
            assertEquals(it, AgentResultStatus.FOLLOW_UP, result.status)
            assertTrue(result.followUp!!.missingFields.contains("date/time"))
        }
        val missingTitle = languageAgentOrchestrator(TestReads()) { zone }
            .process(request("remind me tomorrow at 8 am"))
        assertEquals(setOf("title"), missingTitle.followUp!!.missingFields)
    }

    @Test
    fun dstGapAndOverlapAreNotSilentlyNormalized() {
        val ny = EnglishRules { ZoneId.of("America/New_York") }
        listOf("2027-03-14 at 02:30", "2026-11-01 at 01:30").forEach {
            assertNull(ny.parse(request("remind me $it to study")).entities.first().normalizedValue)
        }
        assertTrue(ny.parse(request("remind me 2027-03-14 at 03:30 to study"))
            .entities.first().normalizedValue is NormalizedValue.Time)
    }

    @Test
    fun readRequestsUseNarrowPortsWhileRecognizedMutationsNeverProposeOrExecute(): Unit = runBlocking {
        val reads = TestReads()
        val pipeline = languageAgentOrchestrator(reads) { zone }
        listOf("show my tasks", "what do you know about DSP exam?", "what did i promise Rahul?").forEach {
            assertEquals(AgentResultStatus.ANSWER, pipeline.process(request(it)).status)
        }
        assertEquals(3, reads.calls)
        assertEquals("DSP exam", reads.query)
        assertEquals("Rahul", reads.person)
        listOf("add a task to study", "remind me tomorrow at 8 pm to call Rahul", "set a timer for 5 minutes",
            "when am i free tomorrow?").forEach {
            val result = pipeline.process(request(it))
            assertEquals(AgentResultStatus.UNSUPPORTED, result.status)
            assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
        }
        assertEquals(3, reads.calls)
    }

    @Test
    fun boundedDurationsRejectZeroOverflowAndHugeValues(): Unit = runBlocking {
        val pipeline = languageAgentOrchestrator(TestReads()) { zone }
        listOf("0 seconds", "86401 seconds", "1441 minutes", "9999999999 minutes").forEach {
            assertEquals(AgentResultStatus.FOLLOW_UP, pipeline.process(request("set a timer for $it")).status)
        }
        assertEquals(NormalizedValue.Duration(86400), rules.parse(request("set a timer for 1440 minutes"))
            .entities.single().normalizedValue)
    }

    @Test
    fun storageFailureIsGenericAndCancellationPropagates(): Unit = runBlocking {
        val reads = TestReads()
        reads.failure = IllegalStateException("private synthetic data")
        val result = languageAgentOrchestrator(reads) { zone }.process(request("show my tasks"))
        assertEquals(AgentResultStatus.FAILED, result.status)
        assertTrue(!result.message.contains("private"))
        reads.failure = CancellationException("cancel")
        assertThrows(CancellationException::class.java) {
            runBlocking { languageAgentOrchestrator(reads) { zone }.process(request("show my tasks")) }
        }
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-09T05:30:00Z"))
}

private class TestReads : AgentReadPort {
    var calls = 0
    var query = ""
    var person = ""
    var failure: Exception? = null
    override suspend fun tasks(): String { calls++; failure?.let { throw it }; return "Synthetic tasks" }
    override suspend fun memories(query: String, at: Long): String {
        calls++; this.query = query; return "Synthetic memory"
    }
    override suspend fun promises(person: String): SpecialistResult {
        calls++; this.person = person; return SpecialistResult.Answer("Synthetic promise")
    }
}
