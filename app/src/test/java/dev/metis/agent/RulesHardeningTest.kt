package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedExternalActionStore
import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.AcceptedReminderStore
import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.Action
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AlarmResolution
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.MessageAction
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.DurationParser
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.ExternalActionLauncher
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ReminderHistoryEntry
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.RevisionTarget
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactMatcher
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.ml.NluIntentClassifier
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RulesHardeningTest {

    private val now = Instant.parse("2026-11-01T12:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(now, zone)
    private val session = UUID.randomUUID()

    @Before
    fun setUp() {
        val assetFile = findRepoFile("app/src/main/assets/nlu_intent_model.json")
        if (assetFile.exists()) {
            val loaded = assetFile.inputStream().use { NluIntentClassifier.fromInputStream(it) }
            NluIntentClassifier.resetCacheForTesting(loaded)
        }
    }

    @After
    fun tearDown() {
        NluIntentClassifier.resetCacheForTesting(null)
    }

    // --- Part A: Duration and Unit Parsing ---

    @Test
    fun durationParserAcceptsAllSpecifiedFormats() {
        val testCases = mapOf(
            "10 min" to 600L,
            "10min" to 600L,
            "5 mins" to 300L,
            "30 sec" to 30L,
            "30s" to 30L,
            "2 hrs" to 7200L,
            "2h" to 7200L,
            "1 hr" to 3600L,
            "10m" to 600L,
            "1.5 hr" to 5400L,
            "half an hour" to 1800L,
            "quarter hour" to 900L,
            "an hour" to 3600L,
            "a minute" to 60L,
            "aadha ghanta" to 1800L,
            "ek ghanta" to 3600L,
            "das minute" to 600L,
            "10 minutes" to 600L,
            "45 seconds" to 45L
        )

        for ((raw, expected) in testCases) {
            val parsed = DurationParser.parseToSeconds(raw)
            assertEquals("Failed for: '$raw'", expected, parsed)
        }
    }

    @Test
    fun durationParserRejectsZeroAndAbsurdValues() {
        assertNull("Zero seconds must be rejected", DurationParser.parseToSeconds("0 sec"))
        assertNull("Zero minutes must be rejected", DurationParser.parseToSeconds("0 min"))
        assertNull("Zero hours must be rejected", DurationParser.parseToSeconds("0 hr"))
        assertNull("Absurd > 86400s must be rejected", DurationParser.parseToSeconds("25 hrs"))
        assertNull("Absurd > 86400s must be rejected", DurationParser.parseToSeconds("2000 min"))
    }

    @Test
    fun timerRuleProposalsEndToEnd() = runBlocking {
        val harness = createHarness()
        val phrases = listOf(
            "set timer 10 min",
            "timer 5 mins",
            "timer 30 sec",
            "timer 2 hrs",
            "timer for 1 hr",
            "10m timer",
            "1.5 hr timer",
            "timer for aadha ghanta",
            "set a timer for 10 minutes"
        )
        for (phrase in phrases) {
            val result = harness.agent.process(harness.request(phrase))
            assertEquals("Expected proposal for '$phrase'", AgentResultStatus.PROPOSAL, result.status)
            assertEquals(1, result.proposals.size)
            assertTrue(result.proposals.single().action is CreateTimerAction)
            assertEquals(0, harness.launcher.launchedTimers.size)
            // Verify accept requirement
            harness.agent.accept(result.proposals.single())
            assertEquals(1, harness.launcher.launchedTimers.size)
            harness.launcher.launchedTimers.clear()
        }
    }

    @Test
    fun alarmResolutionResolvesExplicitAndClarifiesAmbiguous() {
        val request = AgentRequest(UUID.randomUUID(), "6am alarm", InputSource.TEXT, now)
        val resolved6am = AlarmResolution.resolve(request, null, "6am", zone)
        assertNotNull(resolved6am)

        val resolvedSubah = AlarmResolution.resolve(
            AgentRequest(UUID.randomUUID(), "subah 6 baje utha dena", InputSource.TEXT, now),
            null, "6", zone
        )
        assertNotNull(resolvedSubah)

        // Bare hour without AM/PM or contextual indicator must clarify
        val bare6 = AlarmResolution.resolve(
            AgentRequest(UUID.randomUUID(), "wake me at 6", InputSource.TEXT, now),
            null, "6", zone
        )
        assertNull("Bare 6 without AM/PM must be null to trigger clarification", bare6)

        val bareAlarm6 = AlarmResolution.resolve(
            AgentRequest(UUID.randomUUID(), "alarm at 6", InputSource.TEXT, now),
            null, "6", zone
        )
        assertNull("Bare alarm at 6 must trigger clarification", bareAlarm6)

        val kal8 = AlarmResolution.resolve(
            AgentRequest(UUID.randomUUID(), "kal 8 baje alarm laga do", InputSource.TEXT, now),
            "kal", "8", zone
        )
        assertNull("Kal 8 baje without AM/PM must trigger clarification", kal8)
    }

    // --- Part B: Hinglish Verb Patterns ---

    @Test
    fun hinglishVerbPatternsClassifiedCorrectly() = runBlocking {
        val rules = EnglishRules(zone = { zone })

        // Reminders
        listOf(
            "rahul ko kal 8 baje call karna yaad dilao",
            "kal yaad dila dena medicine lene ke liye",
            "remind kar do meeting ke liye"
        ).forEach {
            assertEquals("Failed on reminder: $it", AgentIntent.CREATE_REMINDER, rules.parse(AgentRequest(UUID.randomUUID(), it, InputSource.TEXT, now)).intent)
        }

        // Task create
        listOf(
            "gym jaana hai task bana do",
            "doodh lana hai list mein daal do",
            "dsp assignment add kar do",
            "add task finish DSP assignment"
        ).forEach {
            assertEquals("Failed on task create: $it", AgentIntent.CREATE_TASK, rules.parse(AgentRequest(UUID.randomUUID(), it, InputSource.TEXT, now)).intent)
        }

        // Call
        listOf(
            "Rahul ko call karo",
            "Mom ko phone laga do",
            "Priya ko call laga do"
        ).forEach {
            assertEquals("Failed on call: $it", AgentIntent.CALL_PERSON, rules.parse(AgentRequest(UUID.randomUUID(), it, InputSource.TEXT, now)).intent)
        }

        // Message
        listOf(
            "Rahul ko message kar do I will be late",
            "Mom ko text bhej do coming home",
            "Rahul ko bata do ki slides are ready"
        ).forEach {
            assertEquals("Failed on message: $it", AgentIntent.SEND_MESSAGE, rules.parse(AgentRequest(UUID.randomUUID(), it, InputSource.TEXT, now)).intent)
        }

        // Delete
        listOf(
            "DSP assignment hata do",
            "DSP assignment delete kar do"
        ).forEach {
            assertEquals("Failed on delete: $it", AgentIntent.DELETE_TASK, rules.parse(AgentRequest(UUID.randomUUID(), it, InputSource.TEXT, now)).intent)
        }

        // Complete
        listOf(
            "DSP assignment ho gaya",
            "lab report kar liya",
            "DSP assignment done kar do"
        ).forEach {
            assertEquals("Failed on complete: $it", AgentIntent.COMPLETE_TASK, rules.parse(AgentRequest(UUID.randomUUID(), it, InputSource.TEXT, now)).intent)
        }
    }

    // --- Part C: Negation Guard ---

    @Test
    fun negationGuardCatchesAll19Phrases() {
        val negationFile = findRepoFile("ml/reports/negation_phrases_for_guard_tests.csv")
        assertTrue("negation_phrases_for_guard_tests.csv must exist", negationFile.exists())
        val lines = negationFile.readLines().drop(1).filter { it.isNotBlank() }
        assertEquals(19, lines.size)

        for (line in lines) {
            val text = if (line.startsWith("\"")) {
                line.substring(1, line.indexOf("\",")).replace("\"\"", "\"")
            } else {
                line.substringBefore(",")
            }
            assertTrue("Expected isNegated=true for: '$text'", EnglishRules.isNegated(text))
            val parse = EnglishRules(zone = { zone }).parse(AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, now))
            assertTrue("Expected parse.negated=true for: '$text'", parse.negated)
        }
    }

    @Test
    fun negationGuardDoesNotOverBlockValidPhrases() {
        assertFalse("delete the DSP task must NOT be negated", EnglishRules.isNegated("delete the DSP task"))
        assertFalse("don't forget to call Rahul must NOT be negated", EnglishRules.isNegated("don't forget to call Rahul"))
        assertFalse("remind me not to forget my charger must NOT be negated", EnglishRules.isNegated("remind me not to forget my charger"))
    }

    // --- Part D: Fix List Rendering & Missing Fixtures ---

    @Test
    fun contactDisambiguationRendersCandidateNames() = runBlocking {
        val twoRahuls = listOf(
            ContactCandidate("1", "Rahul K", phoneNumber = "9876543210"),
            ContactCandidate("2", "Rahul S", phoneNumber = "9876543211")
        )
        val harnessTwo = createHarness(contacts = twoRahuls)
        val resultTwo = harnessTwo.agent.process(harnessTwo.request("call Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, resultTwo.status)
        assertTrue("Must render matching names", resultTwo.message.contains("Rahul K or Rahul S"))
        assertFalse("Must NOT include refinement hint for 2 candidates", resultTwo.message.contains("type a fuller name"))

        // More than 5 candidates
        val sixRahuls = (1..6).map {
            ContactCandidate("$it", "Rahul $it", phoneNumber = "987654321$it")
        }
        val harnessSix = createHarness(contacts = sixRahuls)
        val resultSix = harnessSix.agent.process(harnessSix.request("call Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, resultSix.status)
        assertTrue("Must render first 5 names", resultSix.message.contains("Rahul 1 or Rahul 2 or Rahul 3 or Rahul 4 or Rahul 5"))
        assertTrue("Must add 'type a fuller name' when >5 candidates", resultSix.message.contains("(type a fuller name)"))
    }

    @Test
    fun seededHarnessExercisesProposalPathForDeviceSheetRows() = runBlocking {
        val harness = createHarness()

        // Row 15: "move my DSP task to tomorrow"
        val r15 = harness.agent.process(harness.request("move my DSP task to tomorrow"))
        assertTrue("Row 15 proposal or confirmation step expected", r15.status in setOf(AgentResultStatus.PROPOSAL, AgentResultStatus.FOLLOW_UP))
        assertFalse("Must not be 'No matching open task found'", r15.message.contains("No matching open task found"))
        assertTrue("Recognized DSP assignment", r15.message.contains("DSP assignment") || r15.proposals.isNotEmpty())

        // Row 16: "mark lab report done"
        val r16 = harness.agent.process(harness.request("mark lab report done"))
        assertTrue("Row 16 proposal or confirmation step expected", r16.status in setOf(AgentResultStatus.PROPOSAL, AgentResultStatus.FOLLOW_UP))
        assertFalse("Must not be 'No matching open task found'", r16.message.contains("No matching open task found"))
        assertTrue("Recognized lab report", r16.message.contains("lab report") || r16.proposals.isNotEmpty())
        assertEquals(0, harness.taskStore.writes)

        // Row 17: "make DSP assignment high priority"
        val r17 = harness.agent.process(harness.request("make DSP assignment high priority"))
        assertTrue("Row 17 proposal or confirmation step expected", r17.status in setOf(AgentResultStatus.PROPOSAL, AgentResultStatus.FOLLOW_UP))
        assertFalse("Must not be 'No matching open task found'", r17.message.contains("No matching open task found"))
        assertTrue("Recognized DSP assignment", r17.message.contains("DSP assignment") || r17.proposals.isNotEmpty())

        // Row 18: "rename DSP assignment to DSP lab 3"
        val r18 = harness.agent.process(harness.request("rename DSP assignment to DSP lab 3"))
        assertTrue("Row 18 proposal or confirmation step expected", r18.status in setOf(AgentResultStatus.PROPOSAL, AgentResultStatus.FOLLOW_UP))
        assertFalse("Must not be 'No matching open task found'", r18.message.contains("No matching open task found"))
        assertTrue("Recognized DSP assignment", r18.message.contains("DSP assignment") || r18.proposals.isNotEmpty())

        // Row 27: "delete the DSP task"
        val r27 = harness.agent.process(harness.request("delete the DSP task"))
        assertTrue("Row 27 proposal or confirmation step expected", r27.status in setOf(AgentResultStatus.PROPOSAL, AgentResultStatus.FOLLOW_UP))
        assertFalse("Must not be 'No matching open task found'", r27.message.contains("No matching open task found"))
        assertTrue("Recognized DSP assignment", r27.message.contains("DSP assignment") || r27.proposals.isNotEmpty())

        // Row 34: "delet the dsp taks" (typo)
        val r34 = harness.agent.process(harness.request("delet the dsp taks"))
        assertFalse("Must not be 'No matching open task found'", r34.message.contains("No matching open task found"))
    }

    // --- Part E: 60-phrase evaluation runner ---

    private fun findRepoFile(relative: String): File = listOf(
        File(relative),
        File("../$relative"),
        File("../../$relative")
    ).firstOrNull { it.exists() } ?: File(relative)

    @Test
    fun runDevice60Evaluation() = runBlocking {
        val harness = createHarness()
        val deviceFile = findRepoFile("ml/data/device_test_60.csv")
        assertTrue("device_test_60.csv must exist", deviceFile.exists())
        val lines = deviceFile.readLines().drop(1).filter { it.isNotBlank() }

        println("\n=== 60-PHRASE DEVICE EVALUATION REPORT ===")
        println("ID | Phrase | Status | Outcome / Proposal | Notes")
        println("---|---|---|---|---")

        var proposalCount = 0
        var clarificationCount = 0
        var deniedCount = 0
        var answerCount = 0

        for ((idx, line) in lines.withIndex()) {
            val parts = line.split(",")
            val phrase = parts[0]
            val expected = if (parts.size > 1) parts[1] else ""
            val result = harness.agent.process(harness.request(phrase))

            val outcome = when (result.status) {
                AgentResultStatus.PROPOSAL -> {
                    proposalCount++
                    "PROPOSAL (${result.proposals.firstOrNull()?.action?.type ?: "UNKNOWN"})"
                }
                AgentResultStatus.FOLLOW_UP -> {
                    clarificationCount++
                    "CLARIFICATION (${result.followUp?.missingFields?.joinToString() ?: "choice"})"
                }
                AgentResultStatus.DENIED -> {
                    deniedCount++
                    "NO-ACTION (DENIED/NEGATED)"
                }
                AgentResultStatus.ANSWER -> {
                    answerCount++
                    "ANSWER (${result.message.take(30)}...)"
                }
                else -> result.status.name
            }

            println("${idx + 1} | $phrase | ${result.status} | $outcome | Expected: $expected")
        }

        println("=== SUMMARY: proposals=$proposalCount, clarifications=$clarificationCount, denied=$deniedCount, answers=$answerCount ===")
    }

    // --- Test Harness Helper ---

    private fun createHarness(
        contacts: List<ContactCandidate> = listOf(
            ContactCandidate("1", "Rahul K", phoneNumber = "9876543210"),
            ContactCandidate("2", "Rahul S", phoneNumber = "9876543211"),
            ContactCandidate("3", "Raghav 1", phoneNumber = "9876543212"),
            ContactCandidate("4", "Raghav 2", phoneNumber = "9876543213")
        )
    ): TestHarness {
        val resolver = FakeContactsResolver(contacts)
        val launcher = TrackingExternalActionLauncher()
        val externalStore = TrackingExternalStore(launcher)
        val taskStore = TestSeededTaskStore()
        val promiseStore = TestPromiseStore()
        val reminderStore = TestReminderStore()

        val taskAgent = ConfirmedTaskAgent(
            reads = TestSeededReads(),
            store = taskStore,
            zone = { zone }
        )
        val reminderAgent = ConfirmedReminderAgent(
            fallback = taskAgent,
            store = reminderStore,
            zone = { zone },
            clock = clock,
            contacts = resolver
        )
        val promiseAgent = ConfirmedPromiseAgent(
            fallback = reminderAgent,
            store = promiseStore,
            zone = { zone },
            clock = clock,
            contacts = resolver
        )
        val externalAgent = ConfirmedExternalActionAgent(
            fallback = promiseAgent,
            store = externalStore,
            contacts = resolver,
            zone = { zone },
            clock = clock
        )

        return TestHarness(externalAgent, taskStore, promiseStore, reminderStore, externalStore, launcher, resolver)
    }

    private inner class TestHarness(
        val agent: ConfirmedExternalActionAgent,
        val taskStore: TestSeededTaskStore,
        val promiseStore: TestPromiseStore,
        val reminderStore: TestReminderStore,
        val externalStore: TrackingExternalStore,
        val launcher: TrackingExternalActionLauncher,
        val contacts: ContactsResolver
    ) {
        fun request(text: String) = AgentRequest(
            id = UUID.randomUUID(),
            text = text,
            source = InputSource.TEXT,
            timestamp = now,
            conversationId = session
        )
    }

    private inner class TestSeededReads : AgentReadPort {
        override suspend fun tasks(): String = "Open tasks: DSP assignment, lab report"
        override suspend fun memories(query: String, at: Long): String =
            if (query.contains("RF", ignoreCase = true)) "RF project is pending circuit simulation." else "No memories."
        override suspend fun promises(person: String): SpecialistResult =
            if (person.contains("Rahul", ignoreCase = true)) SpecialistResult.Answer("You promised Rahul the slides by Friday.")
            else SpecialistResult.Answer("No promises found for $person.")
    }

    private inner class TestSeededTaskStore : AcceptedTaskStore {
        var writes = 0
        private val seededTasks = listOf("DSP assignment", "lab report")

        override suspend fun resolveCompletion(title: String): CompletionSelection {
            val cleanQuery = cleanTaskTitle(title)
            val match = seededTasks.firstOrNull { it.equals(title, ignoreCase = true) }
                ?: seededTasks.firstOrNull {
                    val cleanTarget = cleanTaskTitle(it)
                    cleanTarget.equals(cleanQuery, ignoreCase = true) ||
                        cleanTarget.contains(cleanQuery, ignoreCase = true) ||
                        cleanQuery.contains(cleanTarget, ignoreCase = true)
                }
            return match?.let {
                CompletionSelection.Selected(it, RevisionTarget(EntityReference(MemoryEntityType.TASK, UUID.randomUUID()), 1))
            } ?: CompletionSelection.Unavailable
        }

        private fun cleanTaskTitle(value: String): String = value
            .replace(Regex("(?i)^(?:the|my|a|an)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:task|tasks|taks)$"), "")
            .trim()

        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )

        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            return ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.TASK,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
                "Task operation executed"
            )
        }

        override suspend fun retry(actionId: UUID) = error("Not used")
        override suspend fun cancelPending(actionId: UUID) = Unit
        override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
        override fun observeHistory(): Flow<List<TaskActionHistoryEntry>> = flowOf(emptyList())
    }

    private inner class TestPromiseStore : AcceptedPromiseStore {
        val writes = mutableListOf<Action>()
        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )
        override suspend fun matchingPeople(name: String) = emptyList<PromisePersonMatch>()
        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes.add(proposal.action)
            return ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.CREATE_PROMISE,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
                "Promise saved"
            )
        }
    }

    private inner class TestReminderStore : AcceptedReminderStore {
        val writes = mutableListOf<Action>()
        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE, Capability.REMINDER_SCHEDULE, Capability.NOTIFICATIONS)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )
        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes.add(proposal.action)
            return ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.CREATE_REMINDER,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
                "Reminder saved"
            )
        }
        override suspend fun retry(actionId: UUID) = error("Not used")
        override suspend fun cancel(actionId: UUID) = UndoResult.NOT_SUPPORTED
        override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
        override suspend fun reconcile() = Unit
        override fun history(): Flow<List<ReminderHistoryEntry>> = flowOf(emptyList())
    }

    private class TrackingExternalActionLauncher : ExternalActionLauncher {
        val launchedTimers = mutableListOf<String>()
        val launchedAlarms = mutableListOf<String>()
        val launchedCalls = mutableListOf<String>()
        val launchedMessages = mutableListOf<String>()

        override fun launchTimer(lengthSeconds: Int, message: String): ExternalLaunchResult {
            launchedTimers.add("$lengthSeconds:$message")
            return ExternalLaunchResult.Success
        }

        override fun launchAlarm(hour: Int, minutes: Int, message: String): ExternalLaunchResult {
            launchedAlarms.add("$hour:$minutes:$message")
            return ExternalLaunchResult.Success
        }

        override fun launchDial(phoneNumber: String): ExternalLaunchResult {
            launchedCalls.add(phoneNumber)
            return ExternalLaunchResult.Success
        }

        override fun launchSendTo(phoneNumber: String, body: String): ExternalLaunchResult {
            launchedMessages.add("$phoneNumber:$body")
            return ExternalLaunchResult.Success
        }
    }

    private inner class TrackingExternalStore(private val launcher: ExternalActionLauncher) :
        AcceptedExternalActionStore {
        var writes = 0

        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE, Capability.TIMER, Capability.CLOCK_ALARM, Capability.DIALER, Capability.MESSAGE_COMPOSER)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )

        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            val action = proposal.action
            if (action is CreateTimerAction) {
                launcher.launchTimer(action.durationSeconds.toInt(), action.label)
            }
            return ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, proposal.action.type,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_PLATFORM),
                "External action launched"
            )
        }
    }
}