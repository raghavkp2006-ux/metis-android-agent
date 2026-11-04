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
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ClarificationDomain
import dev.metis.agent.domain.agent.ClarificationRegistry
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.EnglishConversationState
import dev.metis.agent.domain.agent.ExternalActionLauncher
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.MessageAction
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.PendingClarificationState
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ReminderHistoryEntry
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SessionWorkingMemory
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWorkingMemoryTest {
    private val now = Instant.parse("2026-11-04T12:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(now, zone)
    private val session = UUID.randomUUID()

    // 1. Clarification clear/evict/cancel/expiry leaves working memory intact
    @Test
    fun clarificationClear_leavesWorkingMemoryIntact() {
        val memory = SessionWorkingMemory()
        val registry = ClarificationRegistry(clock)
        memory.updateLastResolvedPerson("Rahul")

        registry.requestSlot(PendingClarificationState(
            domain = ClarificationDomain.CALL,
            slot = "person",
            filledSlots = emptyMap(),
            createdAt = now,
            expiresAt = now.plusSeconds(120),
            turnsRemaining = 2,
            originalRequestId = UUID.randomUUID(),
            questionId = UUID.randomUUID(),
            sessionId = session,
            prompt = "Who do you mean?"
        ))
        assertNotNull(registry.current())

        registry.clear()
        assertNull(registry.current())
        assertEquals("Rahul", memory.lastResolvedPerson())
    }

    @Test
    fun clarificationEvict_onUnrelatedIntent_leavesWorkingMemoryIntact(): Unit = runBlocking {
        val harness = createHarness()
        harness.memory.updateLastResolvedPerson("Rahul")

        // Turn 1 starts clarification
        val turn1 = harness.agent.process(harness.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertNotNull(harness.agent.activeClarification)

        // Unrelated command evicts active clarification
        val turn2 = harness.agent.process(harness.request("set timer 10 min"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        assertNull(harness.agent.activeClarification)

        // Working memory is preserved
        assertEquals("Rahul", harness.memory.lastResolvedPerson())
    }

    @Test
    fun clarificationCancel_leavesWorkingMemoryIntact(): Unit = runBlocking {
        val harness = createHarness()
        harness.memory.updateLastResolvedPerson("Rahul")

        // Start clarification
        val turn1 = harness.agent.process(harness.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)

        // Cancel it
        val turn2 = harness.agent.process(harness.request("never mind"))
        assertEquals(AgentResultStatus.DENIED, turn2.status)
        assertNull(harness.agent.activeClarification)

        assertEquals("Rahul", harness.memory.lastResolvedPerson())
    }

    @Test
    fun clarificationExpiry_leavesWorkingMemoryIntact(): Unit = runBlocking {
        val harness = createHarness()
        harness.memory.updateLastResolvedPerson("Rahul")

        // Start clarification
        val turn1 = harness.agent.process(harness.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)

        // Two invalid turns exhaust turnsRemaining and expire
        harness.agent.process(harness.request("8 pn"))
        val turn3 = harness.agent.process(harness.request("invalid again"))
        assertEquals(AgentResultStatus.DENIED, turn3.status)
        assertNull(harness.agent.activeClarification)

        assertEquals("Rahul", harness.memory.lastResolvedPerson())
    }

    // 2. Session reset clears working memory
    @Test
    fun sessionReset_clearsWorkingMemory() {
        val harness = createHarness()
        harness.memory.updateLastResolvedPerson("Rahul")
        assertEquals("Rahul", harness.memory.lastResolvedPerson())

        val conversation = EnglishConversationState(harness.agent, clock)
        conversation.reset()

        assertNull(harness.memory.lastResolvedPerson())
    }

    // 3. "call him" with a prior person resolves
    @Test
    fun callHim_withPriorPerson_resolves(): Unit = runBlocking {
        val harness = createHarness()
        harness.memory.updateLastResolvedPerson("Rahul K")

        val result = harness.agent.process(harness.request("call him"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        val action = proposal.action as CallAction
        assertEquals("Rahul K", action.targetLabel)
        assertEquals("+15550101", action.phoneNumber)
    }

    // 4. "call him" with none asks
    @Test
    fun callHim_withNoPriorPerson_promptsClarification(): Unit = runBlocking {
        val harness = createHarness()
        assertNull(harness.memory.lastResolvedPerson())

        val result = harness.agent.process(harness.request("call him"))
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertEquals("Who do you mean?", result.message)
        assertTrue(result.proposals.isEmpty())
    }

    // 5. Explicit name overrides working memory
    @Test
    fun explicitName_overridesWorkingMemory(): Unit = runBlocking {
        val harness = createHarness()
        harness.memory.updateLastResolvedPerson("Rahul K")

        // Explicit call to Priya overrides working memory
        val result1 = harness.agent.process(harness.request("call Priya"))
        assertEquals(AgentResultStatus.PROPOSAL, result1.status)
        assertEquals("Priya", harness.memory.lastResolvedPerson())

        // Subsequent "call her" resolves to Priya
        val result2 = harness.agent.process(harness.request("call her"))
        assertEquals(AgentResultStatus.PROPOSAL, result2.status)
        val action = result2.proposals.single().action as CallAction
        assertEquals("Priya", action.targetLabel)
    }

    // 6. Compound fork sees working memory ("call him and text him")
    @Test
    fun compoundFork_seesWorkingMemory_callHimAndTextHim(): Unit = runBlocking {
        val harness = createHarness()
        harness.memory.updateLastResolvedPerson("Rahul K")

        val conversation = EnglishConversationState(harness.agent, clock)
        val results = conversation.process(harness.request("call him and text him I'll be late"))
        assertEquals(2, results.size)
        assertTrue(results.all { it.status == AgentResultStatus.PROPOSAL })

        val callAction = results[0].proposals.single().action as CallAction
        assertEquals("Rahul K", callAction.targetLabel)

        val messageAction = results[1].proposals.single().action as MessageAction
        assertEquals("Rahul K", messageAction.recipient)
    }

    private fun createHarness(): TestHarness {
        val memory = SessionWorkingMemory()
        val registry = ClarificationRegistry(clock)
        val contacts = FakeContactsResolver(listOf(
            ContactCandidate("1", "Rahul K", phoneNumber = "+15550101"),
            ContactCandidate("2", "Priya", phoneNumber = "+15550102")
        ))
        val extLauncher = TestExternalActionLauncher()
        val extStore = TestExternalStore(extLauncher)
        val taskStore = TestTaskStore()
        val promiseStore = TestPromiseStore()
        val reminderStore = TestReminderStore()

        val taskAgent = ConfirmedTaskAgent(TestReads(), taskStore, { zone })
        val reminderAgent = ConfirmedReminderAgent(
            taskAgent, reminderStore, { zone }, clock, contacts, registry = registry, memory = memory
        )
        val promiseAgent = ConfirmedPromiseAgent(
            reminderAgent, promiseStore, { zone }, clock, contacts, registry = registry, memory = memory
        )
        val agent = ConfirmedExternalActionAgent(
            promiseAgent, extStore, contacts, { zone }, clock, registry = registry, memory = memory
        )

        return TestHarness(agent, memory, registry, extLauncher)
    }

    private inner class TestHarness(
        val agent: ConfirmedExternalActionAgent,
        val memory: SessionWorkingMemory,
        val registry: ClarificationRegistry,
        val launcher: TestExternalActionLauncher
    ) {
        fun request(text: String) =
            AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, now, conversationId = session)
    }

    private class TestReads : AgentReadPort {
        override suspend fun tasks(): String = "Open tasks: None"
        override suspend fun memories(query: String, at: Long): String = "No memories."
        override suspend fun promises(person: String): SpecialistResult =
            SpecialistResult.Answer("No promises for $person.")
    }

    private inner class TestTaskStore : AcceptedTaskStore {
        override suspend fun resolveCompletion(title: String): CompletionSelection =
            CompletionSelection.Unavailable
        override suspend fun resolveCompletion(title: String, fuzzy: Boolean): CompletionSelection =
            CompletionSelection.Unavailable
        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )
        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt =
            ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, ActionType.TASK,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
                "Task operation executed"
            )
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
        var notificationsAvailable: Boolean = true
        val writes = mutableListOf<Action>()
        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(
                now,
                if (notificationsAvailable) {
                    setOf(
                        Capability.LOCAL_READ, Capability.LOCAL_WRITE,
                        Capability.REMINDER_SCHEDULE, Capability.NOTIFICATIONS
                    )
                } else {
                    setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE, Capability.REMINDER_SCHEDULE)
                }
            ),
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

    private class TestExternalActionLauncher : ExternalActionLauncher {
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

    private inner class TestExternalStore(private val launcher: ExternalActionLauncher) : AcceptedExternalActionStore {
        override suspend fun context(request: AgentRequest) = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(
                now,
                setOf(
                    Capability.LOCAL_READ, Capability.LOCAL_WRITE,
                    Capability.TIMER, Capability.CLOCK_ALARM, Capability.DIALER, Capability.MESSAGE_COMPOSER
                )
            ),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )

        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            val action = proposal.action
            when (action) {
                is CallAction -> launcher.launchDial(action.phoneNumber)
                is MessageAction -> launcher.launchSendTo(action.destination.schemeSpecificPart, action.body)
                else -> Unit
            }
            return ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, proposal.action.type,
                ReceiptOutcome(ActionStatus.SUCCEEDED, now, now, OutcomeVerification.VERIFIED_LOCAL),
                "External action launched"
            )
        }
    }
}
