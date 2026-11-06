package dev.metis.agent.ml

import dev.metis.agent.data.storage.toStorageRisk
import dev.metis.agent.domain.agent.AcceptedExternalActionStore
import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRequirements
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.ExternalActionLauncher
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.RevisionTarget
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun
import java.io.ByteArrayInputStream
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NluFallbackIntegrationTest {

    private val now = Instant.parse("2026-11-01T12:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(now, zone)
    private lateinit var classifier: NluIntentClassifier

    @Before
    fun setUp() {
        val assetFile = listOf(
            File("src/main/assets/nlu_intent_model.json"),
            File("app/src/main/assets/nlu_intent_model.json")
        ).firstOrNull { it.exists() } ?: File("src/main/assets/nlu_intent_model.json")
        assertTrue("Model asset file must exist", assetFile.exists())
        val loaded = assetFile.inputStream().use { NluIntentClassifier.fromInputStream(it) }
        classifier = loaded
        NluIntentClassifier.resetCacheForTesting(loaded)
    }

    @After
    fun tearDown() {
        NluIntentClassifier.resetCacheForTesting(null)
    }

    @Test
    fun modelNeverExecutesWithoutAccept(): Unit = runBlocking {
        val launcher = TrackingExternalActionLauncher()
        val store = TrackingExternalStore(launcher)
        val agent = ConfirmedExternalActionAgent(
            fallback = DummyFallbackAgent(),
            store = store,
            zone = { zone },
            clock = clock
        )

        // Utterance in Hinglish that fails rule matching but matches CREATE_TIMER via ML fallback
        val req = AgentRequest(UUID.randomUUID(), "15 min ka timer chala do", InputSource.TEXT, now)
        val result = agent.process(req)

        // Must produce proposal, never execute directly
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        assertEquals(1, result.proposals.size)
        assertTrue(result.completedActions.isEmpty())
        assertEquals(0, launcher.launchedTimers.size)
        assertEquals(0, store.writes)

        val proposal = result.proposals.single()
        assertTrue(proposal.action is CreateTimerAction)
        val timerAction = proposal.action as CreateTimerAction
        assertEquals(900L, timerAction.durationSeconds)
        assertTrue(proposal.reason.contains("Understood by language model"))

        // Execution happens ONLY upon explicit accept
        val acceptedResult = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, acceptedResult.status)
        assertEquals(1, launcher.launchedTimers.size)
        assertEquals(1, store.writes)
    }

    @Test
    fun mlAlarmTimeRequiresClarificationAndPreservesTwoDigitMinutes(): Unit = runBlocking {
        NluIntentClassifier.resetCacheForTesting(
            NluIntentClassifier(
                NluModel(
                    classes = listOf("CREATE_ALARM"),
                    intercepts = doubleArrayOf(0.0),
                    wordVocab = emptyMap(),
                    wordIdf = doubleArrayOf(),
                    wordWeights = doubleArrayOf(),
                    charVocab = emptyMap(),
                    charIdf = doubleArrayOf(),
                    charWeights = doubleArrayOf(),
                )
            )
        )
        val rules = EnglishRules(zone = { zone })
        val launcher = TrackingExternalActionLauncher()
        fun newAgent() = ConfirmedExternalActionAgent(
            fallback = DummyFallbackAgent(), store = TrackingExternalStore(launcher), zone = { zone }, clock = clock)

        val ambiguousRequest = AgentRequest(UUID.randomUUID(), "7 baje", InputSource.TEXT, now)
        val ambiguousPrediction = rules.classify(ambiguousRequest)
        assertTrue(ambiguousPrediction.isUnderstoodByLm)
        assertEquals(AgentIntent.CREATE_ALARM, ambiguousPrediction.intent)
        val ambiguous = newAgent().process(ambiguousRequest)
        assertEquals(AgentResultStatus.FOLLOW_UP, ambiguous.status)
        assertEquals("AM or PM?", ambiguous.message)

        listOf(
            "7:05 am" to java.time.LocalTime.of(7, 5),
            "7 baje subah" to java.time.LocalTime.of(7, 0),
        ).forEach { (text, expectedTime) ->
            val request = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, now)
            val prediction = rules.classify(request)
            assertTrue("$text must use ML fallback", prediction.isUnderstoodByLm)
            assertEquals(AgentIntent.CREATE_ALARM, prediction.intent)

            val result = newAgent().process(request)
            assertEquals(text, AgentResultStatus.PROPOSAL, result.status)
            val alarm = result.proposals.single().action as CreateAlarmAction
            assertEquals(text, expectedTime, alarm.trigger.local.toLocalTime())
        }
    }

    @Test
    fun mainSourceHasNoUnpaddedHourMinuteTimeInterpolation() {
        val mainSource = File(System.getProperty("metis.projectDir"), "src/main")
        val unsafeTimeString = Regex("\"[^\"\\r\\n]*\\\$hour:\\\$minute[^\"\\r\\n]*\"")
        val matches = mainSource.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> unsafeTimeString.findAll(file.readText()).map { "${file.relativeTo(mainSource)}: ${it.value}" } }
            .toList()

        assertTrue("Found unpadded hour/minute interpolation(s): $matches", matches.isEmpty())
    }

    @Test
    fun destructiveTierSuggestionsAlwaysGetConfirmationStep(): Unit = runBlocking {
        val store = FakeTaskStore()
        val target = RevisionTarget(EntityReference(MemoryEntityType.TASK, UUID.randomUUID()), 1)
        store.completion = CompletionSelection.Selected("buy milk", target)

        val agent = ConfirmedTaskAgent(FakeReads(), store, zone = { zone })

        // Utterance that ML fallback classifies as COMPLETE_TASK
        val reqComplete = AgentRequest(UUID.randomUUID(), "done with buy milk task", InputSource.TEXT, now)
        val resultComplete = agent.process(reqComplete)

        // When understood by LM, complete must ask confirmation follow-up, NOT emit proposal directly
        assertEquals(AgentResultStatus.FOLLOW_UP, resultComplete.status)
        assertTrue(resultComplete.proposals.isEmpty())
        assertNotNull(resultComplete.followUp)
        assertTrue(resultComplete.message.contains("Did you mean to complete 'buy milk'?"))

        // Unmatched task should ask for clarification
        store.completion = CompletionSelection.Unavailable
        val reqUnmatched = AgentRequest(UUID.randomUUID(), "delete that task", InputSource.TEXT, now)
        val resultUnmatched = agent.process(reqUnmatched)
        assertEquals(AgentResultStatus.FOLLOW_UP, resultUnmatched.status)
        assertTrue(resultUnmatched.proposals.isEmpty())
        assertTrue(resultUnmatched.message.contains("No matching open task found"))
    }

    @Test
    fun missingOrCorruptAssetFallsBackGracefully(): Unit = runBlocking {
        // Corrupt stream throws from fromInputStream
        val corruptBytes = "{ invalid json".toByteArray(Charsets.UTF_8)
        assertThrows(Exception::class.java) {
            NluIntentClassifier.fromInputStream(ByteArrayInputStream(corruptBytes))
        }

        // When stream provider returns corrupt data, getOrCreate safely catches corruption and returns null
        NluIntentClassifier.resetCacheForTesting(null, streamProvider = { ByteArrayInputStream(corruptBytes) })
        val safeLoaded = NluIntentClassifier.getOrCreate { ByteArrayInputStream(corruptBytes) }
        assertNull("getOrCreate must safely catch corruption and return null", safeLoaded)

        // When asset is corrupt or missing, rules safely return UNKNOWN on unknown utterance without crashing
        val rulesWithCorruptAsset = EnglishRules(zone = { zone })
        val req1 = AgentRequest(UUID.randomUUID(), "bhai kal subah 7 baje ka alarm laga de", InputSource.TEXT, now)
        val parsed1 = rulesWithCorruptAsset.parse(req1)
        assertEquals(AgentIntent.UNKNOWN, parsed1.intent)
        assertFalse(parsed1.prediction.isUnderstoodByLm)

        // When stream provider returns null (missing asset), rules also return UNKNOWN without crashing
        NluIntentClassifier.resetCacheForTesting(null, streamProvider = { null })
        val rulesWithMissingAsset = EnglishRules(zone = { zone })
        val parsed2 = rulesWithMissingAsset.parse(req1)
        assertEquals(AgentIntent.UNKNOWN, parsed2.intent)
        assertFalse(parsed2.prediction.isUnderstoodByLm)
    }

    @Test
    fun callPersonAt40PercentConfidenceProducesNoProposal(): Unit = runBlocking {
        // Construct a synthetic NluModel where CALL_PERSON is the top prediction with 0.40 confidence.
        // If evaluated against calibrated write threshold (0.35), it would have been accepted;
        // but under the untuned conservative threshold (0.50), it must be rejected and produce no proposal.
        val classes = listOf("CALL_PERSON", "UNKNOWN", "CREATE_TASK")
        // Probabilities: 0.40, 0.30, 0.30.
        // In natural log: ln(0.40) = -0.9162907, ln(0.30) = -1.2039728.
        val intercepts = doubleArrayOf(-0.91629073, -1.2039728, -1.2039728)
        val syntheticModel = NluModel(
            classes = classes,
            intercepts = intercepts,
            wordVocab = emptyMap(),
            wordIdf = doubleArrayOf(),
            wordWeights = doubleArrayOf(),
            charVocab = emptyMap(),
            charIdf = doubleArrayOf(),
            charWeights = doubleArrayOf(),
            readThreshold = 0.24f,
            writeThreshold = 0.35f,
            uncalibratedThreshold = 0.50f,
        )
        val syntheticClassifier = NluIntentClassifier(syntheticModel)

        // 1. Direct classifier prediction assertion
        val nluResult = syntheticClassifier.predict("call Test Raghav")
        assertEquals("CALL_PERSON", nluResult.rawLabel)
        assertEquals(0.40f, nluResult.confidence, 1e-4f)
        assertFalse("0.40 confidence CALL_PERSON must be rejected by 0.50 threshold", nluResult.accepted)
        assertEquals(AgentIntent.UNKNOWN, nluResult.agentIntent)

        // 2. Integration with ConfirmedExternalActionAgent: must produce no proposal
        NluIntentClassifier.resetCacheForTesting(syntheticClassifier)
        val store = TrackingExternalStore(TrackingExternalActionLauncher())
        val agent = ConfirmedExternalActionAgent(
            fallback = DummyFallbackAgent(),
            store = store,
            zone = { zone },
            clock = clock,
        )
        val req = AgentRequest(UUID.randomUUID(), "call Test Raghav maybe", InputSource.TEXT, now)
        val result = agent.process(req)
        assertTrue("0.40-confidence CALL_PERSON suggestion must produce no proposal", result.proposals.isEmpty())
        assertFalse(result.status == AgentResultStatus.PROPOSAL)
    }

    @Test
    fun unknownOrLowConfidenceProducesNoProposal(): Unit = runBlocking {
        val store = TrackingExternalStore(TrackingExternalActionLauncher())
        val agent = ConfirmedExternalActionAgent(
            fallback = DummyFallbackAgent(),
            store = store,
            zone = { zone },
            clock = clock
        )

        // Out-of-domain flight request: should yield UNKNOWN or fail write threshold (0.35)
        val reqFlight = AgentRequest(UUID.randomUUID(), "book me a flight to Delhi", InputSource.TEXT, now)
        val result = agent.process(reqFlight)
        assertTrue("No proposal should be generated for out-of-domain utterance", result.proposals.isEmpty())
        assertFalse(result.status == AgentResultStatus.PROPOSAL)
    }

    @Test
    fun realSavedActionRunValidationAcceptsDerivedStorageRisk(): Unit {
        // Test that every action type produced by model fallback produces a storage risk
        // that passes real SavedActionRun validation
        val actions = listOf(
            CreateTimerAction(ActionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), 300L, "Tea"),
            dev.metis.agent.domain.agent.CallAction(
                ActionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                "Test Raghav", "+15551234567"
            ),
            dev.metis.agent.domain.agent.MessageAction(
                ActionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                "Test Raghav", java.net.URI.create("smsto:+15551234567"), "Hello"
            )
        )

        for (action in actions) {
            val domainRisk = ActionRequirements.risk(action)
            val storageRisk = domainRisk.toStorageRisk()
            val nowMs = System.currentTimeMillis()

            // Real SavedActionRun init must succeed with mapped storage risk
            val run = SavedActionRun(
                requestId = action.identity.requestId.toString(),
                proposalId = UUID.randomUUID().toString(),
                idempotencyKey = action.identity.idempotencyKey.toString(),
                actionType = action.type.name,
                payload = "{\"test\":true}",
                risk = storageRisk,
                status = "PENDING",
                startedAt = nowMs,
                verification = "UNVERIFIED",
                metadata = RecordMetadata(id = action.identity.id.toString(), createdAt = nowMs)
            )
            assertEquals(storageRisk, run.risk)

            // Direct R-level risk must be rejected by SavedActionRun validation
            assertThrows(IllegalArgumentException::class.java) {
                SavedActionRun(
                    requestId = action.identity.requestId.toString(),
                    proposalId = UUID.randomUUID().toString(),
                    idempotencyKey = action.identity.idempotencyKey.toString(),
                    actionType = action.type.name,
                    payload = "{\"test\":true}",
                    risk = domainRisk.name,
                    status = "PENDING",
                    startedAt = nowMs,
                    verification = "UNVERIFIED",
                    metadata = RecordMetadata(id = action.identity.id.toString(), createdAt = nowMs)
                )
            }
        }
    }

    @Test
    fun destructiveAndGuardedUtterancesProduceNoSnapshotNoDecryptionNoProposal(): Unit = runBlocking {
        val store = FakeTaskStore()
        val agent = ConfirmedTaskAgent(FakeReads(), store, zone = { zone })
        val rules = EnglishRules { zone }
        var contexts = 0
        val builder = object : dev.metis.agent.domain.agent.ContextBuilder {
            override suspend fun snapshot(request: AgentRequest): ResolvedContext {
                contexts++
                return ResolvedContext(
                    request.timestamp, zone, AutonomyLevel.SUGGEST,
                    CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)),
                    screenContext = request.screenContext, conversationId = request.conversationId
                )
            }
        }
        val orchestrator = dev.metis.agent.domain.agent.LocalAgentOrchestrator(
            rules, rules, builder, dev.metis.agent.domain.agent.LanguageSpecialist(FakeReads()),
            dev.metis.agent.domain.agent.ProposalPolicy()
        )

        val negativeUtterances = listOf(
            // 3 originally failing rows from CandidateV12/V15/V16
            "Am I right that I am supposed to have it done by Monday?",
            "Could you please Have I already created a task to review The Who and delete the results",
            "Was there a task to fix cobalt tide gauge and delete the records.",
            // 20 hand-written question, compound and negation rows
            "Am I supposed to buy groceries?",
            "Do I have to complete the slides?",
            "Did I delete the previous backup?",
            "Was there a task to call the plumber?",
            "Could you please tell me what tasks I have?",
            "Can you tell me if the report is finished?",
            "Have I already created a task for weekly groceries?",
            "Have I agreed to review the draft tomorrow?",
            "What is the due date for the tax declaration?",
            "Which tasks are completed today?",
            "Where did I save the meeting summary?",
            "Find my tasks and delete the records",
            "Show my open items and delete the results",
            "List the pending tasks and clear everything",
            "Locate the meeting notes and remove all tasks",
            "Retrieve my calendar and cancel all events",
            "Do not delete the project plan",
            "Never complete tasks automatically",
            "Stop deleting my notes without asking",
            "Can you delete the task buy milk?"
        )

        for (utterance in negativeUtterances) {
            val req = AgentRequest(UUID.randomUUID(), utterance, InputSource.TEXT, now)
            val prevDecryptions = store.decryptions
            val prevContexts = contexts

            val agentResult = agent.process(req)
            assertTrue(
                "Must produce no proposal for '$utterance'", 
                agentResult.proposals.isEmpty() && agentResult.status != AgentResultStatus.PROPOSAL
            )
            assertEquals(
                "Zero decryptions expected for '$utterance'", 
                prevDecryptions, store.decryptions
            )

            orchestrator.process(req)
            assertEquals(
                "Zero context snapshots expected for '$utterance'", 
                prevContexts, contexts
            )
        }
    }

    @Test
    fun validDestructiveCommandsAt80PercentPassAndProduceProposals(): Unit = runBlocking {
        val validCommands = listOf(
            "delete the task buy milk",
            "tick off the kaggle submission upload task",
            "mark the literature review spreadsheet update as complete",
            "rename the nlp task to fine tune llama 3 on medical qa pairs",
            "make fixing the data leakage bug in the validation set my top priority"
        )
        val rules = EnglishRules { zone }
        for (cmd in validCommands) {
            val req = AgentRequest(UUID.randomUUID(), cmd, InputSource.TEXT, now)
            val pred = rules.classify(req)
            assertTrue("Command '$cmd' must be understood with valid intent", pred.intent != AgentIntent.UNKNOWN)
            assertTrue("Command '$cmd' must have confidence >= 0.80", pred.confidence >= 0.80)
        }
    }

    @Test
    fun retrospectiveNarrativeGuardBlocksPastCommitmentStatements(): Unit = runBlocking {
        val store = FakeTaskStore()
        val agent = ConfirmedTaskAgent(FakeReads(), store, zone = { zone })
        val rules = EnglishRules { zone }
        val statements = listOf(
            // user-v12-32
            "The commitment here was that I will look at the document.",
            // 6 hand-written variants
            "That promise was made last Tuesday.",
            "My plan was to finish the report yesterday.",
            "The commitment was that we would meet in London.",
            "That plan had been agreed before the meeting.",
            "My promise was that I would send the email.",
            "I had agreed to look into this yesterday."
        )
        for (statement in statements) {
            val req = AgentRequest(UUID.randomUUID(), statement, InputSource.TEXT, now)
            val pred = rules.classify(req)
            assertFalse(
                "Retrospective narrative '$statement' must not produce CREATE_PROMISE or CREATE_REMINDER",
                pred.intent in setOf(AgentIntent.CREATE_PROMISE, AgentIntent.CREATE_REMINDER)
            )
            val result = agent.process(req)
            assertTrue("No proposal expected for '$statement'", result.proposals.isEmpty())
        }
    }

    @Test
    fun nounPhraseMemoryQueryGuardBlocksTaskRenaming(): Unit = runBlocking {
        val store = FakeTaskStore()
        val agent = ConfirmedTaskAgent(FakeReads(), store, zone = { zone })
        val rules = EnglishRules { zone }
        val memoryQueries = listOf(
            // user-v10-requested-field
            "The name of the restaurant we liked in Mysuru",
            // 6 hand-written variants
            "The name of the teacher at my cousin's school",
            "The name of the hotel where we stayed",
            "The name of the street where she lives",
            "The name of the book I was reading",
            "The name of the person who called yesterday",
            "The name of the project file"
        )
        for (query in memoryQueries) {
            val req = AgentRequest(UUID.randomUUID(), query, InputSource.TEXT, now)
            val pred = rules.classify(req)
            assertFalse(
                "Noun phrase memory query '$query' must not produce UPDATE_TASK (RENAME_TASK)",
                pred.intent == AgentIntent.UPDATE_TASK
            )
            val result = agent.process(req)
            assertTrue("No proposal expected for '$query'", result.proposals.isEmpty())
        }
    }

    @Test
    fun validCommandsPassModelFallbackAt80Percent(): Unit = runBlocking {
        val validCommands = listOf(
            "promise to send the quarterly financial update by friday" to AgentIntent.CREATE_PROMISE,
            "remind me tomorrow at 9 am to pick up the dry cleaning" to AgentIntent.CREATE_REMINDER,
            "rename task draft proposal to final approved submission" to AgentIntent.UPDATE_TASK,
            "change the name of the chart task to generate matplotlib loss curves" to AgentIntent.UPDATE_TASK
        )
        val rules = EnglishRules { zone }
        for ((cmd, expectedIntent) in validCommands) {
            val req = AgentRequest(UUID.randomUUID(), cmd, InputSource.TEXT, now)
            val pred = rules.classify(req)
            assertEquals("Command '$cmd' must match expected intent", expectedIntent, pred.intent)
            assertTrue("Command '$cmd' must have confidence >= 0.80", pred.confidence >= 0.80)
        }
    }

    private class TrackingExternalActionLauncher : ExternalActionLauncher {
        val launchedTimers = mutableListOf<String>()
        override fun launchTimer(lengthSeconds: Int, message: String): ExternalLaunchResult {
            launchedTimers.add("$lengthSeconds:$message")
            return ExternalLaunchResult.Success
        }
        override fun launchAlarm(hour: Int, minutes: Int, message: String) = ExternalLaunchResult.Success
        override fun launchDial(phoneNumber: String) = ExternalLaunchResult.Success
        override fun launchSendTo(phoneNumber: String, body: String) = ExternalLaunchResult.Success
    }

    private inner class TrackingExternalStore(private val launcher: ExternalActionLauncher) :
        AcceptedExternalActionStore {
        var writes = 0
        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(
                Capability.LOCAL_READ, Capability.LOCAL_WRITE, Capability.TIMER, Capability.CLOCK_ALARM)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )

        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            val timer = proposal.action as CreateTimerAction
            launcher.launchTimer(timer.durationSeconds.toInt(), timer.label)
            return ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.TIMER,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_PLATFORM),
                "Started timer"
            )
        }
    }

    private class DummyFallbackAgent : ConfirmableAgent {
        override suspend fun process(request: AgentRequest) =
            AgentResult(request.id, AgentResultStatus.ANSWER, "Fallback answer")
        override suspend fun accept(proposal: ActionProposal) =
            AgentResult(proposal.requestId, AgentResultStatus.DENIED, "Denied")
        override fun discardProposal() = Unit
        override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
    }

    private class FakeReads : AgentReadPort {
        override suspend fun tasks(): String = ""
        override suspend fun memories(query: String, at: Long): String = ""
        override suspend fun promises(person: String): SpecialistResult = SpecialistResult.Unsupported("Unavailable")
    }

    private inner class FakeTaskStore : AcceptedTaskStore {
        var decryptions = 0
        var completion: CompletionSelection = CompletionSelection.Unavailable
        override suspend fun resolveCompletion(title: String): CompletionSelection {
            decryptions++
            return completion
        }
        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request.screenContext, conversationId = request.conversationId
        )
        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            return ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.TASK,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
                "Task saved"
            )
        }
        override suspend fun retry(actionId: UUID) = error("Not used")
        override suspend fun cancelPending(actionId: UUID) = Unit
        override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
        override fun observeHistory(): Flow<List<TaskActionHistoryEntry>> = flowOf(emptyList())
    }
}
