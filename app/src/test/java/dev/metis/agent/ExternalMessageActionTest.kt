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
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.ConfirmedExternalActionAgent
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.MessageAction
import dev.metis.agent.domain.agent.MessageMode
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalMessageActionTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val session = UUID.randomUUID()

    @Test
    fun grammarExtractsRecipientAndBody() {
        val rules = EnglishRules { zone }

        // "message Mom: running late"
        val r1 = rules.parse(request("message Mom: running late"))
        assertEquals(AgentIntent.SEND_MESSAGE, r1.intent)
        assertEquals("Mom", r1.entities.single { it.type == EntityKind.PERSON }.rawValue)
        assertEquals("running late", r1.entities.single { it.type == EntityKind.TITLE }.rawValue)

        // "text Rahul that I'll be there at 8"
        val r2 = rules.parse(request("text Rahul that I'll be there at 8"))
        assertEquals(AgentIntent.SEND_MESSAGE, r2.intent)
        assertEquals("Rahul", r2.entities.single { it.type == EntityKind.PERSON }.rawValue)
        assertEquals("I'll be there at 8", r2.entities.single { it.type == EntityKind.TITLE }.rawValue)

        // "send a message to Mom: on my way"
        val r3 = rules.parse(request("send a message to Mom: on my way"))
        assertEquals(AgentIntent.SEND_MESSAGE, r3.intent)
        assertEquals("Mom", r3.entities.single { it.type == EntityKind.PERSON }.rawValue)
        assertEquals("on my way", r3.entities.single { it.type == EntityKind.TITLE }.rawValue)

        // "text Rahul saying will be late by 15 mins"
        val r4 = rules.parse(request("text Rahul saying will be late by 15 mins"))
        assertEquals(AgentIntent.SEND_MESSAGE, r4.intent)
        assertEquals("Rahul", r4.entities.single { it.type == EntityKind.PERSON }.rawValue)
        assertEquals("will be late by 15 mins", r4.entities.single { it.type == EntityKind.TITLE }.rawValue)
    }

    @Test
    fun resolvedContactProposesMessageShowingRecipientAndBodyAndLaunchesOnlyAfterAccept(): Unit = runBlocking {
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

        val result = agent.process(request("text Rahul that I'll be there at 8"))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        assertTrue(proposal.action is MessageAction)
        val message = proposal.action as MessageAction
        assertEquals("Rahul Sharma", message.recipient)
        assertEquals(URI("smsto:+91%2098765%2043210"), message.destination)
        assertEquals("+91 98765 43210", message.destination.schemeSpecificPart)
        assertEquals("I'll be there at 8", message.body)
        assertEquals(MessageMode.COMPOSER, message.mode)
        assertTrue(proposal.reason.contains("I'll be there at 8"))
        assertTrue(proposal.reason.contains("+91 98765 43210"))
        assertFalse(message.toString().contains("Rahul Sharma"))
        assertFalse(message.toString().contains("+91 98765 43210"))
        assertFalse(message.toString().contains("I'll be there at 8"))
        assertTrue(message.toString().contains("[REDACTED]"))

        // Messaging composer NOT launched before accept
        assertEquals(0, fakeLauncher.launchedMessages.size)
        assertEquals(0, store.writes)

        // Explicit accept
        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, fakeLauncher.launchedMessages.size)
        val launched = fakeLauncher.launchedMessages.single()
        assertEquals("+91 98765 43210", launched.phoneNumber)
        assertEquals("I'll be there at 8", launched.body)
        assertEquals(1, accepted.completedActions.size)
        val receipt = accepted.completedActions.single()
        assertEquals(ActionType.MESSAGE, receipt.actionType)
        assertEquals(ActionStatus.HANDED_OFF, receipt.outcome.status)
        assertEquals(OutcomeVerification.HANDOFF_ONLY, receipt.outcome.verification)

        // Second accept is denied
        val duplicate = agent.accept(proposal)
        assertEquals(AgentResultStatus.DENIED, duplicate.status)
        assertEquals(1, fakeLauncher.launchedMessages.size)
    }

    @Test
    fun ambiguousRecipientRequestsClarification(): Unit = runBlocking {
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

        val result = agent.process(request("message Rahul: running late"))
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertTrue(result.proposals.isEmpty())
        val followUp = result.followUp
        assertNotNull(followUp)
        assertTrue(result.message.contains("Rahul Verma"))
        assertTrue(result.message.contains("Rahul Sharma"))
        assertEquals(2, followUp!!.suggestedChoices.size)
        assertEquals(0, fakeLauncher.launchedMessages.size)
        assertEquals(0, store.writes)
    }

    @Test
    fun recipientNotFoundReturnsAnswer(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.NotFound
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("message Mom: running late"))
        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("No contact found"))
        assertTrue(result.proposals.isEmpty())
        assertEquals(0, fakeLauncher.launchedMessages.size)
    }

    @Test
    fun noMessageComposerHandlerReturnsFailedWithClearMessage(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher().apply {
            sendToResult = ExternalLaunchResult.NoHandler("No messaging app was found to compose this message.")
        }
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.Resolved(
                ContactCandidate("id-1", "Mom", phoneNumber = "+1 555 010 4242")
            )
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("message Mom: running late"))
        val proposal = result.proposals.single()

        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.FAILED, accepted.status)
        assertEquals("No messaging app was found to compose this message.", accepted.message)
        assertTrue(accepted.completedActions.isEmpty())
        assertEquals(1, store.writes)
    }

    @Test
    fun contactWithNoPhoneNumberReturnsAnswer(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.Resolved(
                ContactCandidate("id-1", "Mom", phoneNumber = null)
            )
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("message Mom: running late"))
        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("No phone number found"))
        assertTrue(result.proposals.isEmpty())
        assertEquals(0, fakeLauncher.launchedMessages.size)
    }

    @Test
    fun contactsPermissionDeniedReturnsAnswer(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.PermissionNeeded
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("message Mom: running late"))
        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertTrue(result.message.contains("Contacts permission is needed"))
        assertTrue(result.proposals.isEmpty())
        assertEquals(0, fakeLauncher.launchedMessages.size)
    }

    @Test
    fun bareMessageWithNoRecipientOrBodyRequestsClarification(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.NotFound
        }
        val agent = agent(store, contacts)

        val result = agent.process(request("message"))
        assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
        assertTrue(result.message.contains("Specify a recipient and message body"))
        assertTrue(result.proposals.isEmpty())
    }

    @Test
    fun messageBodyWithQuotesNewlinesEmojiAndHinglish(): Unit = runBlocking {
        val fakeLauncher = FakeExternalActionLauncher()
        val store = FakeExternalStore(fakeLauncher)
        val contacts = object : ContactsResolver {
            override suspend fun resolve(query: String) = ContactResolution.Resolved(
                ContactCandidate("id-1", "Rahul", phoneNumber = "+91 98765 43210")
            )
        }
        val agent = agent(store, contacts)

        val text = "text Rahul that Bhai, \"5 mins\" mein aa raha hoon! \uD83C\uDFC3\u200D♂️\nTraffic bohot hai"
        val result = agent.process(request(text))
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        val message = proposal.action as MessageAction
        assertEquals("Bhai, \"5 mins\" mein aa raha hoon! \uD83C\uDFC3\u200D♂️\nTraffic bohot hai", message.body)

        val accepted = agent.accept(proposal)
        assertEquals(AgentResultStatus.ANSWER, accepted.status)
        assertEquals(1, fakeLauncher.launchedMessages.size)
        assertEquals(message.body, fakeLauncher.launchedMessages.single().body)
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
            val message = proposal.action as MessageAction
            val launch = launcher.launchSendTo(message.destination.schemeSpecificPart, message.body)
            return when (launch) {
                is ExternalLaunchResult.Success -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.MESSAGE,
                    ReceiptOutcome(
                        ActionStatus.HANDED_OFF,
                        now,
                        now,
                        OutcomeVerification.HANDOFF_ONLY
                    ),
                    "Opened message composer for ${message.recipient}."
                )
                is ExternalLaunchResult.NoHandler -> ActionReceipt(
                    UUID.randomUUID(),
                    proposal.action.identity,
                    ActionType.MESSAGE,
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
                    ActionType.MESSAGE,
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

