package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedExternalActionStore
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.CallMode
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalCallActionTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val session = UUID.randomUUID()

    @Test
    fun grammarExtractsPersonForCallIntent() {
        val rules = EnglishRules { zone }

        val r1 = rules.parse(request("call Rahul"))
        assertEquals(AgentIntent.CALL_PERSON, r1.intent)
        assertEquals("Rahul", r1.entities.single { it.type == EntityKind.PERSON }.rawValue)

        val r2 = rules.parse(request("dial Rahul"))
        assertEquals(AgentIntent.CALL_PERSON, r2.intent)
        assertEquals("Rahul", r2.entities.single { it.type == EntityKind.PERSON }.rawValue)

        val r3 = rules.parse(request("phone Mom"))
        assertEquals(AgentIntent.CALL_PERSON, r3.intent)
        assertEquals("Mom", r3.entities.single { it.type == EntityKind.PERSON }.rawValue)
    }

    @Test
    fun resolvedContactShowsProposalWithResolvedNumberAndDialsOnlyAfterAccept(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String): ContactResolution =
                if (query.equals("Rahul", true)) {
                    ContactResolution.Resolved(
                        ContactCandidate("id-1", "Rahul Sharma", phoneNumber = "+91 98765 43210")
                    )
                } else ContactResolution.NotFound
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("call Rahul"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        assertTrue(proposal.action is CallAction)
        val call = proposal.action as CallAction
        assertEquals("Rahul Sharma", call.targetLabel)
        assertEquals("+91 98765 43210", call.phoneNumber)
        assertEquals(CallMode.DIAL, call.mode)
        assertTrue(proposal.reason.contains("+91 98765 43210"))
        assertFalse(call.toString().contains("+91 98765 43210"))
        assertTrue(call.toString().contains("[REDACTED]"))

        // Dialer NOT launched before explicit accept
        assertEquals(0, fakeLauncher.launchedDials.size)
        assertEquals(0, store.writes)

        // Explicit accept triggers ACTION_DIAL
        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(listOf("+91 98765 43210"), fakeLauncher.launchedDials)
        assertEquals(1, store.writes)
        val receipt = accepted.completedActions.single()
        assertEquals(ActionType.CALL, receipt.actionType)
        assertEquals(ActionStatus.HANDED_OFF, receipt.outcome.status)
        assertEquals(OutcomeVerification.HANDOFF_ONLY, receipt.outcome.verification)

        // Second accept is denied
        val duplicate = agent.accept(proposal)
        assertEquals(AgentResultStatus.DENIED, duplicate.status)
        assertEquals(1, fakeLauncher.launchedDials.size)
    }

    @Test
    fun ambiguousContactRequestsClarification(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String): ContactResolution =
                ContactResolution.Ambiguous(
                    listOf(
                        ContactCandidate("id-1", "Rahul Verma", phoneNumber = "+91 98111 11111"),
                        ContactCandidate("id-2", "Rahul Sharma", phoneNumber = "+91 98222 22222")
                    )
                )
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("call Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertTrue(result.proposals.isEmpty())
        val followUp = result.followUp
        org.junit.Assert.assertNotNull(followUp)
        assertTrue(result.message.contains("Rahul Verma"))
        assertTrue(result.message.contains("Rahul Sharma"))
        assertEquals(2, followUp!!.suggestedChoices.size)
        assertEquals(0, fakeLauncher.launchedDials.size)
        assertEquals(0, store.writes)
    }

    @Test
    fun contactNotFoundReturnsAnswer(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.NotFound
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("call John"))
        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("No contact found"))
        assertTrue(result.proposals.isEmpty())
        assertEquals(0, fakeLauncher.launchedDials.size)
    }

    @Test
    fun contactWithNoPhoneNumberReturnsAnswer(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.Resolved(
                ContactCandidate("id-1", "Rahul", phoneNumber = null)
            )
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("call Rahul"))
        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("No phone number found"))
        assertTrue(result.proposals.isEmpty())
        assertEquals(0, fakeLauncher.launchedDials.size)
    }

    @Test
    fun contactsPermissionDeniedReturnsAnswer(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.PermissionNeeded
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("call Rahul"))
        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Contacts permission is needed"))
        assertTrue(result.proposals.isEmpty())
        assertEquals(0, fakeLauncher.launchedDials.size)
    }

    @Test
    fun bareCallWithNoNameRequestsClarification(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.NotFound
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("call"))
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertTrue(result.message.contains("Who would you like to call"))
        assertTrue(result.proposals.isEmpty())
    }

    @Test
    fun noDialerHandlerReturnsFailedWithClearMessage(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher().apply {
            dialResult = ExternalLaunchResult.NoHandler("No dialer app was found to make this call.")
        }
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.Resolved(
                ContactCandidate("id-1", "Rahul", phoneNumber = "+91 98765 43210")
            )
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("call Rahul"))
        val proposal = result.proposals.single()

        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.FAILED, accepted.status)
        assertEquals("No dialer app was found to make this call.", accepted.message)
        assertTrue(accepted.completedActions.isEmpty())
        assertEquals(1, store.writes)
    }

    private fun agent(
        store: AcceptedExternalActionStore,
        contacts: ContactsResolver,
    ): ConfirmedExternalActionAgent = ConfirmedExternalActionAgent(
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
        contacts = contacts,
        zone = { zone },
        clock = Clock.fixed(now, zone)
    )

    private fun request(text: String) = AgentRequest(
        UUID.randomUUID(), text, InputSource.TEXT, now, conversationId = session
    )

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
                    Capability.DIALER,
                    Capability.MESSAGE_COMPOSER
                )
            ),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )

        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
            writes++
            val call = proposal.action as CallAction
            val launch = launcher.launchDial(call.phoneNumber)
            return when (launch) {
                is ExternalLaunchResult.Success -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.CALL,
                    ReceiptOutcome(
                        ActionStatus.HANDED_OFF,
                        now,
                        now,
                        OutcomeVerification.HANDOFF_ONLY
                    ),
                    "Opened dialer for ${call.targetLabel} (${call.phoneNumber})."
                )
                is ExternalLaunchResult.NoHandler -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.CALL,
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
                    ActionType.CALL,
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
