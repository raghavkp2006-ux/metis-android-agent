package dev.metis.agent

import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.RevisionTarget
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.MemoryEntityType
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.flowOf

internal class ConversationClock(var current: Instant = Instant.parse("2026-10-25T06:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant() = current
}

internal class ConversationTaskStore(private val clock: Clock) : AcceptedTaskStore {
    var writes = 0
    override suspend fun resolveCompletion(title: String): CompletionSelection = CompletionSelection.Selected(title,
        RevisionTarget(EntityReference(MemoryEntityType.TASK, UUID.randomUUID()), 0))
    override suspend fun context(request: AgentRequest) = ResolvedContext(clock.instant(), clock.zone,
        AutonomyLevel.SUGGEST, CapabilitySnapshot(clock.instant(),
            setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
        screenContext = request.screenContext, conversationId = request.conversationId)
    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt {
        writes++
        return ActionReceipt(UUID.randomUUID(), proposal.action.identity, ActionType.TASK,
            ReceiptOutcome(ActionStatus.SUCCEEDED, clock.instant(), clock.instant(),
                OutcomeVerification.VERIFIED_LOCAL),
            "Synthetic action saved")
    }
    override suspend fun retry(actionId: UUID): ActionReceipt = error("Unexpected retry")
    override suspend fun cancelPending(actionId: UUID) = Unit
    override suspend fun undo(receipt: ActionReceipt) = UndoResult.NOT_SUPPORTED
    override fun observeHistory() = flowOf(emptyList<TaskActionHistoryEntry>())
}

internal class ConversationReads : AgentReadPort {
    override suspend fun tasks() = "Synthetic saved tasks"
    override suspend fun memories(query: String, at: Long) = "Synthetic memory"
    override suspend fun promises(person: String) = SpecialistResult.Answer("Synthetic promises")
}
