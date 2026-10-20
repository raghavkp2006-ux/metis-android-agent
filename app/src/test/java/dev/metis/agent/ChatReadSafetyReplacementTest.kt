package dev.metis.agent

import dev.metis.agent.domain.agent.Action
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AgentSpecialist
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
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.ParsedRequest
import dev.metis.agent.domain.agent.PolicyEngine
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.agent.ReadDateV15
import dev.metis.agent.domain.agent.ReadSafetyV12
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.ValidationResult
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic replacements for the archived read-safety corpus tests. No file or live clock is used. */
class ChatReadSafetyReplacementTest {
    private val now = Instant.parse("2026-10-20T06:30:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val labels = ReadSafetyV12.intents
    private val task = "Show my tasks"
    private val memory = "What did I write down about ceramic glazing?"
    private val calendar = "Show my calendar"

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, now)
    private fun context(request: AgentRequest, zone: ZoneId = this.zone) = ResolvedContext(
        request.timestamp, zone, AutonomyLevel.ANSWER_ONLY,
        CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))

    private class Ports(private val zone: ZoneId) : AgentReadPort, ContextBuilder, PolicyEngine {
        var contexts = 0
        var specialists = 0
        var reads = 0
        var reviews = 0
        var query = ""
        var scope: ReadScopeV15? = null
        var calendarScope: CalendarReadScope? = null
        var receivedNow: Long? = null
        var receivedZone: ZoneId? = null
        private fun read(value: String, at: Long? = null, from: ZoneId? = null): SpecialistResult {
            reads++
            query = value
            receivedNow = at
            receivedZone = from
            return SpecialistResult.Answer("Synthetic saved records")
        }
        override suspend fun snapshot(request: AgentRequest): ResolvedContext {
            contexts++
            return ResolvedContext(request.timestamp, zone, AutonomyLevel.ANSWER_ONLY,
                CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))
        }
        override fun review(action: Action, context: ResolvedContext): ValidationResult {
            reviews++
            error("A read must never propose an action")
        }
        override suspend fun tasks() = read("tasks").let { "Synthetic saved records" }
        override suspend fun memories(query: String, at: Long) = read(query, at).let { "Synthetic saved records" }
        override suspend fun promises(person: String) = read(person)
        override suspend fun calendar(scope: CalendarReadScope, now: Long, zone: ZoneId): SpecialistResult {
            calendarScope = scope
            return read(scope.subject, now, zone)
        }
        override suspend fun tasks(scope: ReadScopeV15.Tasks, now: Long, zone: ZoneId): SpecialistResult {
            this.scope = scope
            return read(scope.subject, now, zone)
        }
        override suspend fun memories(scope: ReadScopeV15.Memories, now: Long, zone: ZoneId): SpecialistResult {
            this.scope = scope
            return read(scope.subject, now, zone)
        }
        override suspend fun promises(scope: ReadScopeV15.Promises, now: Long, zone: ZoneId): SpecialistResult {
            this.scope = scope
            return read(scope.subject.ifEmpty { scope.person }, now, zone)
        }
        override suspend fun calendar(scope: ReadScopeV15.Calendar, now: Long, zone: ZoneId): SpecialistResult {
            this.scope = scope
            return read(scope.subject.ifEmpty { scope.person }, now, zone)
        }
    }

    private fun pipeline(ports: Ports, candidate: ReadCandidate = ReadCandidate.V16,
                         label: AgentIntent? = null, extractor: EntityExtractor? = null,
                         zone: ZoneId = this.zone): LocalAgentOrchestrator {
        val rules = EnglishRules(candidate) { zone }
        val specialist = LanguageSpecialist(ports, true, candidate)
        val counted = object : AgentSpecialist {
            override suspend fun respond(request: ParsedRequest): SpecialistResult {
                ports.specialists++
                return specialist.respond(request)
            }
        }
        val classifier = label?.let { forced -> object : IntentClassifier {
            override suspend fun classify(request: AgentRequest) = IntentPrediction(forced, 1.0)
        } } ?: rules
        return LocalAgentOrchestrator(classifier, extractor ?: rules, ports, counted, ports,
            localCalendar = true, readCandidate = candidate)
    }

    private fun assertQuiet(ports: Ports, name: String) {
        assertEquals("$name context", 0, ports.contexts)
        assertEquals("$name specialist", 0, ports.specialists)
        assertEquals("$name read", 0, ports.reads)
        assertEquals("$name policy", 0, ports.reviews)
    }

    private fun assertNoAction(result: AgentResult, name: String) {
        assertTrue("$name proposals", result.proposals.isEmpty())
        assertTrue("$name actions", result.completedActions.isEmpty())
        assertTrue("$name status", result.status != AgentResultStatus.PROPOSAL)
    }

    private suspend fun allowed(text: String, intent: AgentIntent, candidate: ReadCandidate,
                                expectedScope: ReadScopeV15? = null, expectedQuery: String? = null): Ports {
        val input = request(text)
        val parsed = EnglishRules(candidate) { zone }.parse(input)
        assertEquals(text, intent, parsed.intent)
        assertEquals(text, expectedScope, parsed.readScope)
        val ports = Ports(zone)
        val answer = pipeline(ports, candidate).process(input)
        assertEquals(text, AgentResultStatus.ANSWER, answer.status)
        assertEquals(text, 1, ports.contexts)
        assertEquals(text, 1, ports.specialists)
        assertEquals(text, 1, ports.reads)
        assertEquals(text, 0, ports.reviews)
        assertNoAction(answer, text)
        if (expectedQuery != null) assertEquals(text, expectedQuery, ports.query)
        return ports
    }

    private suspend fun rejected(text: String, candidate: ReadCandidate = ReadCandidate.V16,
                                 allLabels: Boolean = true) {
        val input = request(text)
        val forced = if (allLabels) labels.map { it } else listOf<AgentIntent?>(null)
        for (label in forced) {
            val ports = Ports(zone)
            val answer = pipeline(ports, candidate, label).process(input)
            assertQuiet(ports, "$text/$label")
            assertNoAction(answer, "$text/$label")
            if (label != null) {
                val parsed = EnglishRules(candidate) { zone }.parse(input)
                val direct = LanguageSpecialist(ports, true, candidate).respond(ParsedRequest(input,
                    IntentPrediction(label, 1.0), parsed.entities, context(input), parsed.readScope))
                assertTrue("$text/$label direct", direct is SpecialistResult.Clarification)
                assertEquals("$text/$label direct read", 0, ports.reads)
            }
        }
    }

    private suspend fun forged(text: String, candidate: ReadCandidate, values: List<ExtractedEntity>) {
        val input = request(text)
        val parsed = EnglishRules(candidate) { zone }.parse(input)
        val extractor = object : EntityExtractor {
            override suspend fun extract(request: AgentRequest) = values
        }
        val ports = Ports(zone)
        val result = pipeline(ports, candidate, parsed.intent, extractor).process(input)
        assertEquals(text, AgentResultStatus.FOLLOW_UP, result.status)
        assertQuiet(ports, text)
        assertNoAction(result, text)
        val direct = runCatching {
            LanguageSpecialist(ports, true, candidate).respond(ParsedRequest(input,
                parsed.prediction, values, context(input), parsed.readScope))
        }
        assertTrue(text, direct.getOrNull() is SpecialistResult.Clarification ||
            direct.exceptionOrNull() is IllegalArgumentException)
        assertEquals(text, 0, ports.reads)
    }

    @Test fun calendarBatchDispatchesExactKindsAndCounts(): Unit = runBlocking {
        val calendars = listOf(
            calendar to CalendarReadScope(CalendarReadScope.Kind.EVENTS),
            "When am I free tomorrow?" to CalendarReadScope(CalendarReadScope.Kind.AVAILABILITY,
                CalendarReadScope.Day.TOMORROW),
            "What meetings are on my calendar?" to CalendarReadScope(CalendarReadScope.Kind.MEETINGS),
            "Bring up my appointments" to CalendarReadScope(CalendarReadScope.Kind.APPOINTMENTS))
        calendars.forEach { (text, scope) ->
            assertEquals(text, scope, allowed(text, AgentIntent.CHECK_CALENDAR,
                ReadCandidate.V12).calendarScope)
        }
        val memories = listOf(memory to "ceramic glazing",
            "What did I note about acoustic resonance?" to "acoustic resonance",
            "What did I save about river erosion?" to "river erosion",
            "What do you know about DSP exam?" to "DSP exam")
        memories.forEach { (text, query) -> allowed(text, AgentIntent.CHECK_MEMORY,
            ReadCandidate.V12, expectedQuery = query) }
    }

    @Test fun calendarClarificationsBlockForcedLabelsBeforeContext(): Unit = runBlocking {
        listOf("Don't show my calendar", "Show my calendar and delete an event",
            "When am I free tomorrow after 3 pm?").forEach { rejected(it, ReadCandidate.V12) }
    }

    @Test fun calendarIncompleteAndAmbiguousRequestsNeedClarification(): Unit = runBlocking {
        listOf("Have any meetings with him this week?", "When am I free?",
            "Show my calendar for Friday", "Which meetings are next and create one?")
            .forEach { rejected(it, ReadCandidate.V12) }
    }

    @Test fun v12SyntheticScopesAndEveryForcedUnsafeLabel(): Unit = runBlocking {
        allowed(task, AgentIntent.CHECK_TASKS, ReadCandidate.V12)
        allowed(memory, AgentIntent.CHECK_MEMORY, ReadCandidate.V12, expectedQuery = "ceramic glazing")
        listOf("Show my tasks and delete one", "Search memory for ceramic glazing or optics")
            .forEach { rejected(it, ReadCandidate.V12) }
        val prefix = "what the doctor at the " + "c".repeat(180) + " said about "
        val limit = prefix + "g".repeat(256 - prefix.length)
        allowed("Did I write down $limit", AgentIntent.CHECK_MEMORY, ReadCandidate.V12,
            expectedQuery = limit)
        rejected("Did I write down ${limit}q", ReadCandidate.V12)
    }

    @Test fun v12ForgedTypesSpansAndCompleteQueriesCannotRead(): Unit = runBlocking {
        val entity = EnglishRules(ReadCandidate.V12).parse(request(memory)).entities.single()
        listOf(listOf(entity.copy(type = EntityKind.PERSON)),
            emptyList(), listOf(entity, entity),
            listOf(entity.copy(rawValue = "x".repeat(entity.rawValue.length))),
            listOf(entity.copy(normalizedValue = NormalizedValue.Text("ceramic glazing yesterday"))),
            listOf(entity.copy(startOffset = entity.startOffset + 1, endOffset = entity.endOffset + 1)))
            .forEach { forged(memory, ReadCandidate.V12, it) }
    }

    @Test fun v14SyntheticSafeUnsafeRowsAndForcedLabels(): Unit = runBlocking {
        allowed("What do you remember about The Who?", AgentIntent.CHECK_MEMORY,
            ReadCandidate.V14, expectedQuery = "The Who")
        listOf("What do you remember about The Who and erase it?",
            "Did I write down ceramic glazing or optics?").forEach { rejected(it, ReadCandidate.V14) }
    }

    @Test fun v14ForgedSpansTypesAndQueriesFailBeforeDispatch(): Unit = runBlocking {
        val text = "What do you remember about The Who?"
        val entity = EnglishRules(ReadCandidate.V14).parse(request(text)).entities.single()
        listOf(listOf(entity.copy(type = EntityKind.TASK)),
            listOf(entity.copy(startOffset = entity.startOffset + 1, endOffset = entity.endOffset + 1)),
            listOf(entity.copy(rawValue = "X".repeat(entity.rawValue.length))),
            listOf(entity.copy(normalizedValue = NormalizedValue.Text("The Who yesterday"))))
            .forEach { forged(text, ReadCandidate.V14, it) }
    }

    @Test fun v15TypedScopesEntitiesAndForcedLabels(): Unit = runBlocking {
        val scope = ReadScopeV15.Memories("ceramic glazing")
        val text = memory
        val entity = EnglishRules(ReadCandidate.V15).parse(request(text)).entities.single()
        assertEquals(EntityKind.MEMORY, entity.type)
        assertEquals("ceramic glazing", entity.rawValue)
        assertEquals(text.indexOf("ceramic glazing"), entity.startOffset)
        allowed(memory, AgentIntent.CHECK_MEMORY, ReadCandidate.V15, scope, "ceramic glazing")
        rejected("What did I write down about ceramic glazing and delete it?", ReadCandidate.V15)
    }

    @Test fun v15ForgedScopesAndExtractionNeverRead(): Unit = runBlocking {
        val input = request(memory)
        val parsed = EnglishRules(ReadCandidate.V15).parse(input)
        val ports = Ports(zone)
        val specialist = LanguageSpecialist(ports, true, ReadCandidate.V15)
        assertTrue(specialist.respond(ParsedRequest(input, parsed.prediction, parsed.entities,
            context(input), ReadScopeV15.Memories("optics"))) is SpecialistResult.Clarification)
        assertEquals(0, ports.reads)
        forged(memory, ReadCandidate.V15, parsed.entities.map { it.copy(type = EntityKind.PERSON) })
    }

    @Test fun v16CurrentScopesDatesAndForcedLabels(): Unit = runBlocking {
        val text = "Did I set an event for studio review?"
        val entity = EnglishRules(ReadCandidate.V16).parse(request(text)).entities.single()
        assertEquals(EntityKind.TITLE, entity.type)
        assertEquals("studio review", entity.rawValue)
        assertEquals(text.indexOf("studio review"), entity.startOffset)
        allowed(text, AgentIntent.CHECK_CALENDAR, ReadCandidate.V16,
            ReadScopeV15.Calendar(subject = "studio review"), "studio review")
        rejected("Did I set an event for studio review and cancel it?", ReadCandidate.V16)
    }

    @Test fun v16ForgedScopesDatesAndExtractionNeverRead(): Unit = runBlocking {
        val input = request("Did I set an event for studio review?")
        val parsed = EnglishRules(ReadCandidate.V16).parse(input)
        val ports = Ports(zone)
        val specialist = LanguageSpecialist(ports, true, ReadCandidate.V16)
        listOf(ReadScopeV15.Calendar(subject = "other topic"),
            ReadScopeV15.Calendar(subject = "studio review", date = ReadDateV15(ReadDateV15.Field.START,
                "tomorrow"))).forEach { scope ->
            assertTrue(specialist.respond(ParsedRequest(input, parsed.prediction, parsed.entities,
                context(input), scope)) is SpecialistResult.Clarification)
        }
        assertEquals(0, ports.reads)
        forged(input.text, ReadCandidate.V16, parsed.entities.map { it.copy(type = EntityKind.PERSON) })
    }

    @Test fun prohibitedMatrixRejectsEveryReadLabel(): Unit = runBlocking {
        listOf("Delete task: studio review", "Don't search memory for ceramic glazing",
            "Call Mira", "Create a calendar event for tomorrow", "Show my tasks and send a message")
            .forEach { rejected(it) }
    }

    @Test fun boundedEnglishIntentAndSafeOutcomeTable(): Unit = runBlocking {
        val rules = EnglishRules(ReadCandidate.V16) { zone }
        val rows = listOf(task to AgentIntent.CHECK_TASKS,
            "Remind me tomorrow at 9 am to review notes" to AgentIntent.CREATE_REMINDER,
            "Show my tasks and send a message" to AgentIntent.UNKNOWN)
        rows.forEach { (text, intent) -> assertEquals(text, intent, rules.parse(request(text)).intent) }
        rejected(rows.last().first)
    }

    @Test fun fixedLocalCalendarWindowAndAmbiguity(): Unit = runBlocking {
        val ports = allowed("When am I free tomorrow?", AgentIntent.CHECK_CALENDAR, ReadCandidate.V12)
        assertEquals(now.toEpochMilli(), ports.receivedNow)
        assertEquals(zone, ports.receivedZone)
        val scope = requireNotNull(ports.calendarScope)
        assertEquals(CalendarReadScope.Day.TOMORROW, scope.day)
        assertEquals(Instant.parse("2026-10-20T18:30:00Z").toEpochMilli(), scope.window(now, zone).first)
        val ny = ZoneId.of("America/New_York")
        val dst = ReadDateV15(ReadDateV15.Field.START, "2026-03-08").window(now, ny)
        assertEquals(23L * 60 * 60 * 1000, dst.second - dst.first)
        rejected("When am I free Friday?", ReadCandidate.V12)
    }

    @Test fun forcedLabelsAndForgedExtractionCannotBypassAdmission(): Unit = runBlocking {
        rejected("Show my tasks and delete them")
        val entity = EnglishRules(ReadCandidate.V16).parse(request(memory)).entities.single()
        forged(memory, ReadCandidate.V16, listOf(entity.copy(type = EntityKind.TITLE)))
    }

    @Test fun syntheticCoverageSpansQueriesConstraintsAndLabels(): Unit = runBlocking {
        val text = "What do you remember about The Who?"
        val entity = EnglishRules(ReadCandidate.V16).parse(request(text)).entities.single()
        assertEquals(EntityKind.MEMORY, entity.type)
        assertEquals("Who", entity.rawValue)
        assertEquals(text.indexOf("Who"), entity.startOffset)
        assertEquals(entity.startOffset + "Who".length, entity.endOffset)
        allowed(text, AgentIntent.CHECK_MEMORY, ReadCandidate.V16, ReadScopeV15.Memories("The Who"), "The Who")
        rejected("What do you remember about The Who before Friday?", ReadCandidate.V16)
    }

    @Test fun forgedTypesSpansAndQueriesCannotReachContextOrPorts(): Unit = runBlocking {
        val text = "What do you remember about The Who?"
        val entity = EnglishRules(ReadCandidate.V16).parse(request(text)).entities.single()
        listOf(listOf(entity.copy(type = EntityKind.PERSON)),
            listOf(entity.copy(startOffset = entity.startOffset + 1, endOffset = entity.endOffset + 1)),
            listOf(entity.copy(rawValue = "X".repeat(entity.rawValue.length))),
            listOf(entity.copy(normalizedValue = NormalizedValue.Text("The Who before Friday"))))
            .forEach { forged(text, ReadCandidate.V16, it) }
    }

    @Test fun referencePreservationAndWrongLabelsCannotAddAuthority(): Unit = runBlocking {
        val text = "What did I promise Zoë?"
        val entity = EnglishRules(ReadCandidate.V12).parse(request(text)).entities.single()
        assertEquals("Zoë", entity.rawValue)
        assertEquals(text.indexOf("Zoë"), entity.startOffset)
        allowed(text, AgentIntent.WHAT_DID_I_PROMISE, ReadCandidate.V12, expectedQuery = "Zoë")
        rejected("What did I promise her?", ReadCandidate.V12)
        labels.filter { it != AgentIntent.WHAT_DID_I_PROMISE }.forEach { label ->
            val ports = Ports(zone)
            val result = pipeline(ports, ReadCandidate.V12, label).process(request(text))
            assertQuiet(ports, "$text/$label")
            assertNoAction(result, "$text/$label")
        }
    }

    @Test fun supplementaryUtf16SpansAndEveryWrongLabel(): Unit = runBlocking {
        val text = "What did I promise Ravi 😀?"
        val entity = EnglishRules(ReadCandidate.V12).parse(request(text)).entities.single()
        assertEquals("Ravi 😀", entity.rawValue)
        assertEquals(text.indexOf("Ravi 😀"), entity.startOffset)
        assertEquals(entity.startOffset + "Ravi 😀".length, entity.endOffset)
        allowed(text, AgentIntent.WHAT_DID_I_PROMISE, ReadCandidate.V12, expectedQuery = "Ravi 😀")
        labels.filter { it != AgentIntent.WHAT_DID_I_PROMISE }.forEach { label ->
            val ports = Ports(zone)
            pipeline(ports, ReadCandidate.V12, label).process(request(text))
            assertQuiet(ports, "$text/$label")
        }
    }

    @Test fun syntheticNonreadsAndNegationRejectEveryReadLabel(): Unit = runBlocking {
        listOf("Create task: studio review", "Don't show my tasks", "Cancel the meeting",
            "Remind me tomorrow at 9 am to review notes", "Show my tasks then create one")
            .forEach { rejected(it) }
    }
}
