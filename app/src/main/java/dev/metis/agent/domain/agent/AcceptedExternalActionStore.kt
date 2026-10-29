package dev.metis.agent.domain.agent

import java.util.UUID

interface AcceptedExternalActionStore {
    suspend fun context(request: AgentRequest): ResolvedContext
    suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt
}

