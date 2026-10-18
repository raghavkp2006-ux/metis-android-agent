package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.CalendarReadScope
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ContextBuilder
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityExtractor
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExtractedEntity
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.IntentClassifier
import dev.metis.agent.domain.agent.IntentPrediction
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.LocalAgentOrchestrator
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.presentation.navigation.CalendarDraft
import dev.metis.agent.presentation.navigation.buildEvent
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarV13Test {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val time = Instant.parse("2026-10-12T05:30:00Z")
    private val valid = listOf(
        "Do I already have some plans for tomorrow afternoon?" to CalendarReadScope(
            CalendarReadScope.Kind.PLANS, CalendarReadScope.Day.TOMORROW,
            CalendarReadScope.Period.AFTERNOON),
        "Which meetings are coming up next?" to CalendarReadScope(CalendarReadScope.Kind.MEETINGS),
        "Have any meetings with Rohit this week?" to CalendarReadScope(
            CalendarReadScope.Kind.MEETINGS_WITH_PERSON, CalendarReadScope.Day.THIS_WEEK, subject = "Rohit"),
        "When do I have to meet again concerning the research project?" to CalendarReadScope(
            CalendarReadScope.Kind.MEETINGS_ABOUT_TOPIC, subject = "research project"),
        "Show my calendar" to CalendarReadScope(CalendarReadScope.Kind.EVENTS),
        "What meetings are on my calendar?" to CalendarReadScope(CalendarReadScope.Kind.MEETINGS),
        "Bring up my appointments" to CalendarReadScope(CalendarReadScope.Kind.APPOINTMENTS),
        "When am I free tomorrow?" to CalendarReadScope(CalendarReadScope.Kind.AVAILABILITY,
            CalendarReadScope.Day.TOMORROW),
    )

    @Test fun approvedFormsPreserveScopesAndReadOnce(): Unit = runBlocking {
        valid.forEach { (text, expected) ->
            val reads = Reads()
            val contexts = Contexts()
            val request = request(text)
            val result = pipeline(reads, contexts).process(request)
            assertEquals(text, AgentResultStatus.ANSWER, result.status)
            assertEquals(text, expected, reads.scope)
            assertEquals(text, 1, reads.calls)
            assertEquals(text, 1, contexts.calls)
            assertEquals(text, AgentIntent.CHECK_CALENDAR, EnglishRules().parse(request).intent)
        }
    }

    @Test fun unsafeVariantsBlockBeforeContextEvenWithForcedCalendarLabel(): Unit = runBlocking {
        val variants = listOf(
            "Have any meetings with Rohit this week in Chennai?",
            "Have any meetings with him this week?",
            "Have any meetings with Rohit this week and call him?",
            "Which meetings are coming up next and make a note?",
            "Do I already have some plans for tomorrow afternoon except work?",
            "Do I already have some plans for tomorrow afternoon and next morning?",
            "When do I have to meet again concerning the research project before Friday?",
            "Don't show my calendar",
            "Show my calendar and delete an event",
            "When am I free tomorrow after 3 pm?",
        )
        variants.forEach { text ->
            val reads = Reads()
            val contexts = Contexts()
            val result = pipeline(reads, contexts, forced = true).process(request(text))
            assertEquals(text, AgentResultStatus.FOLLOW_UP, result.status)
            assertTrue(text, result.message.contains(text))
            assertEquals(text, 0, reads.calls)
            assertEquals(text, 0, contexts.calls)
        }
    }

    @Test fun forgedCalendarEntityCannotReachContextOrReadPort(): Unit = runBlocking {
        valid.forEach { (text, _) ->
            val reads = Reads()
            val contexts = Contexts()
            val forged = object : EntityExtractor {
                override suspend fun extract(request: AgentRequest) = listOf(ExtractedEntity(
                    EntityKind.MEMORY, request.text.take(1), 0, 1, 1.0))
            }
            val result = pipeline(reads, contexts, extractor = forged).process(request(text))
            assertEquals(text, AgentResultStatus.FOLLOW_UP, result.status)
            assertTrue(text, result.message.contains(text))
            assertEquals(text, 0, contexts.calls)
            assertEquals(text, 0, reads.calls)
        }
    }

    @org.junit.Ignore("Archived Phase 0: requires historical docs/evaluation fixtures.")
    @Test fun exposedUserBatchHasFourCalendarAndFourMemoryReadsOnly(): Unit = runBlocking {
        val directory = File(System.getProperty("metis.projectDir"),
            "../docs/evaluation/phase15/candidate_v12")
        val expected = File(directory, "regression-expectations.tsv").readLines().drop(1)
            .associate { it.split('\t').let { fields -> fields[0] to fields } }
        var calendars = 0
        var memories = 0
        var forbidden = 0
        File(directory, "regression-input.tsv").readLines().drop(1).forEach { line ->
            val fields = line.split('\t')
            val id = fields[0]
            if (!id.startsWith("user-v12-")) return@forEach
            val text = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
            val annotated = requireNotNull(expected[id])
            val reads = Reads()
            val contexts = Contexts()
            val result = pipeline(reads, contexts).process(request(text))
            when {
                id in setOf("user-v12-23", "user-v12-24", "user-v12-25", "user-v12-26") -> {
                    calendars++
                    assertEquals(id, AgentResultStatus.ANSWER, result.status)
                    assertEquals(id, 1, reads.calls)
                    assertTrue(id, reads.scope != null)
                }
                annotated[3] == "read" -> {
                    memories++
                    assertEquals(id, 1, reads.calls)
                    assertEquals(id, null, reads.scope)
                }
                else -> {
                    forbidden++
                    assertEquals(id, 0, reads.calls)
                    assertEquals(id, 0, contexts.calls)
                    val forcedReads = Reads()
                    val forcedContexts = Contexts()
                    pipeline(forcedReads, forcedContexts, forced = true).process(request(text))
                    assertEquals(id, 0, forcedReads.calls)
                    assertEquals(id, 0, forcedContexts.calls)
                }
            }
        }
        assertEquals(4, calendars)
        assertEquals(4, memories)
        assertEquals(42, forbidden)
    }

    @org.junit.Ignore("Archived Phase 0: requires historical docs/evaluation fixtures.")
    @Test fun exposedBenchmarkClarificationsRemainBlockedWithCalendarEnabled(): Unit = runBlocking {
        val directory = File(System.getProperty("metis.projectDir"),
            "../docs/evaluation/phase15/candidate_v12")
        val expected = File(directory, "regression-expectations.tsv").readLines().drop(1)
            .associate { it.split('\t').let { fields -> fields[0] to fields } }
        var blocked = 0
        File(directory, "regression-input.tsv").readLines().drop(1).forEach { line ->
            val fields = line.split('\t')
            val annotation = requireNotNull(expected[fields[0]])
            if (annotation[1] != "exposed" || annotation[3] !in setOf("clarify", "deny")) return@forEach
            val text = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
            val reads = Reads()
            val contexts = Contexts()
            pipeline(reads, contexts).process(request(text))
            assertEquals(fields[0], 0, reads.calls)
            assertEquals(fields[0], 0, contexts.calls)
            val forcedReads = Reads()
            val forcedContexts = Contexts()
            pipeline(forcedReads, forcedContexts, forced = true).process(request(text))
            assertEquals(fields[0], 0, forcedReads.calls)
            assertEquals(fields[0], 0, forcedContexts.calls)
            blocked++
        }
        assertTrue(blocked >= 14)
    }

    @org.junit.Ignore("Archived Phase 0: requires historical docs/evaluation fixtures.")
    @Test fun otherExposedCalendarRequestsStillNeedClarification(): Unit = runBlocking {
        val directory = File(System.getProperty("metis.projectDir"),
            "../docs/evaluation/phase15/candidate_v12")
        val expected = File(directory, "regression-expectations.tsv").readLines().drop(1)
            .associate { it.split('\t').let { fields -> fields[0] to fields } }
        var blocked = 0
        File(directory, "regression-input.tsv").readLines().drop(1).forEach { line ->
            val fields = line.split('\t')
            val annotation = requireNotNull(expected[fields[0]])
            if (annotation[1] != "exposed" || annotation[2] != "CHECK_CALENDAR" ||
                annotation[3] != "unsupported") return@forEach
            val text = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
            val reads = Reads()
            val contexts = Contexts()
            pipeline(reads, contexts).process(request(text))
            assertEquals(fields[0], 0, reads.calls)
            assertEquals(fields[0], 0, contexts.calls)
            val forcedReads = Reads()
            val forcedContexts = Contexts()
            pipeline(forcedReads, forcedContexts, forced = true).process(request(text))
            assertEquals(fields[0], 0, forcedReads.calls)
            assertEquals(fields[0], 0, forcedContexts.calls)
            blocked++
        }
        assertEquals(23, blocked)
    }

    @Test fun windowAndEditorRejectInvalidOrAmbiguousTimes() {
        val tomorrow = valid[0].second.window(time, zone)
        assertEquals(Instant.parse("2026-10-13T06:30:00Z").toEpochMilli(), tomorrow.first)
        assertEquals(Instant.parse("2026-10-13T12:30:00Z").toEpochMilli(), tomorrow.second)
        val week = valid[2].second.window(time, zone)
        assertEquals(time.toEpochMilli(), week.first)
        assertEquals(Instant.parse("2026-10-18T18:30:00Z").toEpochMilli(), week.second)
        val draft = CalendarDraft(title = "Meeting with Rohit", date = "2026-10-13", start = "14:00",
            endDate = "2026-10-13", end = "15:00", details = "research project")
        val event = buildEvent(draft, zone)
        assertEquals("Asia/Kolkata", event.zoneId)
        assertTrue(event.reason.contains("research project"))
        assertEquals(dev.metis.agent.domain.storage.ScheduleStatus.ACCEPTED, event.status)
        assertFalse(event.endAt <= event.startAt)
        assertThrowsIllegal(draft.copy(end = "13:00"), zone)
        assertThrowsIllegal(draft.copy(date = "2026-02-30"), zone)
        assertThrowsIllegal(draft.copy(start = "25:00"), zone)
        assertThrowsIllegal(draft.copy(date = "2026-03-08", endDate = "2026-03-08", start = "02:30"),
            ZoneId.of("America/New_York"))
        assertThrowsIllegal(draft.copy(date = "2026-11-01", endDate = "2026-11-01", start = "01:30"),
            ZoneId.of("America/New_York"))
    }

    private fun assertThrowsIllegal(draft: CalendarDraft, zone: ZoneId) {
        try { buildEvent(draft, zone); throw AssertionError("Expected invalid event") }
        catch (_: IllegalArgumentException) { /* invalid input */ }
        catch (_: java.time.DateTimeException) { /* invalid date or time */ }
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, time)

    private fun pipeline(reads: Reads, contexts: Contexts, forced: Boolean = false,
                         extractor: EntityExtractor? = null) = LocalAgentOrchestrator(
        if (forced) object : IntentClassifier {
            override suspend fun classify(request: AgentRequest) = IntentPrediction(AgentIntent.CHECK_CALENDAR, 1.0)
        } else EnglishRules(),
        extractor ?: if (forced) object : EntityExtractor {
            override suspend fun extract(request: AgentRequest): List<ExtractedEntity> = emptyList()
        } else EnglishRules(),
        contexts, LanguageSpecialist(reads, localCalendar = true), ProposalPolicy(), localCalendar = true)

    private inner class Contexts : ContextBuilder {
        var calls = 0
        override suspend fun snapshot(request: AgentRequest): ResolvedContext {
            calls++
            return ResolvedContext(request.timestamp, zone, AutonomyLevel.ANSWER_ONLY,
                CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))
        }
    }

    private class Reads : AgentReadPort {
        var calls = 0
        var scope: CalendarReadScope? = null
        override suspend fun tasks(): String { calls++; return "Synthetic tasks" }
        override suspend fun memories(query: String, at: Long): String { calls++; return "Synthetic memories" }
        override suspend fun promises(person: String): SpecialistResult {
            calls++; return SpecialistResult.Answer("Synthetic promises")
        }
        override suspend fun calendar(scope: CalendarReadScope, now: Long, zone: ZoneId): SpecialistResult {
            calls++
            this.scope = scope
            return SpecialistResult.Answer("Synthetic local calendar")
        }
    }
}
