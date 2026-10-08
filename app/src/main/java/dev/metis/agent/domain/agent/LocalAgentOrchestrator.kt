package dev.metis.agent.domain.agent

import java.util.UUID
import kotlinx.coroutines.CancellationException

/** One request path. It produces answers/proposals only and has no executor or repository write dependency. */
class LocalAgentOrchestrator(
    private val classifier: IntentClassifier,
    private val extractor: EntityExtractor,
    private val contexts: ContextBuilder,
    private val specialist: AgentSpecialist,
    private val policy: PolicyEngine,
) : AgentOrchestrator {
    override suspend fun process(request: AgentRequest): AgentResult = try {
        processSafely(request)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AgentResult(request.id, AgentResultStatus.FAILED, "The request could not be processed. Try again.")
    }

    private suspend fun processSafely(request: AgentRequest): AgentResult {
        val prediction = classifier.classify(request)
        return when {
            prediction.negated -> AgentResult(request.id, AgentResultStatus.DENIED,
                "No action was proposed for a negated request.")
            prediction.intent == AgentIntent.UNSUPPORTED -> AgentResult(request.id,
                AgentResultStatus.UNSUPPORTED, REQUEST_UNSUPPORTED_MESSAGE)
            prediction.intent == AgentIntent.UNKNOWN || prediction.confidence < HIGH_CONFIDENCE ->
                clarification(request, setOf("intent"))
            else -> resolve(request, prediction)
        }
    }

    private suspend fun resolve(request: AgentRequest, prediction: IntentPrediction): AgentResult {
        val context = contexts.snapshot(request)
        if (context.unresolvedFields.isNotEmpty()) return clarification(request, context.unresolvedFields)
        val parsed = ParsedRequest(request, prediction, extractor.extract(request), context)
        return when (val response = specialist.respond(parsed)) {
            is SpecialistResult.Answer -> AgentResult(request.id, AgentResultStatus.ANSWER, response.message)
            is SpecialistResult.Unsupported -> AgentResult(request.id, AgentResultStatus.UNSUPPORTED, response.message)
            is SpecialistResult.Suggestion -> propose(request, response, context)
        }
    }

    private fun propose(
        request: AgentRequest, suggestion: SpecialistResult.Suggestion, context: ResolvedContext,
    ): AgentResult {
        require(suggestion.action.identity.requestId == request.id)
        return when (val review = policy.review(suggestion.action, context)) {
            is ValidationResult.Invalid -> AgentResult(request.id, AgentResultStatus.DENIED, review.userMessage)
            ValidationResult.Valid -> AgentResult(request.id, AgentResultStatus.PROPOSAL,
                "Review the proposed action. Execution is not available yet.",
                proposals = listOf(ActionProposal(UUID.randomUUID(), suggestion.action,
                    ActionRequirements.risk(suggestion.action), suggestion.reason, suggestion.evidence,
                    true, context.now, context.now.plusSeconds(PROPOSAL_LIFETIME_SECONDS))))
        }
    }

    private fun clarification(request: AgentRequest, fields: Set<String>) = AgentResult(
        request.id, AgentResultStatus.FOLLOW_UP, "Please clarify the request before continuing.",
        followUp = FollowUpQuestion(UUID.randomUUID(), request.id, "Which details did you mean?", fields),
    )
}
private const val HIGH_CONFIDENCE = 0.90
const val REQUEST_UNSUPPORTED_MESSAGE = "Command understanding is not available yet. No action was taken."
