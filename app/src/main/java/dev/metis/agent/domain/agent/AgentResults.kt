package dev.metis.agent.domain.agent

import java.time.Duration
import java.time.Instant
import java.util.UUID

// Preserve the protocol envelope while defensively copying its evidence.
@Suppress("LongParameterList")
class ActionProposal(
    val id: UUID,
    val action: Action,
    val risk: RiskLevel,
    val reason: String,
    evidence: List<Evidence>,
    val requiresConfirmation: Boolean,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    val requestId: UUID get() = action.identity.requestId
    val evidence = frozenList(evidence)
    init {
        requireText(reason)
        require(expiresAt > createdAt)
        require(Duration.between(createdAt, expiresAt) <= Duration.ofSeconds(PROPOSAL_LIFETIME_SECONDS))
        require(risk >= ActionRequirements.risk(action))
        require(requiresConfirmation)
    }
}

class FollowUpQuestion(
    val id: UUID,
    val requestId: UUID,
    val question: String,
    missingFields: Set<String>,
    suggestedChoices: List<String> = emptyList(),
) {
    val missingFields = frozenSet(missingFields)
    val suggestedChoices = frozenList(suggestedChoices)
    init {
        requireText(question)
        require(missingFields.isNotEmpty())
        missingFields.forEach(::requireText)
        suggestedChoices.forEach(::requireText)
    }
}
class Explanation(val summary: String, evidence: List<Evidence> = emptyList()) {
    val evidence = frozenList(evidence)
    init { requireText(summary) }
}
enum class AgentResultStatus { ANSWER, PROPOSAL, FOLLOW_UP, UNSUPPORTED, DENIED, FAILED }
@Suppress("LongParameterList")
class AgentResult(
    val requestId: UUID,
    val status: AgentResultStatus,
    val message: String,
    proposals: List<ActionProposal> = emptyList(),
    completedActions: List<ActionReceipt> = emptyList(),
    val followUp: FollowUpQuestion? = null,
    val explanation: Explanation? = null,
) {
    val proposals = frozenList(proposals)
    val completedActions = frozenList(completedActions)
    init {
        requireText(message)
        require(proposals.all { it.requestId == requestId } && completedActions.all { it.requestId == requestId })
        require(followUp == null || followUp.requestId == requestId)
        require((status == AgentResultStatus.PROPOSAL) == proposals.isNotEmpty())
        require((status == AgentResultStatus.FOLLOW_UP) == (followUp != null))
        require(status == AgentResultStatus.ANSWER || completedActions.isEmpty())
    }
}
