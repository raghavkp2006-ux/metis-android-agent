package dev.metis.agent.domain.agent

import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import java.net.URI
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
class ConfirmedExternalActionAgent(
    private val fallback: ConfirmableAgent,
    private val store: AcceptedExternalActionStore,
    private val contacts: ContactsResolver? = null,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: Clock = Clock.systemUTC(),
) : ConfirmableAgent {
    private val pending = AtomicReference<ActionProposal?>()

    override fun fork(): ConfirmableAgent? = fallback.fork()?.let {
        ConfirmedExternalActionAgent(it, store, contacts, zone, clock)
    }

    override val reminderFollowUp: ReminderClarification? get() = fallback.reminderFollowUp
    override val promiseFollowUp: PromiseClarification? get() = fallback.promiseFollowUp

    override suspend fun process(request: AgentRequest): AgentResult {
        discardProposal()
        val capturedZone = zone()
        val rules = EnglishRules(zone = { capturedZone })
        val parsed = rules.parse(request)
        return when (parsed.intent) {
            AgentIntent.CREATE_TIMER -> processTimer(request, capturedZone)
            AgentIntent.CREATE_ALARM -> processAlarm(request, capturedZone)
            AgentIntent.CALL_PERSON -> processCall(request)
            AgentIntent.SEND_MESSAGE -> processMessage(request)
            else -> fallback.process(request)
        }
    }

    private suspend fun processTimer(
        request: AgentRequest,
        capturedZone: ZoneId,
    ): AgentResult {
        val rules = EnglishRules(zone = { capturedZone })
        val contexts = object : ContextBuilder {
            override suspend fun snapshot(request: AgentRequest) = store.context(request)
        }
        val dummyReads = object : AgentReadPort {
            override suspend fun tasks(): String = ""
            override suspend fun memories(query: String, at: Long): String = ""
            override suspend fun promises(person: String): SpecialistResult =
                SpecialistResult.Unsupported("Unavailable")
        }
        val specialist = LanguageSpecialist(dummyReads)
        val result = LocalAgentOrchestrator(
            rules, rules, contexts, specialist, ProposalPolicy(),
            "Review this timer request. Nothing is started until you accept."
        ).process(request)
        currentCoroutineContext().ensureActive()
        pending.set(result.proposals.singleOrNull())
        return result
    }

    private suspend fun processAlarm(
        request: AgentRequest,
        capturedZone: ZoneId,
    ): AgentResult {
        val rules = EnglishRules(zone = { capturedZone })
        val contexts = object : ContextBuilder {
            override suspend fun snapshot(request: AgentRequest) = store.context(request)
        }
        val dummyReads = object : AgentReadPort {
            override suspend fun tasks(): String = ""
            override suspend fun memories(query: String, at: Long): String = ""
            override suspend fun promises(person: String): SpecialistResult =
                SpecialistResult.Unsupported("Unavailable")
        }
        val specialist = LanguageSpecialist(dummyReads)
        val result = LocalAgentOrchestrator(
            rules, rules, contexts, specialist, ProposalPolicy(),
            "Review this alarm request. Nothing is set until you accept."
        ).process(request)
        currentCoroutineContext().ensureActive()
        pending.set(result.proposals.singleOrNull())
        return result
    }

    @Suppress("ReturnCount")
    private suspend fun processCall(request: AgentRequest): AgentResult {
        val rules = EnglishRules(zone = zone)
        val parsed = rules.parse(request)
        val personEntity = parsed.entities.firstOrNull { it.type == EntityKind.PERSON }
        val personName = personEntity?.rawValue
        if (personName.isNullOrBlank()) {
            val followUp = FollowUpQuestion(
                UUID.randomUUID(), request.id, "Who would you like to call?", setOf("person")
            )
            return AgentResult(
                request.id, AgentResultStatus.FOLLOW_UP,
                "Who would you like to call?",
                followUp = followUp
            )
        }
        val resolver = contacts ?: return AgentResult(request.id, AgentResultStatus.ANSWER,
            "No contact found for \"$personName\".")
        return when (val resolution = resolver.resolve(personName)) {
            is ContactResolution.NotFound -> AgentResult(request.id, AgentResultStatus.ANSWER,
                "No contact found for \"$personName\".")
            is ContactResolution.PermissionNeeded -> AgentResult(request.id, AgentResultStatus.ANSWER,
                "Contacts permission is needed to find this contact.")
            is ContactResolution.Ambiguous -> ambiguousClarification(request, personName, resolution.candidates)
            is ContactResolution.Resolved -> proposeCall(request, resolution.contact)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun processMessage(request: AgentRequest): AgentResult {
        val rules = EnglishRules(zone = zone)
        val parsed = rules.parse(request)
        val personEntity = parsed.entities.firstOrNull { it.type == EntityKind.PERSON }
        val titleEntity = parsed.entities.firstOrNull { it.type == EntityKind.TITLE }
        val recipient = personEntity?.rawValue
        val body = titleEntity?.rawValue
        if (recipient.isNullOrBlank() || body.isNullOrBlank()) {
            val missing = buildSet {
                if (recipient.isNullOrBlank()) add("recipient")
                if (body.isNullOrBlank()) add("body")
            }
            val followUp = FollowUpQuestion(
                UUID.randomUUID(), request.id, "Specify a recipient and message body.", missing
            )
            return AgentResult(
                request.id, AgentResultStatus.FOLLOW_UP,
                "Specify a recipient and message body.",
                followUp = followUp
            )
        }
        val resolver = contacts ?: return AgentResult(request.id, AgentResultStatus.ANSWER,
            "No contact found for \"$recipient\".")
        return when (val resolution = resolver.resolve(recipient)) {
            is ContactResolution.NotFound -> AgentResult(request.id, AgentResultStatus.ANSWER,
                "No contact found for \"$recipient\".")
            is ContactResolution.PermissionNeeded -> AgentResult(request.id, AgentResultStatus.ANSWER,
                "Contacts permission is needed to find this contact.")
            is ContactResolution.Ambiguous -> ambiguousClarification(request, recipient, resolution.candidates)
            is ContactResolution.Resolved -> proposeMessage(request, resolution.contact, body)
        }
    }

    private fun ambiguousClarification(
        request: AgentRequest,
        name: String,
        candidates: List<ContactCandidate>,
    ): AgentResult {
        val names = candidates.joinToString(" or ") { it.displayName }
        val question = "Multiple contacts found for \"$name\": $names. Which one did you mean?"
        val choices = candidates.map { it.displayName }
        return AgentResult(
            request.id,
            AgentResultStatus.FOLLOW_UP,
            question,
            followUp = FollowUpQuestion(
                UUID.randomUUID(), request.id, question, setOf("person"), suggestedChoices = choices
            )
        )
    }

    private fun proposeCall(request: AgentRequest, contact: ContactCandidate): AgentResult {
        val number = contact.phoneNumber
        if (number.isNullOrBlank()) {
            return AgentResult(request.id, AgentResultStatus.ANSWER,
                "No phone number found for ${contact.displayName}.")
        }
        val identity = ActionIdentity(UUID.randomUUID(), request.id, UUID.randomUUID())
        val action = CallAction(identity, contact.displayName, number)
        val now = clock.instant()
        val proposal = ActionProposal(
            UUID.randomUUID(),
            action,
            ActionRequirements.risk(action),
            "Call ${contact.displayName} at $number.",
            emptyList(),
            true,
            now,
            now.plusSeconds(PROPOSAL_VALIDITY_SECONDS)
        )
        pending.set(proposal)
        return AgentResult(
            request.id,
            AgentResultStatus.PROPOSAL,
            "Review this call request. Nothing is dialed until you accept.",
            proposals = listOf(proposal)
        )
    }

    private fun proposeMessage(
        request: AgentRequest,
        contact: ContactCandidate,
        body: String,
    ): AgentResult {
        val number = contact.phoneNumber
        if (number.isNullOrBlank()) {
            return AgentResult(request.id, AgentResultStatus.ANSWER,
                "No phone number found for ${contact.displayName}.")
        }
        val identity = ActionIdentity(UUID.randomUUID(), request.id, UUID.randomUUID())
        val encoded = number.replace(" ", "%20")
        val destination = URI("smsto:$encoded")
        val action = MessageAction(identity, contact.displayName, destination, body)
        val now = clock.instant()
        val proposal = ActionProposal(
            UUID.randomUUID(),
            action,
            ActionRequirements.risk(action),
            "Send message to ${contact.displayName} ($number): $body",
            emptyList(),
            true,
            now,
            now.plusSeconds(PROPOSAL_VALIDITY_SECONDS)
        )
        pending.set(proposal)
        return AgentResult(
            request.id,
            AgentResultStatus.PROPOSAL,
            "Review this message request. Nothing is sent until you accept.",
            proposals = listOf(proposal)
        )
    }

    @Suppress("ReturnCount")
    override suspend fun accept(proposal: ActionProposal): AgentResult {
        if (proposal.action.type !in EXTERNAL_ACTION_TYPES) return fallback.accept(proposal)
        if (!pending.compareAndSet(proposal, null)) return AgentResult(
            proposal.requestId,
            AgentResultStatus.DENIED,
            "This proposal is no longer active. Submit a fresh request."
        )
        return try {
            val receipt = withContext(NonCancellable) { store.accept(proposal, UUID.randomUUID()) }
            val success = receipt.outcome.status in setOf(ActionStatus.SUCCEEDED, ActionStatus.HANDED_OFF)
            AgentResult(
                requestId = receipt.requestId,
                status = if (success) AgentResultStatus.ANSWER else AgentResultStatus.FAILED,
                message = receipt.reason,
                completedActions = if (success) listOf(receipt) else emptyList()
            )
        } catch (rejected: ActionRejectedException) {
            AgentResult(proposal.requestId, AgentResultStatus.DENIED, rejected.userMessage)
        }
    }

    override fun discardProposal() {
        pending.set(null)
        fallback.discardProposal()
    }

    override suspend fun undo(receipt: ActionReceipt): UndoResult =
        if (receipt.actionType in EXTERNAL_ACTION_TYPES) UndoResult.NOT_SUPPORTED
        else fallback.undo(receipt)

    private companion object {
        private const val PROPOSAL_VALIDITY_SECONDS = 300L

        val EXTERNAL_ACTION_TYPES = setOf(
            ActionType.TIMER,
            ActionType.ALARM,
            ActionType.CALL,
            ActionType.MESSAGE,
        )
    }
}


