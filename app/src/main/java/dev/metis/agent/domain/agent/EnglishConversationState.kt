package dev.metis.agent.domain.agent

import java.time.Clock
import java.util.UUID
import java.util.Locale

/** A bounded extension of the existing in-memory pending/clarification state; no storage or executor. */
@Suppress("TooManyFunctions") // Each UI event has a separate guarded transition.
internal class EnglishConversationState(private val root: ConfirmableAgent?, private val clock: Clock) {
    private val history = mutableListOf<ReferenceTurn>()
    private var observedRequestId: UUID? = null
    private var parts = emptyList<Part>()
    val active get() = parts.isNotEmpty()
    val results get() = parts.map { it.result }

    fun handles(request: AgentRequest) = active || EnglishConversationRules.split(request).let {
        it.tooMany || it.requests.size > 1
    } || EnglishConversationRules.question(request, history) != null

    fun observe(request: AgentRequest) = remember(request, EnglishConversationRules.mentions(request))
    fun reference(request: AgentRequest) = EnglishConversationRules.question(request, history)

    fun observeResult(request: AgentRequest, result: AgentResult) {
        if (observedRequestId != request.id) return
        val last = history.lastOrNull() ?: return
        history[history.lastIndex] = ReferenceTurn(last.sessionId, last.at,
            EnglishConversationRules.withProposalMentions(last.mentions, result))
    }

    private fun remember(request: AgentRequest, mentions: List<ReferenceMention>) {
        val session = request.conversationId ?: return
        history.removeAll { it.sessionId != session }
        history.add(ReferenceTurn(session, request.timestamp, mentions))
        observedRequestId = request.id
        while (history.size > REFERENCE_TURN_LIMIT) history.removeAt(0)
    }

    @Suppress("ReturnCount") // Invalid splits and unavailable independent state must stop before dispatch.
    suspend fun process(request: AgentRequest): List<AgentResult> {
        val pending = parts.firstOrNull { it.result.followUp != null }
        val single = EnglishConversationRules.split(request)
        val isAnswer = pending != null && !single.tooMany && single.requests.size == 1 &&
            (pending.pending.current()?.kind == Kind.OTHER ||
                EnglishRules().parse(request).intent == AgentIntent.UNKNOWN)
        if (request.text.trim().lowercase(Locale.ROOT) in setOf("cancel", "ignore") && active) {
            remember(request, emptyList())
            return reject()
        }
        if (isAnswer) {
            answer(requireNotNull(pending), request)
            val part = requireNotNull(pending)
            remember(request, if (part.result.status in setOf(AgentResultStatus.DENIED, AgentResultStatus.FAILED))
                emptyList() else EnglishConversationRules.withProposalMentions(
                    EnglishConversationRules.mentions(part.request), part.result))
            return results
        }
        dropPending()
        if (single.tooMany) return unparsed(request,
            question(request, "Please split this into at most two requests.", "intents"))
        if (single.requests.size > 1 && EnglishRules().parse(request).negated) return unparsed(request,
            AgentResult(request.id, AgentResultStatus.DENIED, "No actions were proposed for this negated request."))
        val agents = single.requests.map { root?.fork() }
        if (agents.any { it == null }) return unparsed(request, question(request,
            "Please send these requests separately.", "intent"))
        parts = single.requests.mapIndexed { index, item ->
            Part(item.copy(id = UUID.randomUUID()), requireNotNull(agents[index]), clock)
        }
        parts.forEach { prepare(it) }
        remember(request, parts.flatMap {
            EnglishConversationRules.withProposalMentions(EnglishConversationRules.mentions(it.request), it.result)
        })
        return results
    }

    private suspend fun prepare(part: Part) {
        val reference = EnglishConversationRules.question(part.request, history)
        if (reference != null && reference.choices.size != 1) {
            part.reference = reference
            part.result = question(part.request, reference.question,
                if (reference.person) "reference_person" else "reference_item", reference.choices)
            register(part, Kind.REFERENCE)
        } else {
            if (reference != null) {
                part.request = requireNotNull(EnglishConversationRules.resolve(reference, "1"))
                prepare(part)
                return
            }
            val intent = EnglishRules().parse(part.request).intent
            if (parts.size > 1 && intent !in WRITE_INTENTS) {
                part.result = question(part.request,
                    "Please give one task, reminder, or promise request for this part.", "intent")
                register(part, Kind.OTHER)
                return
            }
            part.result = part.agent.process(part.request)
            track(part)
        }
    }

    private fun track(part: Part) {
        val field = part.result.followUp?.missingFields?.singleOrNull()
        val kind = when {
            field == "time" && part.agent.reminderFollowUp != null -> Kind.TIME
            field?.startsWith("reminder_contact_") == true -> Kind.REMINDER_PERSON
            EnglishRules().parse(part.request).intent == AgentIntent.CREATE_PROMISE -> Kind.PROMISE
            else -> Kind.OTHER
        }
        if (part.result.followUp == null) part.pending.clear() else register(part, kind)
    }

    private fun register(part: Part, kind: Kind) {
        val followUp = requireNotNull(part.result.followUp)
        val field = followUp.missingFields.singleOrNull().orEmpty()
        val current = part.pending.current()
        if (current?.kind == kind && current.payload == field) {
            part.pending.replace(current, current.copy(questionId = followUp.id, lastAnswerId = part.request.id))
        } else part.pending.begin(PendingClarification(kind, part.request.id,
            followUp.id, requireNotNull(part.request.conversationId),
            part.request.timestamp, part.request.timestamp.plusSeconds(PROPOSAL_LIFETIME_SECONDS), field))
    }

    @Suppress("ReturnCount") // Expired/invalid choices never reach an action agent.
    private suspend fun answer(part: Part, answer: AgentRequest) {
        val current = part.pending.current() ?: return
        if (part.pending.answer(current.kind, current.originalRequestId, current.questionId, answer) == null) {
            part.pending.clear()
            part.result = AgentResult(answer.id, AgentResultStatus.DENIED,
                "This clarification has expired. Submit a fresh request.")
            return
        }
        if (current.kind == Kind.REFERENCE) {
            answerReference(part, answer)
            return
        }
        if (current.kind in setOf(Kind.PROMISE, Kind.REMINDER_PERSON) &&
            EnglishConversationRules.isBareReference(answer.text)) {
            answerReferenceSlot(part, current, answer)
            return
        }
        part.result = when (current.kind) {
            Kind.TIME -> requireNotNull(part.agent.reminderFollowUp).answerReminder(
                current.originalRequestId, current.questionId, answer)
            Kind.REMINDER_PERSON -> answerPerson(part, current, answer)
            Kind.PROMISE -> requireNotNull(part.agent.promiseFollowUp).answerPromise(
                current.originalRequestId, current.questionId, answer)
            else -> {
                part.request = answer
                prepare(part)
                return
            }
        }
        part.request = part.request.copy(id = answer.id, timestamp = answer.timestamp)
        track(part)
    }

    @Suppress("ReturnCount") // Expired choices cannot resume a prior action clarification.
    private suspend fun answerReference(part: Part, answer: AgentRequest) {
        val reference = part.reference ?: return
        val refreshed = EnglishConversationRules.question(reference.request.copy(timestamp = answer.timestamp), history)
        if (reference.choices.isNotEmpty() && refreshed?.choices != reference.choices) {
            part.result = AgentResult(answer.id, AgentResultStatus.DENIED,
                "The reference has expired. Submit a fresh request.")
            part.pending.clear()
            return
        }
        val resolved = EnglishConversationRules.resolve(reference, answer.text) ?: return
        if (EnglishConversationRules.split(resolved).requests.size != 1) return
        part.reference = null
        val continuation = part.referenceContinuation
        part.referenceContinuation = null
        if (continuation != null) {
            part.pending.begin(continuation)
            answer(part, answer.copy(text = resolved.text))
        } else {
            part.request = resolved.copy(id = answer.id, timestamp = answer.timestamp)
            prepare(part)
        }
    }

    private suspend fun answerReferenceSlot(part: Part, current: PendingClarification<Kind, String>,
        answer: AgentRequest) {
        val reference = requireNotNull(reference(answer))
        if (reference.choices.size == 1) {
            val resolved = requireNotNull(EnglishConversationRules.resolve(reference, "1"))
            answer(part, answer.copy(text = resolved.text))
        } else {
            part.reference = reference
            part.referenceContinuation = current
            part.result = question(answer, reference.question,
                if (reference.person) "reference_person" else "reference_item", reference.choices)
            register(part, Kind.REFERENCE)
        }
    }

    private suspend fun answerPerson(part: Part, current: PendingClarification<Kind, String>,
        answer: AgentRequest): AgentResult {
        val agent = requireNotNull(part.agent.reminderFollowUp)
        return if (part.result.followUp?.missingFields == setOf("reminder_contact_permission") &&
            answer.text.trim().lowercase(Locale.ROOT) in setOf("skip", "no", "continue"))
            agent.answerReminderContactPermission(current.originalRequestId, current.questionId, answer, false)
        else agent.answerReminderPerson(current.originalRequestId, current.questionId, answer)
    }

    fun hasPermissionQuestion(questionId: UUID) = parts.any {
        it.result.followUp?.id == questionId && it.result.followUp?.missingFields?.singleOrNull() in
            setOf("contact_permission", "reminder_contact_permission") &&
            it.pending.current()?.let { pending -> clock.instant() < pending.expiresAt } == true
    }

    @Suppress("ReturnCount") // Permission callbacks require the matching live question and session.
    suspend fun permission(questionId: UUID, granted: Boolean): List<AgentResult> {
        val part = parts.firstOrNull { it.result.followUp?.id == questionId } ?: return results
        if (!hasPermissionQuestion(questionId)) return results
        val current = part.pending.current() ?: return results
        val answer = part.request.copy(id = UUID.randomUUID(), text = "permission result", timestamp = clock.instant())
        if (part.pending.answer(current.kind, current.originalRequestId, questionId, answer) == null) return results
        part.result = if (current.kind == Kind.PROMISE) requireNotNull(part.agent.promiseFollowUp)
            .answerPromiseContactPermission(current.originalRequestId, questionId, answer, granted)
        else requireNotNull(part.agent.reminderFollowUp)
            .answerReminderContactPermission(current.originalRequestId, questionId, answer, granted)
        part.request = part.request.copy(id = answer.id, timestamp = answer.timestamp)
        track(part)
        return results
    }

    suspend fun accept(proposal: ActionProposal): List<AgentResult> {
        val part = parts.firstOrNull { it.result.proposals.any { pending -> pending === proposal } } ?: return results
        part.result = part.agent.accept(proposal)
        return results
    }

    suspend fun undo(receipt: ActionReceipt): List<AgentResult> {
        val part = parts.firstOrNull { it.result.completedActions.any { value -> value === receipt } }
            ?: return results
        val outcome = part.agent.undo(receipt)
        part.result = AgentResult(receipt.requestId, AgentResultStatus.ANSWER,
            if (outcome == UndoResult.UNDONE) "Action undone and verified locally."
            else "This action could not be undone.")
        return results
    }

    fun reject(): List<AgentResult> {
        parts.forEach(::rejectPart)
        return results
    }

    fun reject(proposal: ActionProposal): List<AgentResult> {
        parts.firstOrNull { it.result.proposals.any { value -> value === proposal } }?.let(::rejectPart)
        return results
    }

    private fun rejectPart(part: Part) {
        part.agent.discardProposal()
        part.agent.reminderFollowUp?.cancelReminderClarification()
        part.agent.promiseFollowUp?.cancelPromiseClarification()
        part.pending.clear()
        part.reference = null
        part.referenceContinuation = null
        if (part.result.completedActions.isEmpty()) part.result = AgentResult(part.request.id,
            AgentResultStatus.DENIED, "Request dismissed. Nothing was executed for this card.")
    }

    fun dropPending() { reject(); parts = emptyList() }
    fun reset() { dropPending(); history.clear(); observedRequestId = null }

    private fun unparsed(request: AgentRequest, result: AgentResult): List<AgentResult> {
        remember(request, emptyList())
        return listOf(result)
    }

    private fun question(request: AgentRequest, message: String, field: String,
        choices: List<String> = emptyList()) = AgentResult(request.id, AgentResultStatus.FOLLOW_UP, message,
        followUp = FollowUpQuestion(UUID.randomUUID(), request.id, message, setOf(field), choices))

    private enum class Kind { REFERENCE, TIME, REMINDER_PERSON, PROMISE, OTHER }
    private companion object {
        val WRITE_INTENTS = setOf(AgentIntent.CREATE_TASK, AgentIntent.UPDATE_TASK, AgentIntent.COMPLETE_TASK,
            AgentIntent.DELETE_TASK, AgentIntent.POSTPONE_TASK, AgentIntent.CREATE_REMINDER, AgentIntent.CREATE_PROMISE)
    }
    private class Part(var request: AgentRequest, val agent: ConfirmableAgent, clock: Clock) {
        var result = AgentResult(request.id, AgentResultStatus.ANSWER, "Preparing request.")
        val pending = ClarificationCoordinator<Kind, String>(clock)
        var reference: ReferenceQuestion? = null
        var referenceContinuation: PendingClarification<Kind, String>? = null
        override fun toString() = "ConversationPart(redacted)"
    }
}
