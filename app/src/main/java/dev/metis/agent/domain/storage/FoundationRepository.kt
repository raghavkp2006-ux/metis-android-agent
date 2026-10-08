package dev.metis.agent.domain.storage

import kotlinx.coroutines.flow.Flow

/** Local storage only. Saving a record cannot grant consent, permissions or dispatch an action. */
interface FoundationRepository<T : FoundationRecord> {
    fun observe(): Flow<List<T>>
    suspend fun save(record: T)
    suspend fun delete(id: String, revision: Long)
}
