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
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.UndoResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTimerActionTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val session = UUID.randomUUID()

    @Test
    fun grammarExtractsTimerDurationAndOptionalLabel() {
        val rules = EnglishRules { zone }
        val r1 = rules.parse(request("set a timer for 5 minutes"))
        assertEquals(AgentIntent.CREATE_TIMER, r1.intent)
        val d1 = r1.entities.single { it.type == EntityKind.DURATION }
        assertEquals("5", d1.rawValue)

        val r2 = rules.parse(request("start timer for 30 seconds"))
        assertEquals(AgentIntent.CREATE_TIMER, r2.intent)
        val d2 = r2.entities.single { it.type == EntityKind.DURATION }
        assertEquals("30", d2.rawValue)
    }

    @Test
    fun languageSpecialistReturnsTimerProposalWhenDurationValid(): Unit = runBlocking {
        val specialist = LanguageSpecialist(dummyReads())
        val rules = EnglishRules { zone }
        val req = request("set a timer for 5 minutes")
        val parsed = rules.parse(req)
        val context = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.TIMER)),
            conversationId = req.conversationId,
            screenContext = req.screenContext
        )
        val parsedReq = dev.metis.agent.domain.agent.ParsedRequest(
            req, parsed.prediction, parsed.entities, context
        )
        val response = specialist.respond(parsedReq)
        assertTrue(response is SpecialistResult.Suggestion)
        val suggestion = response as SpecialistResult.Suggestion
        assertTrue(suggestion.action is CreateTimerAction)
        val timer = suggestion.action as CreateTimerAction
        assertEquals(300L, timer.durationSeconds)
        assertEquals("Timer", timer.label)
        assertTrue(suggestion.reason.contains("300 seconds"))
    }

    @Test
    fun languageSpecialistReturnsClarificationWhenDurationInvalid(): Unit = runBlocking {
        val specialist = LanguageSpecialist(dummyReads())
        val req = request("set a timer for 0 seconds")
        val context = ResolvedContext(
            now, zone, AutonomyLevel.SUGGEST,
            CapabilitySnapshot(now, setOf(Capability.TIMER)),
            conversationId = req.conversationId,
            screenContext = req.screenContext
        )
        val parsedReq = dev.metis.agent.domain.agent.ParsedRequest(
            req,
            dev.metis.agent.domain.agent.IntentPrediction(AgentIntent.CREATE_TIMER, 1.0),
            emptyList(),
            context
        )
        val response = specialist.respond(parsedReq)
        assertTrue(response is SpecialistResult.Clarification)
        val clarification = response as SpecialistResult.Clarification
        assertEquals("Use a timer duration from 1 second to 1 day.", clarification.question)
        assertEquals(setOf("duration"), clarification.fields)
    }

    @Test
    fun agentProposesTimerWithoutLaunching(): Unit = runBlocking {
        val launcher = FakeExternalActionLauncher()
        val store = FakeExternalStore(launcher)
        val agent = agent(store)

        val result = agent.process(request("set a timer for 5 minutes"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        assertTrue(proposal.action is CreateTimerAction)
        val timer = proposal.action as CreateTimerAction
        assertEquals(300L, timer.durationSeconds)
        assertEquals("Timer", timer.label)
        // Verify launch has NOT happened yet
        assertTrue(launcher.launchedTimers.isEmpty())
    }

    @Test
    fun acceptingTimerLaunchesAlarmClockAndReturnsAnswerWithReceipt(): Unit = runBlocking {
        val launcher = FakeExternalActionLauncher()
        val store = FakeExternalStore(launcher)
        val agent = agent(store)

        val result = agent.process(request("set a timer for 5 minutes"))
        val proposal = result.proposals.single()

        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, launcher.launchedTimers.size)
        val call = launcher.launchedTimers.single()
        assertEquals(300, call.lengthSeconds)
        assertEquals("Timer", call.message)

        val receipt = accepted.completedActions.single()
        assertEquals(ActionType.TIMER, receipt.actionType)
        assertEquals(ActionStatus.SUCCEEDED, receipt.outcome.status)
        assertEquals(OutcomeVerification.VERIFIED_PLATFORM, receipt.outcome.verification)
        assertEquals(1, store.writes)

        // Second acceptance should be denied
        val duplicate = agent.accept(proposal)
        assertEquals(AgentResultStatus.DENIED, duplicate.status)
        assertEquals(1, launcher.launchedTimers.size)
    }

    @Test
    fun timerNoHandlerFailureReturnsFailedResult(): Unit = runBlocking {
        val launcher = FakeExternalActionLauncher(
            timerResult = ExternalLaunchResult.NoHandler("No timer app was found to start this timer.")
        )
        val store = FakeExternalStore(launcher)
        val agent = agent(store)

        val result = agent.process(request("set a timer for 10 minutes"))
        val proposal = result.proposals.single()

        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.FAILED, accepted.status)
        assertEquals("No timer app was found to start this timer.", accepted.message)
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
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE, Capability.TIMER)),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )

        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            val timer = proposal.action as CreateTimerAction
            val launch = launcher.launchTimer(timer.durationSeconds.toInt(), timer.label)
            return when (launch) {
                is ExternalLaunchResult.Success -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.TIMER,
                    ReceiptOutcome(
                        ActionStatus.SUCCEEDED,
                        now,
                        now,
                        OutcomeVerification.VERIFIED_PLATFORM
                    ),
                    "Started timer for ${timer.durationSeconds} seconds."
                )
                is ExternalLaunchResult.NoHandler -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.TIMER,
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
                    ActionType.TIMER,
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

