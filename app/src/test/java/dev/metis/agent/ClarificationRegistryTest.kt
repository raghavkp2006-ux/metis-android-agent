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
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ClarificationDomain
import dev.metis.agent.domain.agent.ClarificationRegistry
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun crossDomainClarificationClearing4x4Matrix(): Unit = runBlocking {
        val triggers = listOf(
            ClarificationDomain.REMINDER to "remind me tomorrow to study",
            ClarificationDomain.ALARM to "wake me at 6",
            ClarificationDomain.CALL to "call",
            ClarificationDomain.PROMISE to "I promised Contact A",
        )

        val commands = listOf(
            ClarificationDomain.REMINDER to ("remind me tomorrow at 8 pm to study" to ActionType.CREATE_REMINDER),
            ClarificationDomain.ALARM to ("set an alarm for 7am" to ActionType.ALARM),
            ClarificationDomain.CALL to ("call Contact A" to ActionType.CALL),
            ClarificationDomain.PROMISE to ("I promised Contact A the report by Friday" to ActionType.CREATE_PROMISE),
        )

        val scenarios = buildList {
            for ((fromDomain, initPhrase) in triggers) {
                for ((toDomain, cmdPair) in commands) {
                    add(MatrixScenario(fromDomain, initPhrase, toDomain, cmdPair.first, cmdPair.second))
                }
            }
        }

        assertEquals(16, scenarios.size)

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

    private fun createHarness(clock: MutableTestClock): Harness {
        val registry = ClarificationRegistry(clock)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String): ContactResolution {
                val candidate = ContactCandidate("c1", "Contact A", lookupKey = "lookupA", phoneNumber = "+15550100")
                return if (query.contains("Contact A", ignoreCase = true)) ContactResolution.Resolved(candidate)
                else ContactResolution.NotFound
            }
            override suspend fun resolvePerson(query: String): ContactResolution = resolve(query)
        }

        val allCapabilities = Capability.entries.toSet()

        val taskStore = object : AcceptedTaskStore {
            override suspend fun resolveCompletion(title: String): CompletionSelection = CompletionSelection.Unavailable
            override suspend fun resolveCompletion(title: String, fuzzy: Boolean): CompletionSelection = CompletionSelection.Unavailable
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

        val taskAgent = ConfirmedTaskAgent(dummyReads, taskStore, zone = { zone })
        val reminderAgent = ConfirmedReminderAgent(taskAgent, reminderStore, zone = { zone }, clock = clock, contacts = contacts, registry = registry)
        val promiseAgent = ConfirmedPromiseAgent(reminderAgent, promiseStore, zone = { zone }, clock = clock, contacts = contacts, registry = registry)
        val externalAgent = ConfirmedExternalActionAgent(promiseAgent, externalStore, contacts = contacts, zone = { zone }, clock = clock, registry = registry)

        return Harness(externalAgent, registry, clock)
    }

    private class Harness(
        val agent: ConfirmedExternalActionAgent,
        val registry: ClarificationRegistry,
        val clock: MutableTestClock,
    ) {
        fun request(text: String, sessionId: UUID = UUID.randomUUID(), timestamp: Instant = clock.current) = AgentRequest(
            id = UUID.randomUUID(),
            text = text,
            source = InputSource.TEXT,
            timestamp = timestamp,
            screenContext = null,
            conversationId = sessionId
        )
    }
}
