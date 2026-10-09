package dev.metis.agent.domain.decision

import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.FollowUpQuestion
import dev.metis.agent.domain.agent.NormalizedValue
import java.util.UUID
import kotlinx.coroutines.CancellationException

fun interface DecisionPort {
    suspend fun recommend(requestId: UUID, availableSeconds: Long?): AgentResult
}

/** An explicit recommendation request invalidates any outstanding mutation proposal. */
class DecisionAgent(private val fallback: ConfirmableAgent, private val decisions: DecisionPort) : ConfirmableAgent {
    override suspend fun process(request: AgentRequest): AgentResult {
        discardProposal()
        val parsed = EnglishRules().parse(request)
        if (parsed.intent != AgentIntent.WHAT_SHOULD_I_DO) return fallback.process(request)
        val duration = parsed.entities.singleOrNull()?.normalizedValue as? NormalizedValue.Duration
        return if (parsed.entities.isNotEmpty() && duration == null) {
            val question = "Specify an available duration from 1 minute to 1 day."
            AgentResult(request.id, AgentResultStatus.FOLLOW_UP, question,
                followUp = FollowUpQuestion(UUID.randomUUID(), request.id, question, setOf("duration")))
        } else try {
            decisions.recommend(request.id, duration?.seconds)
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
            AgentResult(request.id, AgentResultStatus.FAILED,
                "The recommendation could not be read or saved. No task was changed. Try again.")
        }
    }

    override suspend fun accept(proposal: ActionProposal) = fallback.accept(proposal)
    override suspend fun undo(receipt: ActionReceipt) = fallback.undo(receipt)
    override fun discardProposal() = fallback.discardProposal()
}
