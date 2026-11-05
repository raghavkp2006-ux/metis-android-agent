@file:Suppress("MagicNumber", "MaxLineLength", "TooManyFunctions")

package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedExternalActionStore
import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.AcceptedReminderStore
import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.Action
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.ExternalActionLauncher
import dev.metis.agent.domain.agent.ExternalLaunchResult
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarificationFollowUpTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    // Wednesday 2026-10-14 10:00:00 UTC (15:30 IST)
    private val now = Instant.parse("2026-10-14T10:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val session = UUID.randomUUID()

    private fun harness(): TestHarness = createHarness(clock)

    // Phrase a: Remind me tomorrow to call Rahul -> 8pm
    @Test
    fun phraseA_remindTomorrowToCallRahul_reply8pm(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("Remind me tomorrow to call Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("What time?", turn1.message)
        assertEquals(setOf("time"), turn1.followUp?.missingFields)
        assertTrue(turn1.proposals.isEmpty())

        val turn2 = h.agent.process(h.request("8pm"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val reminder = proposal.action as CreateReminderAction
        assertEquals("call Rahul", reminder.title)
        assertEquals(20, reminder.trigger.local.hour)
        assertEquals(0, reminder.trigger.local.minute)
        assertEquals(0, h.reminderStore.writes.size)

        // Verify explicit Accept required (no auto-execution)
        val accepted = h.agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, h.reminderStore.writes.size)
    }

    // Phrase b: Remind me Friday to buy milk -> at 9
    @Test
    fun phraseB_remindFridayToBuyMilk_replyAt9(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("Remind me Friday to buy milk"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("What time?", turn1.message)
        assertTrue(turn1.proposals.isEmpty())

        val turn2 = h.agent.process(h.request("at 9"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val reminder = proposal.action as CreateReminderAction
        assertEquals("buy milk", reminder.title)
        assertEquals(9, reminder.trigger.local.hour)
        assertEquals(0, reminder.trigger.local.minute)
        assertEquals(java.time.DayOfWeek.FRIDAY, reminder.trigger.local.dayOfWeek)
        assertEquals(0, h.reminderStore.writes.size)

        val accepted = h.agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, h.reminderStore.writes.size)
    }

    @Test
    fun phraseB_remindFridayToBuyMilk_missingNotificationPermission(): Unit = runBlocking {
        val h = harness()
        h.reminderStore.notificationsAvailable = false
        val turn1 = h.agent.process(h.request("Remind me Friday to buy milk"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("What time?", turn1.message)

        val turn2 = h.agent.process(h.request("at 9"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn2.status)
        assertEquals("Notification permission is needed to do this. Allow?", turn2.message)
        assertEquals(setOf("notification_permission"), turn2.followUp?.missingFields)

        val turn3 = h.agent.process(h.request("no"))
        assertEquals(AgentResultStatus.DENIED, turn3.status)
        assertEquals("Cannot schedule reminder without notification permission.", turn3.message)
        assertTrue(turn3.proposals.isEmpty())
        assertEquals(0, h.reminderStore.writes.size)
    }

    // Phrase c: kal call karna hai Rahul ko remind karo -> subah 10 baje
    @Test
    fun phraseC_kalCallKarnaHaiRahulKoRemindKaro_replySubah10Baje(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("kal call karna hai Rahul ko remind karo"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("What time?", turn1.message)
        assertTrue(turn1.proposals.isEmpty())

        val turn2 = h.agent.process(h.request("subah 10 baje"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val reminder = proposal.action as CreateReminderAction
        assertEquals("call karna hai Rahul ko", reminder.title)
        assertEquals(10, reminder.trigger.local.hour)
        assertEquals(0, reminder.trigger.local.minute)
        assertEquals(0, h.reminderStore.writes.size)

        val accepted = h.agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, h.reminderStore.writes.size)
    }

    @Test
    fun phraseC_kalCallKarnaHaiRahulKoRemindKaro_missingNotificationPermission(): Unit = runBlocking {
        val h = harness()
        h.reminderStore.notificationsAvailable = false
        val turn1 = h.agent.process(h.request("kal call karna hai Rahul ko remind karo"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("What time?", turn1.message)

        val turn2 = h.agent.process(h.request("subah 10 baje"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn2.status)
        assertEquals("Notification permission is needed to do this. Allow?", turn2.message)
        assertEquals(setOf("notification_permission"), turn2.followUp?.missingFields)

        val turn3 = h.agent.process(h.request("don't allow"))
        assertEquals(AgentResultStatus.DENIED, turn3.status)
        assertEquals("Cannot schedule reminder without notification permission.", turn3.message)
        assertTrue(turn3.proposals.isEmpty())
        assertEquals(0, h.reminderStore.writes.size)
    }

    // Phrase d: wake me at 6 -> am
    @Test
    fun phraseD_wakeMeAt6_replyAm(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("AM or PM?", turn1.message)
        assertEquals(setOf("am_pm"), turn1.followUp?.missingFields)
        assertTrue(turn1.proposals.isEmpty())

        val turn2 = h.agent.process(h.request("am"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val alarm = proposal.action as CreateAlarmAction
        assertEquals(6, alarm.trigger.local.hour)
        assertEquals(0, alarm.trigger.local.minute)
        assertEquals(0, h.launcher.launchedAlarms.size)

        // Safety: verify proposal requires Accept
        val accepted = h.agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, h.launcher.launchedAlarms.size)
        assertEquals("6:0:Alarm", h.launcher.launchedAlarms.single())
    }

    // Phrase e: kal 8 baje alarm laga do -> subah
    @Test
    fun phraseE_kal8BajeAlarmLagaDo_replySubah(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("kal 8 baje alarm laga do"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("AM or PM?", turn1.message)
        assertTrue(turn1.proposals.isEmpty())

        val turn2 = h.agent.process(h.request("subah"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val alarm = proposal.action as CreateAlarmAction
        assertEquals(8, alarm.trigger.local.hour)
        assertEquals(0, alarm.trigger.local.minute)
        val expectedTomorrow = now.atZone(zone).toLocalDate().plusDays(1)
        assertEquals(expectedTomorrow, alarm.trigger.local.toLocalDate())
        assertEquals(0, h.launcher.launchedAlarms.size)
    }

    // Phrase f: set alarm for 7 -> pm
    @Test
    fun phraseF_setAlarmFor7_replyPm(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("set alarm for 7"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("AM or PM?", turn1.message)
        assertTrue(turn1.proposals.isEmpty())

        val turn2 = h.agent.process(h.request("pm"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val alarm = proposal.action as CreateAlarmAction
        assertEquals(19, alarm.trigger.local.hour)
        assertEquals(0, alarm.trigger.local.minute)
        assertEquals(0, h.launcher.launchedAlarms.size)
    }

    // Phrase g: call him -> David S
    @Test
    fun phraseG_callHim_replyDavidS(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("call him"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("Who do you mean?", turn1.message)
        assertEquals(setOf("person"), turn1.followUp?.missingFields)
        assertTrue(turn1.proposals.isEmpty())

        val turn2 = h.agent.process(h.request("David S"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val call = proposal.action as CallAction
        assertEquals("David S", call.targetLabel)
        assertEquals("+15550103", call.phoneNumber)
        assertEquals(0, h.launcher.launchedCalls.size)

        // Safety: verify proposal requires Accept
        val accepted = h.agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, h.launcher.launchedCalls.size)
        assertEquals("+15550103", h.launcher.launchedCalls.single())
    }

    // Phrase h: call her -> Priya
    @Test
    fun phraseH_callHer_replyPriya(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("call her"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("Who do you mean?", turn1.message)
        assertTrue(turn1.proposals.isEmpty())

        val turn2 = h.agent.process(h.request("Priya"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val call = proposal.action as CallAction
        assertEquals("Priya", call.targetLabel)
        assertEquals("+15550102", call.phoneNumber)
        assertEquals(0, h.launcher.launchedCalls.size)

        // Safety: verify proposal requires Accept
        val accepted = h.agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, h.launcher.launchedCalls.size)
        assertEquals("+15550102", h.launcher.launchedCalls.single())
    }

    // Phrase i: wake me at 6 -> (user says "set timer 10m" instead) -> drops clarification, sets timer
    @Test
    fun phraseI_wakeMeAt6_userSaysSetTimer10m_dropsClarificationSetsTimer(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("AM or PM?", turn1.message)

        val turn2 = h.agent.process(h.request("set timer 10m"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val proposal = turn2.proposals.single()
        val timer = proposal.action as CreateTimerAction
        assertEquals(600L, timer.durationSeconds)
        assertNull(h.agent.activeClarification)
    }

    // Phrase j: wake me at 6 -> user says "never mind" -> dropped cleanly
    @Test
    fun phraseJ1_wakeMeAt6_userSaysNeverMind_droppedCleanly(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("AM or PM?", turn1.message)

        val turn2 = h.agent.process(h.request("never mind"))
        assertEquals(AgentResultStatus.DENIED, turn2.status)
        assertEquals("Clarification cancelled.", turn2.message)
        assertTrue(turn2.proposals.isEmpty())
        assertNull(h.agent.activeClarification)
    }

    // Phrase j2: wake me at 6 -> 2 min elapse -> dropped cleanly, new request evaluated fresh
    @Test
    fun phraseJ2_wakeMeAt6_expiresAfter2Minutes_droppedCleanly(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("AM or PM?", turn1.message)

        // 125 seconds later, user says "am" - clarification has expired, so "am" is treated fresh (UNKNOWN)
        val expiredRequest = h.request("am", timestamp = now.plusSeconds(125))
        val turn2 = h.agent.process(expiredRequest)
        assertNull(h.agent.activeClarification)
        assertTrue(turn2.proposals.isEmpty())
    }

    @Test
    fun replyIsHandledExactlyOnce_reminder(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("Remind me tomorrow to call Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)

        // Turn 2 consumes clarification atomically
        val turn2 = h.agent.process(h.request("8pm"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        assertEquals(1, turn2.proposals.size)

        // Turn 3 repeating the same answer cannot consume again (state is consumed)
        val turn3 = h.agent.process(h.request("8pm"))
        assertTrue("Subsequent repeat reply must not yield duplicate proposal", turn3.proposals.isEmpty())
    }

    @Test
    fun replyIsHandledExactlyOnce_alarm(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)

        // Turn 2 consumes clarification
        val turn2 = h.agent.process(h.request("am"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        assertEquals(1, turn2.proposals.size)

        // Turn 3 repeating the same answer cannot consume again
        val turn3 = h.agent.process(h.request("am"))
        assertTrue("Subsequent repeat reply must not yield duplicate proposal", turn3.proposals.isEmpty())
    }

    @Test
    fun typoReply_alarm_reAsksOnce_maxTwoTurns(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("AM or PM?", turn1.message)

        // First invalid reply ("8 pn"): re-asks once, does not drop
        val turn2 = h.agent.process(h.request("8 pn"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn2.status)
        assertEquals("AM or PM?", turn2.message)
        assertTrue(turn2.proposals.isEmpty())

        // Valid reply on turn 3 succeeds
        val turn3 = h.agent.process(h.request("am"))
        assertEquals(AgentResultStatus.PROPOSAL, turn3.status)
        val alarm = turn3.proposals.single().action as CreateAlarmAction
        assertEquals(6, alarm.trigger.local.hour)
    }

    @Test
    fun typoReply_alarm_cancelledAfterTwoInvalidTurns(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)

        // First typo: re-asks once
        val turn2 = h.agent.process(h.request("8 pn"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn2.status)

        // Second typo: cancels after 2 turns
        val turn3 = h.agent.process(h.request("invalid again"))
        assertEquals(AgentResultStatus.DENIED, turn3.status)
        assertEquals("Clarification cancelled.", turn3.message)
        assertNull(h.agent.activeClarification)
    }

    @Test
    fun typoReply_reminder_reAsksOnce_maxTwoTurns(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("Remind me tomorrow to call Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertEquals("What time?", turn1.message)

        // First invalid reply ("8 pn"): re-asks once, does not drop
        val turn2 = h.agent.process(h.request("8 pn"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn2.status)
        assertEquals("What time?", turn2.message)
        assertTrue(turn2.proposals.isEmpty())

        // Valid reply on turn 3 succeeds
        val turn3 = h.agent.process(h.request("8pm"))
        assertEquals(AgentResultStatus.PROPOSAL, turn3.status)
        val reminder = turn3.proposals.single().action as CreateReminderAction
        assertEquals(20, reminder.trigger.local.hour)
    }

    @Test
    fun typoReply_reminder_cancelledAfterTwoInvalidTurns(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("Remind me tomorrow to call Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)

        // First typo: re-asks once
        val turn2 = h.agent.process(h.request("8 pn"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn2.status)

        // Second typo: cancels after 2 invalid turns
        val turn3 = h.agent.process(h.request("invalid again"))
        assertEquals(AgentResultStatus.DENIED, turn3.status)
        assertEquals("Reminder clarification cancelled after two invalid time answers.", turn3.message)
    }

    @Test
    fun regression_1Point5HrTimerResolvesTo5400s(): Unit = runBlocking {
        val h = harness()
        val result = h.agent.process(h.request("1.5 hr timer"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val timer = result.proposals.single().action as CreateTimerAction
        assertEquals(5400L, timer.durationSeconds)
    }

    @Test
    fun regression_bareWakeMeAt6PromptsAmOrPmClarification(): Unit = runBlocking {
        val h = harness()
        val result = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertEquals("AM or PM?", result.message)
        assertEquals(setOf("am_pm"), result.followUp?.missingFields)
        assertTrue(result.proposals.isEmpty())
    }

    @Test
    fun callAmbiguity_replyByOrdinal_second_resolvesUsingFakeProvider(): Unit = runBlocking {
        val contacts = FakeContactsResolver(listOf(
            ContactCandidate("1", "David A", phoneNumber = "+15550101"),
            ContactCandidate("2", "David B", phoneNumber = "+15550102")
        ))
        val h = harness()
        val agent = ConfirmedExternalActionAgent(
            fallback = h.agent,
            store = h.externalStore,
            contacts = contacts,
            zone = { zone },
            clock = clock
        )
        val turn1 = agent.process(h.request("call David"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertTrue(turn1.message.contains("Multiple contacts found"))

        // Reply with ordinal "second" -> selects second candidate (David B)
        val turn2 = agent.process(h.request("second"))
        assertEquals(AgentResultStatus.PROPOSAL, turn2.status)
        val call = turn2.proposals.single().action as CallAction
        assertEquals("David B", call.targetLabel)
    }

    @Test
    fun declinedContactsPermission_failsClosedWithoutLooping(): Unit = runBlocking {
        val h = harness()
        val contacts = FakeContactsResolver(emptyList(), permitted = false)
        val agent = ConfirmedExternalActionAgent(
            fallback = h.agent,
            store = h.externalStore,
            contacts = contacts,
            zone = { zone },
            clock = clock
        )
        val res = agent.process(h.request("call David"))
        assertEquals(AgentResultStatus.ANSWER, res.status)
        assertEquals("Contacts permission is needed to find this contact.", res.message)
        assertTrue(res.proposals.isEmpty())
    }

    @Test
    fun multiTurnClarification_expiresAfterTimeout_dropsPendingState(): Unit = runBlocking {
        val h = harness()
        val turn1 = h.agent.process(h.request("wake me at 6"))
        assertEquals(AgentResultStatus.FOLLOW_UP, turn1.status)
        assertNotNull(h.agent.activeClarification)

        // Request timestamp after 125 seconds (> 120s expiry)
        val expiredReq = h.request("am").copy(timestamp = now.plusSeconds(125))
        val turn2 = h.agent.process(expiredReq)
        assertEquals(AgentResultStatus.DENIED, turn2.status)
        assertNull(h.agent.activeClarification)
    }

    private fun createHarness(testClock: Clock): TestHarness {
        val taskStore = TestTaskStore()
        val promiseStore = TestPromiseStore()
        val reminderStore = TestReminderStore()
        val launcher = TrackingExternalActionLauncher()
        val externalStore = TrackingExternalStore(launcher)
        val resolver = TestContacts()

        val taskAgent = ConfirmedTaskAgent(
            reads = TestReads(),
            store = taskStore,
            zone = { zone }
        )
        val reminderAgent = ConfirmedReminderAgent(
            fallback = taskAgent,
            store = reminderStore,
            zone = { zone },
            clock = testClock,
            contacts = resolver
        )
        val promiseAgent = ConfirmedPromiseAgent(
            fallback = reminderAgent,
            store = promiseStore,
            zone = { zone },
            clock = testClock,
            contacts = resolver
        )
        val externalAgent = ConfirmedExternalActionAgent(
            fallback = promiseAgent,
            store = externalStore,
            contacts = resolver,
            zone = { zone },
            clock = testClock
        )

        return TestHarness(externalAgent, reminderStore, launcher, externalStore)
    }

    private inner class TestHarness(
        val agent: ConfirmedExternalActionAgent,
        val reminderStore: TestReminderStore,
        val launcher: TrackingExternalActionLauncher,
        val externalStore: TrackingExternalStore,
    ) {
        fun request(text: String, timestamp: Instant = now) = AgentRequest(
            id = UUID.randomUUID(),
            text = text,
            source = InputSource.TEXT,
            timestamp = timestamp,
            conversationId = session
        )
    }

    private class TestReads : AgentReadPort {
        override suspend fun tasks(): String = "Open tasks: None"
        override suspend fun memories(query: String, at: Long): String = "No memories."
        override suspend fun promises(person: String): SpecialistResult =
            SpecialistResult.Answer("No promises for $person.")
    }

    private inner class TestTaskStore : AcceptedTaskStore {
        override suspend fun resolveCompletion(title: String): CompletionSelection = CompletionSelection.Unavailable
        override suspend fun resolveCompletion(title: String, fuzzy: Boolean): CompletionSelection = CompletionSelection.Unavailable
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
                    setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE, Capability.REMINDER_SCHEDULE, Capability.NOTIFICATIONS)
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
            when (action) {
                is CreateTimerAction -> launcher.launchTimer(action.durationSeconds.toInt(), action.label)
                is CreateAlarmAction -> launcher.launchAlarm(action.trigger.local.hour, action.trigger.local.minute, action.label)
                is CallAction -> launcher.launchDial(action.phoneNumber)
                else -> Unit
            }
            val status = if (action.type == ActionType.CALL || action.type == ActionType.MESSAGE)
                ActionStatus.HANDED_OFF else ActionStatus.SUCCEEDED
            val verification = if (status == ActionStatus.HANDED_OFF)
                OutcomeVerification.HANDOFF_ONLY else OutcomeVerification.VERIFIED_PLATFORM
            return ActionReceipt(
                UUID.randomUUID(), proposal.action.identity, proposal.action.type,
                ReceiptOutcome(status, now, now, verification),
                "External action launched"
            )
        }
    }

    private class TestContacts : ContactsResolver {
        private val list = listOf(
            ContactCandidate("1", "David S", phoneNumber = "+15550103"),
            ContactCandidate("2", "Priya", phoneNumber = "+15550102"),
            ContactCandidate("3", "David", phoneNumber = "+15550103"),
        )
        override suspend fun resolve(query: String): ContactResolution {
            val q = query.trim().lowercase(java.util.Locale.ROOT)
            val match = list.firstOrNull { it.displayName.lowercase(java.util.Locale.ROOT) == q }
            return if (match != null) ContactResolution.Resolved(match) else ContactResolution.NotFound
        }
    }
}
