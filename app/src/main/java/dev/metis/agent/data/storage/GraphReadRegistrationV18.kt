package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ConfirmableAgent
import dev.metis.agent.domain.agent.GraphReadAgentV18

/** The composition root's single registration; historical local reads stay delegated unchanged. */
fun registerGraphReadsV18(fallback: ConfirmableAgent, repository: LocalPersonalRepository): ConfirmableAgent =
    GraphReadAgentV18(fallback, LocalGraphReadsV18(LocalAgentReads(repository), LocalGraphSavedDataV18(repository)))
