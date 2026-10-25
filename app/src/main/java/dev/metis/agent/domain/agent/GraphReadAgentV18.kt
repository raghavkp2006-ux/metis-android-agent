package dev.metis.agent.domain.agent

import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** A read dispatch registration. Existing actions and reads retain their original delegates. */
class GraphReadAgentV18(
    private val fallback: ConfirmableAgent,
    private val reads: GraphReadPortV18,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ConfirmableAgent by fallback {
    private val pending = ClarificationCoordinator<GraphReadKindV18, GraphReadPendingV18>(clock)

    override fun fork(): ConfirmableAgent? = fallback.fork()?.let { GraphReadAgentV18(it, reads, clock, zone) }

    override suspend fun process(request: AgentRequest): AgentResult {
        return try {
            processSafely(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            pending.clear()
            AgentResult(request.id, AgentResultStatus.FAILED, "Saved data could not be read. Try again.")
        }
    }

    @Suppress("ReturnCount") // Historical reads and unrelated commands retain their original dispatch.
    private suspend fun processSafely(request: AgentRequest): AgentResult {
        val prior = pending.current()
        if (prior != null && request.text.trim().lowercase(java.util.Locale.ROOT) in setOf("cancel", "ignore")) {
            pending.clear()
            return AgentResult(request.id, AgentResultStatus.DENIED, "Saved-name choice cancelled.")
        }
        // A full new command replaces a name choice. A bare number/name can only answer this session's question.
        if (prior != null && !GraphReadGrammarV18.claims(request.text) &&
            EnglishRules().parse(request).intent == AgentIntent.UNKNOWN) {
            return answerChoice(request, prior)
        }
        pending.clear()
        // Every previously admitted production keeps its original path and answer.
        if (ReadSafetyV16.plan(request) != null) return fallback.process(request)
        val validation = GraphReadValidatorV18.validate(request, zone())
        if (validation == GraphReadValidationV18.Rejected && !GraphReadGrammarV18.claims(request.text))
            return fallback.process(request)
        fallback.discardProposal()
        return when (validation) {
            is GraphReadValidationV18.Valid -> dispatch(validation.plan)
            GraphReadValidationV18.UnresolvedPerson -> clarification(request, "Who do you mean?", "reference_person")
            GraphReadValidationV18.Rejected -> clarification(request,
                "Use one supported saved-data question without extra constraints.", "graph_query")
        }
    }

    private suspend fun dispatch(plan: GraphReadPlanV18, selectedId: String? = null): AgentResult {
        check(GraphReadValidatorV18.matches(plan))
        return when (val reply = reads.graph(plan, selectedId)) {
            is GraphReadReplyV18.Answer -> AgentResult(plan.request.id, AgentResultStatus.ANSWER, reply.message)
            is GraphReadReplyV18.Choose -> {
                val result = clarification(plan.request, reply.question, "graph_name", reply.choices.map { it.label })
                if (reply.choices.isNotEmpty()) pending.begin(PendingClarification(plan.kind, plan.request.id,
                    requireNotNull(result.followUp).id, plan.request.conversationId, plan.request.timestamp,
                    plan.request.timestamp.plusSeconds(PROPOSAL_LIFETIME_SECONDS),
                    GraphReadPendingV18(plan, reply.choices)))
                result
            }
        }
    }

    @Suppress("ReturnCount") // Invalid or expired choices cannot reach repository content reads.
    private suspend fun answerChoice(request: AgentRequest,
                                     prior: PendingClarification<GraphReadKindV18, GraphReadPendingV18>): AgentResult {
        if (!GraphReadGrammarV18.envelope(request) ||
            pending.answer(prior.kind, prior.originalRequestId, prior.questionId, request) == null) {
            pending.clear()
            return clarification(request, "This saved-name choice has expired. Ask the question again.", "graph_query")
        }
        val choices = prior.payload.choices
        val chosen = request.text.trim().toIntOrNull()?.let { choices.getOrNull(it - 1) }
            ?: choices.singleOrNull { it.label.equals(request.text.trim(), true) }
        if (chosen == null) return clarification(request, "Choose one of the saved names by number.", "graph_name",
            choices.map { it.label })
        pending.consume(prior)
        val original = prior.payload.plan
        val refreshed = original.request.copy(id = request.id, timestamp = request.timestamp)
        val validated = GraphReadValidatorV18.validate(refreshed, original.zone) as? GraphReadValidationV18.Valid
            ?: return clarification(request, "Ask the saved-data question again.", "graph_query")
        return dispatch(validated.plan, chosen.id)
    }

    private fun clarification(request: AgentRequest, message: String, field: String,
                              choices: List<String> = emptyList()) =
        AgentResult(request.id, AgentResultStatus.FOLLOW_UP, message, followUp =
            FollowUpQuestion(UUID.randomUUID(), request.id, message, setOf(field), choices))
}

private class GraphReadPendingV18(val plan: GraphReadPlanV18, val choices: List<GraphReadChoiceV18>) {
    override fun toString() = "GraphReadPendingV18(redacted)"
}
