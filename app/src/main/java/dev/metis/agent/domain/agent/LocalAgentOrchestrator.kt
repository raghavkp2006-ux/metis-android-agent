package dev.metis.agent.domain.agent

import java.util.UUID
import kotlinx.coroutines.CancellationException

/** One request path. It produces answers/proposals only and has no executor or repository write dependency. */
@Suppress("LongParameterList") // The calendar flag preserves the historical disabled constructor default.
class LocalAgentOrchestrator(
    private val classifier: IntentClassifier,
    private val extractor: EntityExtractor,
    private val contexts: ContextBuilder,
    private val specialist: AgentSpecialist,
    private val policy: PolicyEngine,
    private val proposalMessage: String = "Review the proposed action. Execution is not available yet.",
    private val localCalendar: Boolean = false,
    private val readCandidate: ReadCandidate = ReadCandidate.V12,
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
            prediction.intent in ReadSafetyV12.intents && !readCandidate.allows(request, prediction.intent) ->
                blockedRead(request)
            else -> resolve(request, prediction)
        }
    }

    @Suppress("ReturnCount") // Read extraction and unresolved context must fail before specialist dispatch.
    private suspend fun resolve(request: AgentRequest, prediction: IntentPrediction): AgentResult {
        val readEntities = if (prediction.intent in ReadSafetyV12.intents) extractor.extract(request) else null
        if (readEntities != null && !readCandidate.matches(request, prediction.intent, readEntities)) {
            return blockedRead(request)
        }
        if (prediction.intent == AgentIntent.CHECK_CALENDAR && !localCalendar) return AgentResult(request.id,
            AgentResultStatus.UNSUPPORTED, "Calendar access is unavailable. Your free time is unknown.")
        val scope = readCandidate.scopedPlan(request)?.scope
        val context = contexts.snapshot(request)
        if (context.unresolvedFields.isNotEmpty()) return clarification(request, context.unresolvedFields)
        val parsed = ParsedRequest(request, prediction, readEntities ?: extractor.extract(request), context, scope)
        return when (val response = specialist.respond(parsed)) {
            is SpecialistResult.Clarification -> AgentResult(request.id, AgentResultStatus.FOLLOW_UP,
                response.question, followUp = FollowUpQuestion(UUID.randomUUID(), request.id,
                    response.question, response.fields))
            is SpecialistResult.Answer -> AgentResult(request.id, AgentResultStatus.ANSWER, response.message)
            is SpecialistResult.Unsupported -> AgentResult(request.id, AgentResultStatus.UNSUPPORTED, response.message)
            is SpecialistResult.Suggestion -> propose(request, response, context)
        }
    }

    private fun blockedRead(request: AgentRequest): AgentResult {
        val blocked = ReadSafetyV6.clarification(request)
        return AgentResult(request.id, AgentResultStatus.FOLLOW_UP, blocked.question,
            followUp = FollowUpQuestion(UUID.randomUUID(), request.id, blocked.question, blocked.fields))
    }

    private fun propose(
        request: AgentRequest, suggestion: SpecialistResult.Suggestion, context: ResolvedContext,
    ): AgentResult {
        require(suggestion.action.identity.requestId == request.id)
        return when (val review = policy.review(suggestion.action, context)) {
            is ValidationResult.Invalid -> AgentResult(request.id, AgentResultStatus.DENIED, review.userMessage)
            ValidationResult.Valid -> AgentResult(request.id, AgentResultStatus.PROPOSAL,
                proposalMessage,
                proposals = listOf(ActionProposal(UUID.randomUUID(), suggestion.action,
                    ActionRequirements.risk(suggestion.action), suggestion.reason, suggestion.evidence,
                    true, context.now, context.now.plusSeconds(PROPOSAL_LIFETIME_SECONDS))))
        }
    }

    private fun clarification(request: AgentRequest, fields: Set<String>): AgentResult {
        val special = if (readCandidate.scoped) ReadSafetyV15.clarification(request) else null
        val question = special?.question ?: "Please clarify the request before continuing: ${request.text}"
        return AgentResult(request.id, AgentResultStatus.FOLLOW_UP, question,
            followUp = FollowUpQuestion(UUID.randomUUID(), request.id, question, special?.fields ?: fields))
    }
}
private const val HIGH_CONFIDENCE = 0.90
const val REQUEST_UNSUPPORTED_MESSAGE = "Command understanding is not available yet. No action was taken."
