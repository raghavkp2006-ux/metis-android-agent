package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedExternalActionStore
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
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.UndoResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAlarmActionTest {
    // 2026-10-28 12:00 UTC = 2026-10-28 17:30 IST (Asia/Kolkata)
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val session = UUID.randomUUID()

    @Test
    fun grammarExtractsAlarmTimesAndLabels() {
        val rules = EnglishRules { zone }

        // "set an alarm for 7 am" - at 5:30 PM, 7 am today has passed, so rolls to tomorrow 7 am
        val r1 = rules.parse(request("set an alarm for 7 am"))
        assertEquals(AgentIntent.CREATE_ALARM, r1.intent)
        val t1 = r1.entities.single { it.type == EntityKind.TIME }
        val norm1 = t1.normalizedValue as NormalizedValue.Time
        assertEquals(LocalTime.of(7, 0), norm1.value.local.toLocalTime())
        assertEquals(LocalDate.parse("2026-10-29"), norm1.value.local.toLocalDate())

        // "wake me at 6:30 tomorrow"
        val r2 = rules.parse(request("wake me at 6:30 tomorrow"))
        assertEquals(AgentIntent.CREATE_ALARM, r2.intent)
        val t2 = r2.entities.single { it.type == EntityKind.TIME }
        val norm2 = t2.normalizedValue as NormalizedValue.Time
        assertEquals(LocalTime.of(6, 30), norm2.value.local.toLocalTime())
        assertEquals(LocalDate.parse("2026-10-29"), norm2.value.local.toLocalDate())

        // "wake me up at 6:30 tomorrow"
        val r3 = rules.parse(request("wake me up at 6:30 tomorrow"))
        assertEquals(AgentIntent.CREATE_ALARM, r3.intent)

        // with label
        val r4 = rules.parse(request("set an alarm for 7 am called Work"))
        assertEquals(AgentIntent.CREATE_ALARM, r4.intent)
        val label = r4.entities.single { it.type == EntityKind.TITLE }
        assertEquals("Work", label.rawValue)
    }

    @Test
    fun languageSpecialistReturnsAlarmProposalWhenCapabilityGranted(): Unit = runBlocking {
        val specialist = LanguageSpecialist(dummyReads())
        val rules = EnglishRules { zone }
        val req = request("set an alarm for 7 am")
        val parsed = rules.parse(req)
        val context = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.CLOCK_ALARM)),
            conversationId = req.conversationId,
            screenContext = req.screenContext
        )
        val parsedReq = dev.metis.agent.domain.agent.ParsedRequest(
            req, parsed.prediction, parsed.entities, context
        )
        val response = specialist.respond(parsedReq)
        assertTrue(response is SpecialistResult.Suggestion)
        val suggestion = response as SpecialistResult.Suggestion
        assertTrue(suggestion.action is CreateAlarmAction)
        val alarm = suggestion.action as CreateAlarmAction
        assertEquals(7, alarm.trigger.local.hour)
        assertEquals(0, alarm.trigger.local.minute)
        assertEquals("Alarm", alarm.label)
    }

    @Test
    fun languageSpecialistReturnsUnsupportedWhenCapabilityMissing(): Unit = runBlocking {
        val specialist = LanguageSpecialist(dummyReads())
        val rules = EnglishRules { zone }
        val req = request("set an alarm for 7 am")
        val parsed = rules.parse(req)
        val context = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ)), // no CLOCK_ALARM
            conversationId = req.conversationId,
            screenContext = req.screenContext
        )
        val parsedReq = dev.metis.agent.domain.agent.ParsedRequest(
            req, parsed.prediction, parsed.entities, context
        )
        val response = specialist.respond(parsedReq)
        assertTrue(response is SpecialistResult.Unsupported)
    }

    @Test
    fun externalAgentProposesAlarmAndExecutesOnlyAfterAccept(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val agent = agent(store)

        val result = agent.process(request("set an alarm for 7 am"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        assertTrue(proposal.action is CreateAlarmAction)
        val alarm = proposal.action as CreateAlarmAction
        assertEquals(7, alarm.trigger.local.hour)
        assertEquals(0, alarm.trigger.local.minute)

        // Ensure launch has NOT happened before accept
        assertEquals(0, fakeLauncher.launchedAlarms.size)
        assertEquals(0, store.writes)

        // Explicit accept
        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, fakeLauncher.launchedAlarms.size)
        val launched = fakeLauncher.launchedAlarms.single()
        assertEquals(7, launched.hour)
        assertEquals(0, launched.minutes)
        assertEquals("Alarm", launched.message)
        assertEquals(1, store.writes)
        assertEquals(1, accepted.completedActions.size)
        assertEquals(ActionType.ALARM, accepted.completedActions.single().actionType)

        // Second accept is denied
        val duplicate = agent.accept(proposal)
        assertEquals(AgentResultStatus.DENIED, duplicate.status)
        assertEquals(1, fakeLauncher.launchedAlarms.size)
    }

    @Test
    fun wakeMeAt630TomorrowProducesProposal(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val agent = agent(store)

        val result = agent.process(request("wake me at 6:30 tomorrow"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        val alarm = proposal.action as CreateAlarmAction
        assertEquals(6, alarm.trigger.local.hour)
        assertEquals(30, alarm.trigger.local.minute)

        agent.accept(proposal)
        assertEquals(1, fakeLauncher.launchedAlarms.size)
        assertEquals(6, fakeLauncher.launchedAlarms.single().hour)
        assertEquals(30, fakeLauncher.launchedAlarms.single().minutes)
    }

    @Test
    fun discardedAlarmProposalCannotBeAccepted(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val agent = agent(store)

        val result = agent.process(request("set an alarm for 7 am"))
        val proposal = result.proposals.single()

        agent.discardProposal()

        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.DENIED, accepted.status)
        assertEquals(0, fakeLauncher.launchedAlarms.size)
        assertEquals(0, store.writes)
    }

    @Test
    fun noAlarmHandlerReturnsFailedWithClearMessage(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher().apply {
            alarmResult = ExternalLaunchResult.NoHandler("No alarm app was found to set this alarm.")
        }
        val store = FakeExternalStore(fakeLauncher)
        val agent = agent(store)

        val result = agent.process(request("set an alarm for 7 am"))
        val proposal = result.proposals.single()

        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.FAILED, accepted.status)
        assertEquals("No alarm app was found to set this alarm.", accepted.message)
        assertTrue(accepted.completedActions.isEmpty())
        assertEquals(1, store.writes)
    }

    private fun agent(store: AcceptedExternalActionStore): ConfirmedExternalActionAgent =
        ConfirmedExternalActionAgent(
            fallback = object : ConfirmableAgent {
                override suspend fun process(request: AgentRequest) = AgentResult(
                    request.id, AgentResultStatus.ANSWER, "Fallback process"
                )
                override suspend fun accept(proposal: ActionProposal) = AgentResult(
                    proposal.requestId, AgentResultStatus.DENIED, "Fallback accept"
                )
                override fun discardProposal() = Unit
                override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
            },
            store = store,
            zone = { zone },
            clock = Clock.fixed(now, zone)
        )

    private fun request(text: String) = AgentRequest(
        UUID.randomUUID(), text, InputSource.TEXT, now, conversationId = session
    )

    private fun dummyReads() = object : AgentReadPort {
        override suspend fun tasks(): String = ""
        override suspend fun memories(query: String, at: Long): String = ""
        override suspend fun promises(person: String): SpecialistResult =
            SpecialistResult.Unsupported("Unavailable")
    }

    private inner class FakeExternalStore(private val launcher: FakeExternalActionLauncher) :
        AcceptedExternalActionStore {
        var writes = 0
        override suspend fun context(request: AgentRequest): ResolvedContext = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(
                now,
                setOf(
                    Capability.LOCAL_READ,
                    Capability.LOCAL_WRITE,
                    Capability.TIMER,
                    Capability.CLOCK_ALARM,
                    Capability.DIALER,
                    Capability.MESSAGE_COMPOSER
                )
            ),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )

        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            val alarm = proposal.action as CreateAlarmAction
            val launch = launcher.launchAlarm(
                alarm.trigger.local.hour,
                alarm.trigger.local.minute,
                alarm.label
            )
            return when (launch) {
                is ExternalLaunchResult.Success -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.ALARM,
                    ReceiptOutcome(
                        ActionStatus.SUCCEEDED,
                        now,
                        now,
                        OutcomeVerification.VERIFIED_PLATFORM
                    ),
                    "Set alarm for ${alarm.trigger.local.toLocalTime()}."
                )
                is ExternalLaunchResult.NoHandler -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.ALARM,
                    ReceiptOutcome(
                        ActionStatus.FAILED,
                        now,
                        now,
                        OutcomeVerification.UNVERIFIED,
                        safeErrorCode = SafeErrorCode.UNSUPPORTED
                    ),
                    launch.message
                )
                is ExternalLaunchResult.Failed -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.ALARM,
                    ReceiptOutcome(
                        ActionStatus.FAILED,
                        now,
                        now,
                        OutcomeVerification.UNVERIFIED,
                        safeErrorCode = SafeErrorCode.OUTCOME_UNKNOWN
                    ),
                    launch.message
                )
            }
        }
    }
}
