package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedEvent

/** Persists a supplied outcome atomically; it cannot dispatch an action or claim platform verification. */
class FoundationOutcomes(private val database: PersonalDatabase, private val stores: FoundationRepositories) {
    suspend fun record(action: SavedActionRun, event: SavedEvent, audit: SavedActionAudit) {
        require(audit.actionRunId == action.metadata.id)
        require(event.actionId == action.metadata.id && event.requestId == action.requestId)
        database.withTransaction {
            stores.actionRun.save(action)
            stores.event.save(event)
            stores.actionAudit.save(audit)
        }
    }
}
