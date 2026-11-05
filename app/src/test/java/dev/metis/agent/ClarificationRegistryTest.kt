@file:Suppress("MagicNumber", "MaxLineLength", "TooManyFunctions")

package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedExternalActionStore
import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.AcceptedReminderStore
import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ClarificationDomain
import dev.metis.agent.domain.agent.ClarificationRegistry
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ReminderHistoryEntry
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedProject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarificationRegistryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val baseNow = Instant.parse("2026-10-14T10:00:00Z")

    private class MutableTestClock(var current: Instant, val zoneId: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId?): Clock = MutableTestClock(current, zone ?: zoneId)
        override fun instant(): Instant = current
    }

    private data class MatrixScenario(
        val fromDomain: ClarificationDomain,
        val initiatingPhrase: String,
        val toDomain: ClarificationDomain,
        val commandPhrase: String,
        val expectedActionType: ActionType,
    )

    @Test
    fun crossDomainClarificationClearing5x5Matrix(): Unit = runBlocking {
        val triggers = listOf(
            ClarificationDomain.REMINDER to "remind me tomorrow to study",
            ClarificationDomain.ALARM to "wake me at 6",
            ClarificationDomain.CALL to "call",
            ClarificationDomain.PROMISE to "I promised Contact A",
            ClarificationDomain.TASK to "add a task to review specs for the Apollo project",
        )

        val commands = listOf(
            ClarificationDomain.REMINDER to ("remind me tomorrow at 8 pm to study" to ActionType.CREATE_REMINDER),
            ClarificationDomain.ALARM to ("set an alarm for 7am" to ActionType.ALARM),
            ClarificationDomain.CALL to ("call Contact A" to ActionType.CALL),
            ClarificationDomain.PROMISE to ("I promised Contact A the report by Friday" to ActionType.CREATE_PROMISE),
            ClarificationDomain.TASK to ("add task test motors for Apollo 11" to ActionType.TASK),
        )

        val scenarios = buildList {
            for ((fromDomain, initPhrase) in triggers) {
                for ((toDomain, cmdPair) in commands) {
                    add(MatrixScenario(fromDomain, initPhrase, toDomain, cmdPair.first, cmdPair.second))
                }
            }
        }

        assertEquals(25, scenarios.size)

        for (scenario in scenarios) {
            val clock = MutableTestClock(baseNow, zone)
            val harness = createHarness(clock)
            val sessionId = UUID.randomUUID()

            val initResult = harness.agent.process(harness.request(scenario.initiatingPhrase, sessionId = sessionId))
            assertEquals(
                "Initial phrase '${scenario.initiatingPhrase}' for ${scenario.fromDomain} must produce FOLLOW_UP",
                AgentResultStatus.FOLLOW_UP,
                initResult.status
            )
            assertEquals(
                "Registry must hold active clarification for ${scenario.fromDomain}",
                scenario.fromDomain,
                harness.registry.current()?.domain
            )

            // Now send the command for the target domain
            val cmdResult = harness.agent.process(harness.request(scenario.commandPhrase, sessionId = sessionId))

            if (scenario.fromDomain != scenario.toDomain) {
                val activeAfter = harness.registry.current()
                assertTrue(
                    "Cross-domain command '${scenario.commandPhrase}' must clear prior ${scenario.fromDomain} clarification, was: ${activeAfter?.domain}",
                    activeAfter == null || activeAfter.domain != scenario.fromDomain
                )
            }

            assertEquals(
                "Command phrase '${scenario.commandPhrase}' must succeed with PROPOSAL, was ${cmdResult.status} with message: '${cmdResult.message}'",
                AgentResultStatus.PROPOSAL,
                cmdResult.status
            )
            val action = cmdResult.proposals.single().action
            assertEquals(
                "Proposal must match expected action type ${scenario.expectedActionType}",
                scenario.expectedActionType,
                action.type
            )
        }
    }

    @Test
    fun replyIsHandledExactlyOnce(): Unit = runBlocking {
        val clock = MutableTestClock(baseNow, zone)
        val harness = createHarness(clock)
        val sessionId = UUID.randomUUID()

        val question = harness.agent.process(harness.request("remind me tomorrow to study", sessionId = sessionId))
        assertEquals(AgentResultStatus.FOLLOW_UP, question.status)
        assertEquals(ClarificationDomain.REMINDER, harness.registry.current()?.domain)

        val firstReply = harness.agent.process(harness.request("at 8pm", sessionId = sessionId))
        assertEquals(AgentResultStatus.PROPOSAL, firstReply.status)
        assertEquals(ActionType.CREATE_REMINDER, firstReply.proposals.single().action.type)
        assertNull("Registry must be consumed after valid reply", harness.registry.current())

        // Sending the reply again immediately must not be handled by the consumed clarification
        val secondReply = harness.agent.process(harness.request("at 8pm", sessionId = sessionId))
        assertTrue(
            "Second reply must not produce duplicate proposal",
            secondReply.status != AgentResultStatus.PROPOSAL
        )
    }

    @Test
    fun clarificationExpiresAfter120Seconds(): Unit = runBlocking {
        val clock = MutableTestClock(baseNow, zone)
        val harness = createHarness(clock)
        val sessionId = UUID.randomUUID()

        val question = harness.agent.process(harness.request("wake me at 6", sessionId = sessionId))
        assertEquals(AgentResultStatus.FOLLOW_UP, question.status)
        assertEquals(ClarificationDomain.ALARM, harness.registry.current()?.domain)

        // Advance clock past the 120s expiry window
        clock.current = clock.current.plusSeconds(121)

        assertNull("Clarification must expire past 120 seconds", harness.registry.current())

        val reply = harness.agent.process(harness.request("am", sessionId = sessionId, timestamp = clock.current))
        assertTrue("Expired clarification must not accept answers", reply.status != AgentResultStatus.PROPOSAL)
    }

    @Test
    fun twoTurnCapEnforced(): Unit = runBlocking {
        val clock = MutableTestClock(baseNow, zone)
        val harness = createHarness(clock)
        val sessionId = UUID.randomUUID()

        val init = harness.agent.process(harness.request("wake me at 6", sessionId = sessionId))
        assertEquals(AgentResultStatus.FOLLOW_UP, init.status)
        assertEquals(2, harness.registry.current()?.turnsRemaining)

        // Turn 1: invalid reply
        val turn1 = harness.agent.process(harness.request("banana", sessionId = sessionId))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals(1, harness.registry.current()?.turnsRemaining)

        // Turn 2: second invalid reply
        val turn2 = harness.agent.process(harness.request("orange", sessionId = sessionId))
        assertEquals(AgentResultStatus.DENIED, turn2.status)
        assertNull("Registry must be cleared after 2 invalid turns", harness.registry.current())
    }

    @Test
    fun wakeMeAt6_thenRemindTomorrowToCallContactA_then8pm_resolvesReminderNotAlarm(): Unit = runBlocking {
        val clock = MutableTestClock(baseNow, zone)
        val harness = createHarness(clock)
        val sessionId = UUID.randomUUID()

        val turn1 = harness.agent.process(harness.request("wake me at 6", sessionId = sessionId))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("AM or PM?", turn1.message)
        assertEquals(ClarificationDomain.ALARM, harness.registry.current()?.domain)

        val turn2 = harness.agent.process(harness.request("remind me tomorrow to call Contact A", sessionId = sessionId))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn2.status)
        assertEquals("What time?", turn2.message)
        assertEquals(ClarificationDomain.REMINDER, harness.registry.current()?.domain)

        val turn3 = harness.agent.process(harness.request("8pm", sessionId = sessionId))
        assertEquals(AgentResultStatus.PROPOSAL, turn3.status)
        val proposal = turn3.proposals.single()
        assertEquals(ActionType.CREATE_REMINDER, proposal.action.type)
        val reminderAction = proposal.action as CreateReminderAction
        assertEquals("call Contact A", reminderAction.title)
        assertNull("Registry must be consumed", harness.registry.current())
    }

    @Test
    fun repliesThatMustAnswer_notEvict(): Unit = runBlocking {
        val clock = MutableTestClock(baseNow, zone)

        // 1. "am" answers ALARM
        var h = createHarness(clock)
        h.agent.process(h.request("wake me at 6"))
        var res = h.agent.process(h.request("am"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.ALARM, res.proposals.single().action.type)

        // 2. "pm" answers ALARM
        h = createHarness(clock)
        h.agent.process(h.request("wake me at 6"))
        res = h.agent.process(h.request("pm"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.ALARM, res.proposals.single().action.type)

        // 3. "subah" answers ALARM
        h = createHarness(clock)
        h.agent.process(h.request("wake me at 6"))
        res = h.agent.process(h.request("subah"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.ALARM, res.proposals.single().action.type)

        // 4. "8" answers REMINDER
        h = createHarness(clock)
        h.agent.process(h.request("remind me tomorrow to call Contact A"))
        res = h.agent.process(h.request("8"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.CREATE_REMINDER, res.proposals.single().action.type)

        // 5. "8pm" answers REMINDER
        h = createHarness(clock)
        h.agent.process(h.request("remind me tomorrow to call Contact A"))
        res = h.agent.process(h.request("8pm"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.CREATE_REMINDER, res.proposals.single().action.type)

        // 6. "1" answers ambiguous call candidate
        h = createHarness(clock)
        val callInit = h.agent.process(h.request("call AmbiguousPerson"))
        assertEquals(AgentResultStatus.FOLLOW_UP, callInit.status)
        res = h.agent.process(h.request("1"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.CALL, res.proposals.single().action.type)
        assertEquals("Contact A", (res.proposals.single().action as CallAction).targetLabel)

        // 7. "second" answers ambiguous call candidate
        h = createHarness(clock)
        h.agent.process(h.request("call AmbiguousPerson"))
        res = h.agent.process(h.request("second"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.CALL, res.proposals.single().action.type)
        assertEquals("Contact B", (res.proposals.single().action as CallAction).targetLabel)

        // 8. "Contact A" answers ambiguous call candidate
        h = createHarness(clock)
        h.agent.process(h.request("call AmbiguousPerson"))
        res = h.agent.process(h.request("Contact A"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.CALL, res.proposals.single().action.type)
        assertEquals("Contact A", (res.proposals.single().action as CallAction).targetLabel)

        // 9. "skip" answers reminder contact permission
        h = createHarness(clock)
        val permInit = h.agent.process(h.request("remind me tomorrow at 8pm to call NeedsPerm"))
        assertEquals(AgentResultStatus.FOLLOW_UP, permInit.status)
        res = h.agent.process(h.request("skip"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.CREATE_REMINDER, res.proposals.single().action.type)
    }

    @Test
    fun repliesThatMustEvict(): Unit = runBlocking {
        val clock = MutableTestClock(baseNow, zone)

        // 1. "set timer 10m" evicts ALARM clarification
        var h = createHarness(clock)
        h.agent.process(h.request("wake me at 6"))
        assertEquals(ClarificationDomain.ALARM, h.registry.current()?.domain)
        var res = h.agent.process(h.request("set timer 10m"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.TIMER, res.proposals.single().action.type)
        val timerAction1 = res.proposals.single().action as CreateTimerAction
        assertEquals(600L, timerAction1.durationSeconds)
        assertNull("Registry must be null after eviction and proposal", h.registry.current())

        // 2. "never mind" evicts/cancels ALARM clarification
        h = createHarness(clock)
        h.agent.process(h.request("wake me at 6"))
        assertEquals(ClarificationDomain.ALARM, h.registry.current()?.domain)
        res = h.agent.process(h.request("never mind"))
        assertEquals(AgentResultStatus.DENIED, res.status)
        assertNull("Registry must be null after cancellation", h.registry.current())

        // 3. "set timer 10m" evicts REMINDER clarification
        h = createHarness(clock)
        h.agent.process(h.request("remind me tomorrow to call Contact A"))
        assertEquals(ClarificationDomain.REMINDER, h.registry.current()?.domain)
        res = h.agent.process(h.request("set timer 10m"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        assertEquals(ActionType.TIMER, res.proposals.single().action.type)
        val timerAction2 = res.proposals.single().action as CreateTimerAction
        assertEquals(600L, timerAction2.durationSeconds)
        assertNull("Registry must be null after eviction and proposal", h.registry.current())

        // 4. "never mind" evicts/cancels REMINDER clarification
        h = createHarness(clock)
        h.agent.process(h.request("remind me tomorrow to call Contact A"))
        assertEquals(ClarificationDomain.REMINDER, h.registry.current()?.domain)
        res = h.agent.process(h.request("never mind"))
        assertEquals(AgentResultStatus.DENIED, res.status)
        assertNull("Registry must be null after cancellation", h.registry.current())
    }

    @Test
    fun promiseWithDueDate(): Unit = runBlocking {
        val clock = MutableTestClock(Instant.parse("2026-11-04T10:00:00Z"), zone)
        val h = createHarness(clock)

        val res = h.agent.process(h.request("I promised ContactOne the report by Friday"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        val action = res.proposals.single().action as CreatePromiseAction
        assertEquals("ContactOne", action.personName)
        assertEquals("the report", action.description)
        assertNotNull(action.dueDate)
    }

    @Test
    fun promiseWithoutDueDate_asksByWhen_replyDateResolves(): Unit = runBlocking {
        val clock = MutableTestClock(Instant.parse("2026-11-04T10:00:00Z"), zone)
        val h = createHarness(clock)

        val turn1 = h.agent.process(h.request("I promised ContactOne the report"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("By when?", turn1.message)
        assertEquals(setOf("due_date"), turn1.followUp?.missingFields)

        val turn2 = h.agent.process(h.request("tomorrow"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val action = turn2.proposals.single().action as CreatePromiseAction
        assertEquals("ContactOne", action.personName)
        assertEquals("the report", action.description)
        assertEquals(LocalDate.parse("2026-11-05"), action.dueDate)
    }

    @Test
    fun promiseWithoutDueDate_asksByWhen_replyNoDeadlineResolves(): Unit = runBlocking {
        val clock = MutableTestClock(Instant.parse("2026-11-04T10:00:00Z"), zone)
        val h = createHarness(clock)

        val turn1 = h.agent.process(h.request("I promised ContactOne the report"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("By when?", turn1.message)

        val turn2 = h.agent.process(h.request("no deadline"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val action = turn2.proposals.single().action as CreatePromiseAction
        assertEquals("ContactOne", action.personName)
        assertEquals("the report", action.description)
        assertNull(action.dueDate)
    }

    @Test
    fun promiseUnknownPerson_proposesFreeTextPerson(): Unit = runBlocking {
        val clock = MutableTestClock(Instant.parse("2026-11-04T10:00:00Z"), zone)
        val h = createHarness(clock)

        val res = h.agent.process(h.request("I promised UnknownPerson the report by tomorrow"))
        assertEquals(AgentResultStatus.PROPOSAL, res.status)
        val action = res.proposals.single().action as CreatePromiseAction
        assertEquals("UnknownPerson", action.personName)
        assertEquals("the report", action.description)
        assertNull(action.matchedPersonId)
        assertNull(action.contactLookupKey)
    }

    @Test
    fun callHim_withNoPriorPerson_stillAsks(): Unit = runBlocking {
        val clock = MutableTestClock(Instant.parse("2026-11-04T10:00:00Z"), zone)
        val h = createHarness(clock)

        val res = h.agent.process(h.request("call him"))
        assertEquals(AgentResultStatus.FOLLOW_UP, res.status)
        assertEquals("Who do you mean?", res.message)
    }

    @Test
    fun callHim_afterResolvedPerson_resolvesWithoutAsking(): Unit = runBlocking {
        val clock = MutableTestClock(Instant.parse("2026-11-04T10:00:00Z"), zone)
        val h = createHarness(clock)

        val turn1 = h.agent.process(h.request("call Contact A"))
        assertEquals(AgentResultStatus.PROPOSAL, turn1.status)
        val action1 = turn1.proposals.single().action as CallAction
        assertEquals("Contact A", action1.targetLabel)

        val turn2 = h.agent.process(h.request("call him"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val action2 = turn2.proposals.single().action as CallAction
        assertEquals("Contact A", action2.targetLabel)
    }

    @Test
    fun freshExplicitName_overridesWorkingMemorySlot(): Unit = runBlocking {
        val clock = MutableTestClock(Instant.parse("2026-11-04T10:00:00Z"), zone)
        val h = createHarness(clock)

        h.agent.process(h.request("call Contact A"))
        h.agent.process(h.request("call Contact B"))

        val turn3 = h.agent.process(h.request("call him"))
        assertEquals(AgentResultStatus.PROPOSAL, turn3.status)
        val action = turn3.proposals.single().action as CallAction
        assertEquals("Contact B", action.targetLabel)
    }

    private fun createHarness(clock: MutableTestClock): Harness {
        val registry = ClarificationRegistry(clock)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String): ContactResolution {
                val candidateA = ContactCandidate("c1", "Contact A", lookupKey = "lookupA", phoneNumber = "+15550100")
                val candidateB = ContactCandidate("c2", "Contact B", lookupKey = "lookupB", phoneNumber = "+15550101")
                val candidateOne = ContactCandidate("c3", "ContactOne", lookupKey = "lookupOne", phoneNumber = "+15550102")
                return when {
                    query.equals("AmbiguousPerson", ignoreCase = true) ->
                        ContactResolution.Ambiguous(listOf(candidateA, candidateB))
                    query.contains("Contact A", ignoreCase = true) -> ContactResolution.Resolved(candidateA)
                    query.contains("Contact B", ignoreCase = true) -> ContactResolution.Resolved(candidateB)
                    query.contains("ContactOne", ignoreCase = true) -> ContactResolution.Resolved(candidateOne)
                    query.contains("NeedsPerm", ignoreCase = true) -> ContactResolution.PermissionNeeded
                    else -> ContactResolution.NotFound
                }
            }
            override suspend fun resolvePerson(query: String): ContactResolution = resolve(query)
        }

        val allCapabilities = Capability.entries.toSet()

        val taskStore = object : AcceptedTaskStore {
            override suspend fun resolveCompletion(title: String): CompletionSelection = CompletionSelection.Unavailable
            override suspend fun resolveCompletion(title: String, fuzzy: Boolean): CompletionSelection = CompletionSelection.Unavailable
            override suspend fun savedProjects(): List<SavedProject> = listOf(
                SavedProject("Apollo 11", metadata = RecordMetadata("00000000-0000-0000-0000-000000000001")),
                SavedProject("Apollo 13", metadata = RecordMetadata("00000000-0000-0000-0000-000000000002")),
            )
            override suspend fun context(request: AgentRequest) = ResolvedContext(
                clock.instant(), zone, AutonomyLevel.SUGGEST,
                CapabilitySnapshot(clock.instant(), allCapabilities),
                screenContext = request.screenContext,
                conversationId = request.conversationId
            )
            override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt = ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.TASK,
                ReceiptOutcome(ActionStatus.SUCCEEDED, clock.instant(), clock.instant(), OutcomeVerification.VERIFIED_LOCAL),
                "Task operation executed"
            )
            override suspend fun retry(actionId: UUID) = error("Not used")
            override suspend fun cancelPending(actionId: UUID) = Unit
            override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
            override fun observeHistory(): Flow<List<TaskActionHistoryEntry>> = flowOf(emptyList())
        }

        val reminderStore = object : AcceptedReminderStore {
            override suspend fun context(request: AgentRequest) = ResolvedContext(
                clock.instant(), zone, AutonomyLevel.SUGGEST,
                CapabilitySnapshot(clock.instant(), allCapabilities),
                screenContext = request.screenContext,
                conversationId = request.conversationId
            )
            override suspend fun matchingPeople(name: String) = emptyList<PromisePersonMatch>()
            override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt = ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.CREATE_REMINDER,
                ReceiptOutcome(ActionStatus.SUCCEEDED, clock.instant(), clock.instant(), OutcomeVerification.VERIFIED_LOCAL),
                "Reminder saved"
            )
            override suspend fun retry(actionId: UUID) = error("Not used")
            override suspend fun cancel(actionId: UUID) = UndoResult.NOT_SUPPORTED
            override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
            override suspend fun reconcile() = Unit
            override fun history(): Flow<List<ReminderHistoryEntry>> = flowOf(emptyList())
        }

        val promiseStore = object : AcceptedPromiseStore {
            override suspend fun context(request: AgentRequest) = ResolvedContext(
                clock.instant(), zone, AutonomyLevel.SUGGEST,
                CapabilitySnapshot(clock.instant(), allCapabilities),
                screenContext = request.screenContext,
                conversationId = request.conversationId
            )
            override suspend fun matchingPeople(name: String) = emptyList<PromisePersonMatch>()
            override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt = ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.CREATE_PROMISE,
                ReceiptOutcome(ActionStatus.SUCCEEDED, clock.instant(), clock.instant(), OutcomeVerification.VERIFIED_LOCAL),
                "Promise saved"
            )
        }

        val externalStore = object : AcceptedExternalActionStore {
            override suspend fun context(request: AgentRequest) = ResolvedContext(
                clock.instant(), zone, AutonomyLevel.SUGGEST,
                CapabilitySnapshot(clock.instant(), allCapabilities),
                screenContext = request.screenContext,
                conversationId = request.conversationId
            )
            override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt = ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, proposal.action.type,
                ReceiptOutcome(ActionStatus.SUCCEEDED, clock.instant(), clock.instant(), OutcomeVerification.VERIFIED_PLATFORM),
                "External action executed"
            )
        }

        val dummyReads = object : AgentReadPort {
            override suspend fun tasks(): String = ""
            override suspend fun memories(query: String, at: Long): String = ""
            override suspend fun promises(person: String): SpecialistResult = SpecialistResult.Unsupported("Unavailable")
        }

        val taskAgent = ConfirmedTaskAgent(dummyReads, taskStore, zone = { zone }, registry = registry, clock = clock)
        val reminderAgent = ConfirmedReminderAgent(taskAgent, reminderStore, zone = { zone }, clock = clock, contacts = contacts, registry = registry)
        val promiseAgent = ConfirmedPromiseAgent(reminderAgent, promiseStore, zone = { zone }, clock = clock, contacts = contacts, registry = registry)
        val externalAgent = ConfirmedExternalActionAgent(promiseAgent, externalStore, contacts = contacts, zone = { zone }, clock = clock, registry = registry)

        return Harness(externalAgent, registry, clock)
    }

    private class Harness(
        val agent: ConfirmedExternalActionAgent,
        val registry: ClarificationRegistry,
        val clock: MutableTestClock,
        val defaultSessionId: UUID = UUID.randomUUID(),
    ) {
        fun request(text: String, sessionId: UUID = defaultSessionId, timestamp: Instant = clock.current) = AgentRequest(
            id = UUID.randomUUID(),
            text = text,
            source = InputSource.TEXT,
            timestamp = timestamp,
            screenContext = null,
            conversationId = sessionId
        )
    }
}
