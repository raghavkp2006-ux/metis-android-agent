package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.storage.MemoryExpiryCandidate
import dev.metis.agent.domain.storage.MemoryExpiryReview
import dev.metis.agent.domain.storage.MemoryRepository
import dev.metis.agent.domain.storage.MemoryRetentionRepository
import dev.metis.agent.domain.storage.RevisionConflictException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalMemoryRetention(
    private val database: PersonalDatabase,
    private val repository: MemoryRepository,
    private val codec: RecordCodec,
    private val now: () -> Long,
) : MemoryRetentionRepository {
    override suspend fun reviewExpired(at: Long) = withContext(Dispatchers.IO) {
        require(at <= now())
        val rows = database.records().expiredMemories(at, MemoryExpiryReview.BATCH_LIMIT + 1)
        MemoryExpiryReview(at, rows.take(MemoryExpiryReview.BATCH_LIMIT).map {
            MemoryExpiryCandidate(it.id, it.revision, it.expiresAt)
        }, rows.size > MemoryExpiryReview.BATCH_LIMIT)
    }

    override suspend fun deleteExpired(review: MemoryExpiryReview): Int = withContext(Dispatchers.IO) {
        database.withTransaction {
            require(review.at <= now())
            requireReadableKey(database.records(), codec)
            review.candidates.forEach { snapshot ->
                val current = database.records().memory(snapshot.id)
                if (current == null || current.metadata.revision != snapshot.revision ||
                    current.expiresAt != snapshot.expiresAt) throw RevisionConflictException()
            }
            // Privacy cleanup can already remove another reviewed memory linked to an earlier root.
            review.candidates.forEach { snapshot ->
                if (database.records().memory(snapshot.id) != null) {
                    repository.deleteMemory(snapshot.id, snapshot.revision)
                }
            }
            review.candidates.size
        }
    }
}
