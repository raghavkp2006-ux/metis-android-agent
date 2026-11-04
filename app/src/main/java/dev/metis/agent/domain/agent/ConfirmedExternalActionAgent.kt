@file:Suppress("MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "NestedBlockDepth", "ReturnCount", "CyclomaticComplexMethod")

package dev.metis.agent.domain.agent

import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import dev.metis.agent.domain.contacts.MAX_CANDIDATES
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
    private val pendingClarification = AtomicReference<PendingClarificationState?>()

    override fun fork(): ConfirmableAgent? = fallback.fork()?.let {
        ConfirmedExternalActionAgent(it, store, contacts, zone, clock)
    }

    override val reminderFollowUp: ReminderClarification? get() = fallback.reminderFollowUp
    override val promiseFollowUp: PromiseClarification? get() = fallback.promiseFollowUp

    fun clearClarification() {
        pendingClarification.set(null)
    }

    val activeClarification: PendingClarificationState? get() = pendingClarification.get()

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override suspend fun process(request: AgentRequest): AgentResult {
        discardProposal()
        val capturedZone = zone()
        val rules = EnglishRules(zone = { capturedZone })

        val active = pendingClarification.get()
        if (active != null) {
            val handled = handleActiveClarification(request, active, capturedZone, rules)
            if (handled != null) return handled
        }

        val prediction = rules.classify(request)
        return when (prediction.intent) {
            AgentIntent.CREATE_TIMER -> processTimer(request, capturedZone)
            AgentIntent.CREATE_ALARM -> processAlarm(request, capturedZone)
            AgentIntent.CALL_PERSON -> processCall(request, prediction)
            AgentIntent.SEND_MESSAGE -> processMessage(request, prediction)
            else -> fallback.process(request)
        }
    }

    private suspend fun handleActiveClarification(
        request: AgentRequest,
        active: PendingClarificationState,
        capturedZone: ZoneId,
        rules: EnglishRules,
    ): AgentResult? {
        val now = clock.instant()
        if (active.isExpired(now, request.conversationId) || request.timestamp >= active.expiresAt) {
            pendingClarification.set(null)
            return null
        }
        if (isClarificationCancel(request.text)) {
            pendingClarification.set(null)
            return AgentResult(request.id, AgentResultStatus.DENIED, "Clarification cancelled.")
        }
        val resolved = resolveClarification(request, active, capturedZone)
        if (resolved != null) return resolved

        if (rules.classify(request).intent != AgentIntent.UNKNOWN) {
            pendingClarification.set(null)
            return null
        }
        if (active.turnsRemaining > 1) {
            val next = active.copy(turnsRemaining = active.turnsRemaining - 1)
            pendingClarification.set(next)
            return AgentResult(
                request.id,
                AgentResultStatus.FOLLOW_UP,
                active.prompt,
                followUp = FollowUpQuestion(UUID.randomUUID(), request.id, active.prompt, setOf(active.missingSlot))
            )
        }
        pendingClarification.set(null)
        return AgentResult(request.id, AgentResultStatus.DENIED, "Clarification cancelled.")
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
        val bare = rules.findBareAlarm(request)
        if (bare != null) {
            val prompt = "AM or PM?"
            val followUp = FollowUpQuestion(UUID.randomUUID(), request.id, prompt, setOf("am_pm"))
            val state = PendingClarificationState(
                originalIntent = AgentIntent.CREATE_ALARM,
                filledSlots = mapOf(
                    "hour" to bare.hour.toString(),
                    "minute" to bare.minute.toString(),
                    "day" to (bare.dayString ?: ""),
                    "label" to (bare.label ?: "Alarm"),
                ),
                missingSlot = "am_pm",
                createdAt = request.timestamp,
                expiresAt = clock.instant().plusSeconds(CLARIFICATION_EXPIRY_SECONDS),
                turnsRemaining = 2,
                originalRequestId = request.id,
                sessionId = request.conversationId,
                prompt = prompt,
            )
            pendingClarification.set(state)
            return AgentResult(request.id, AgentResultStatus.FOLLOW_UP, prompt, followUp = followUp)
        }
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
    private suspend fun processCall(
        request: AgentRequest,
        prediction: IntentPrediction = IntentPrediction(AgentIntent.CALL_PERSON, 1.0)
    ): AgentResult {
        val rules = EnglishRules(zone = zone)
        val entities = rules.extract(request)
        val personEntity = entities.firstOrNull { it.type == EntityKind.PERSON }
        val personName = personEntity?.rawValue
        val isPronoun = personName?.lowercase() in setOf("him", "her", "them", "that one")
        if (personName.isNullOrBlank() || isPronoun) {
            val prompt = if (isPronoun) "Who do you mean?" else "Who would you like to call?"
            val followUp = FollowUpQuestion(
                UUID.randomUUID(), request.id, prompt, setOf("person")
            )
            val state = PendingClarificationState(
                originalIntent = AgentIntent.CALL_PERSON,
                filledSlots = emptyMap(),
                missingSlot = "person",
                createdAt = request.timestamp,
                expiresAt = clock.instant().plusSeconds(CLARIFICATION_EXPIRY_SECONDS),
                turnsRemaining = 2,
                originalRequestId = request.id,
                sessionId = request.conversationId,
                prompt = prompt,
            )
            pendingClarification.set(state)
            return AgentResult(
                request.id, AgentResultStatus.FOLLOW_UP,
                prompt,
                followUp = followUp
            )
        }
        val resolver = contacts ?: return AgentResult(request.id, AgentResultStatus.ANSWER,
            "No contact found for \"$personName\".")
        return when (val resolution = resolver.resolvePerson(personName)) {
            is ContactResolution.NotFound -> AgentResult(request.id, AgentResultStatus.ANSWER,
                "No contact found for \"$personName\".")
            is ContactResolution.PermissionNeeded -> AgentResult(request.id, AgentResultStatus.ANSWER,
                "Contacts permission is needed to find this contact.")
            is ContactResolution.Ambiguous -> ambiguousClarification(request, personName, resolution.candidates, resolution.requiresRefinement)
            is ContactResolution.Resolved -> proposeCall(request, resolution.contact, prediction)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun processMessage(
        request: AgentRequest,
        prediction: IntentPrediction = IntentPrediction(AgentIntent.SEND_MESSAGE, 1.0)
    ): AgentResult {
        val rules = EnglishRules(zone = zone)
        val entities = rules.extract(request)
        val personEntity = entities.firstOrNull { it.type == EntityKind.PERSON }
        val titleEntity = entities.firstOrNull { it.type == EntityKind.TITLE }
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
        return when (val resolution = resolver.resolvePerson(recipient)) {
            is ContactResolution.NotFound -> AgentResult(request.id, AgentResultStatus.ANSWER,
                "No contact found for \"$recipient\".")
            is ContactResolution.PermissionNeeded -> AgentResult(request.id, AgentResultStatus.ANSWER,
                "Contacts permission is needed to find this contact.")
            is ContactResolution.Ambiguous -> ambiguousClarification(request, recipient, resolution.candidates, resolution.requiresRefinement)
            is ContactResolution.Resolved -> proposeMessage(request, resolution.contact, body, prediction)
        }
    }

    private fun ambiguousClarification(
        request: AgentRequest,
        name: String,
        candidates: List<ContactCandidate>,
        requiresRefinement: Boolean = false,
    ): AgentResult {
        val top = candidates.take(MAX_CANDIDATES)
        val names = top.joinToString(" or ") { it.displayName }
        val refinementHint = if (requiresRefinement || candidates.size > MAX_CANDIDATES) " (type a fuller name)" else ""
        val question = if (names.isNotBlank()) {
            "Multiple contacts found for \"$name\": $names$refinementHint. Which one did you mean?"
        } else {
            "Multiple contacts found for \"$name\": (type a fuller name). Which one did you mean?"
        }
        val choices = top.map { it.displayName }
        return AgentResult(
            request.id,
            AgentResultStatus.FOLLOW_UP,
            question,
            followUp = FollowUpQuestion(
                UUID.randomUUID(), request.id, question, setOf("person"), suggestedChoices = choices
            )
        )
    }

    private fun proposeCall(
        request: AgentRequest,
        contact: ContactCandidate,
        prediction: IntentPrediction? = null,
    ): AgentResult {
        val number = contact.phoneNumber
        if (number.isNullOrBlank()) {
            return AgentResult(request.id, AgentResultStatus.ANSWER,
                "No phone number found for ${contact.displayName}.")
        }
        val identity = ActionIdentity(UUID.randomUUID(), request.id, UUID.randomUUID())
        val action = CallAction(identity, contact.displayName, number)
        val now = clock.instant()
        val lmSuffix = if (prediction?.isUnderstoodByLm == true) {
            " Understood by language model (${(prediction.confidence * 100).toInt()}%)."
        } else ""
        val proposal = ActionProposal(
            UUID.randomUUID(),
            action,
            ActionRequirements.risk(action),
            "Call ${contact.displayName} at $number.$lmSuffix",
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
        prediction: IntentPrediction? = null,
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
        val lmSuffix = if (prediction?.isUnderstoodByLm == true) {
            " Understood by language model (${(prediction.confidence * 100).toInt()}%)."
        } else ""
        val proposal = ActionProposal(
            UUID.randomUUID(),
            action,
            ActionRequirements.risk(action),
            "Send message to ${contact.displayName} ($number): $body.$lmSuffix",
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

    private suspend fun resolveClarification(
        request: AgentRequest,
        active: PendingClarificationState,
        capturedZone: ZoneId,
    ): AgentResult? = when (active.missingSlot) {
        "am_pm" -> resolveAlarmAmPm(request, active, capturedZone)
        "person" -> resolveCallPerson(request)
        else -> null
    }

    private fun resolveAlarmAmPm(
        request: AgentRequest,
        active: PendingClarificationState,
        capturedZone: ZoneId,
    ): AgentResult? {
        val amPm = parseAmPm(request.text) ?: return null
        val hour = active.filledSlots["hour"]?.toIntOrNull() ?: return null
        val minute = active.filledSlots["minute"]?.toIntOrNull() ?: 0
        val day = active.filledSlots["day"]?.takeIf { it.isNotBlank() }
        val label = active.filledSlots["label"]?.takeIf { it.isNotBlank() } ?: "Alarm"

        val resolvedHour = (hour % 12) + if (amPm == "pm") 12 else 0
        val localTime = java.time.LocalTime.of(resolvedHour, minute)

        val localDate = when {
            day.equals("tomorrow", true) || day.equals("kal", true) ->
                request.timestamp.atZone(capturedZone).toLocalDate().plusDays(1)
            day.equals("today", true) || day.equals("aaj", true) ->
                request.timestamp.atZone(capturedZone).toLocalDate()
            else -> {
                val today = request.timestamp.atZone(capturedZone).toLocalDate()
                val candidateLocal = java.time.LocalDateTime.of(today, localTime)
                val offsets = capturedZone.rules.getValidOffsets(candidateLocal)
                if (offsets.size == 1 && candidateLocal.toInstant(offsets.single()) > request.timestamp) {
                    today
                } else {
                    today.plusDays(1)
                }
            }
        }
        val local = java.time.LocalDateTime.of(localDate, localTime)
        val offsets = capturedZone.rules.getValidOffsets(local)
        if (offsets.size != 1) return null
        val instant = local.toInstant(offsets.single())
        val resolvedTime = ResolvedTime(instant, local, capturedZone)

        val identity = ActionIdentity(UUID.randomUUID(), request.id, UUID.randomUUID())
        val action = CreateAlarmAction(identity, resolvedTime, label)
        val now = clock.instant()
        val proposal = ActionProposal(
            UUID.randomUUID(),
            action,
            ActionRequirements.risk(action),
            "Set alarm for ${local.toLocalTime()}.",
            emptyList(),
            true,
            now,
            now.plusSeconds(PROPOSAL_VALIDITY_SECONDS)
        )
        pending.set(proposal)
        pendingClarification.set(null)
        return AgentResult(
            request.id,
            AgentResultStatus.PROPOSAL,
            "Review this alarm request. Nothing is set until you accept.",
            proposals = listOf(proposal)
        )
    }

    private fun isInvalidContactName(name: String): Boolean {
        if (name.isBlank()) return true
        if (name.lowercase(java.util.Locale.ROOT) in PRONOUNS) return true
        return name.length > 100 || name.any(Char::isISOControl)
    }

    private suspend fun resolveCallPerson(
        request: AgentRequest,
    ): AgentResult? {
        val cleanName = request.text.trim().replace(Regex("(?i)^(?:call|dial|phone)\\s+"), "")
        if (isInvalidContactName(cleanName)) {
            return null
        }
        val resolver = contacts ?: run {
            pendingClarification.set(null)
            return AgentResult(request.id, AgentResultStatus.ANSWER, "No contact found for \"$cleanName\".")
        }
        pendingClarification.set(null)
        return when (val resolution = resolver.resolvePerson(cleanName)) {
            is ContactResolution.NotFound -> AgentResult(
                request.id, AgentResultStatus.ANSWER, "No contact found for \"$cleanName\"."
            )
            is ContactResolution.PermissionNeeded -> AgentResult(
                request.id, AgentResultStatus.ANSWER, "Contacts permission is needed to find this contact."
            )
            is ContactResolution.Ambiguous -> ambiguousClarification(
                request, cleanName, resolution.candidates, resolution.requiresRefinement
            )
            is ContactResolution.Resolved -> proposeCall(request, resolution.contact)
        }
    }

    private fun isClarificationCancel(text: String): Boolean {
        val lower = text.trim().lowercase(java.util.Locale.ROOT)
        return lower in setOf("cancel", "never mind", "nevermind", "forget it", "forget that", "cancel that", "ignore", "don't remind me", "dont remind me")
    }

    private fun parseAmPm(text: String): String? {
        val clean = text.trim().lowercase(java.util.Locale.ROOT)
        val amWords = setOf("am", "a.m.", "subah", "morning", "subah ka", "subah ki")
        val pmWords = setOf("pm", "p.m.", "shaam", "evening", "raat", "night", "dopahar", "afternoon", "shaam ka", "shaam ki", "raat ka")
        return when {
            amWords.any { clean == it || clean.startsWith("$it ") || clean.endsWith(" $it") } -> "am"
            pmWords.any { clean == it || clean.startsWith("$it ") || clean.endsWith(" $it") } -> "pm"
            clean.contains(Regex("\\b(?:am|subah|morning)\\b")) -> "am"
            clean.contains(Regex("\\b(?:pm|shaam|raat|evening|night|dopahar|afternoon)\\b")) -> "pm"
            else -> null
        }
    }

    private companion object {
        private const val CLARIFICATION_EXPIRY_SECONDS = 120L
        private const val PROPOSAL_VALIDITY_SECONDS = 300L
        private val PRONOUNS = setOf("him", "her", "them", "that one")

        val EXTERNAL_ACTION_TYPES = setOf(
            ActionType.TIMER,
            ActionType.ALARM,
            ActionType.CALL,
            ActionType.MESSAGE,
        )
    }
}
