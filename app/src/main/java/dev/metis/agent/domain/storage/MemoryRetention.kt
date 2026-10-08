package dev.metis.agent.domain.storage

import java.util.UUID

data class MemoryExpiryCandidate(val id: String, val revision: Long, val expiresAt: Long) {
    init { require(UUID.fromString(id).toString() == id && revision >= 0) }
}

/** Metadata-only snapshot. Deletion must revalidate every entry, not rerun a broad expiry predicate. */
data class MemoryExpiryReview(
    val at: Long,
    val candidates: List<MemoryExpiryCandidate>,
    val hasMore: Boolean,
) {
    init {
        require(candidates.size <= BATCH_LIMIT && candidates.map { it.id }.distinct().size == candidates.size)
        require(candidates.all { it.expiresAt <= at })
    }
    companion object { const val BATCH_LIMIT = 50 }
}

interface MemoryRetentionRepository {
    suspend fun reviewExpired(at: Long): MemoryExpiryReview
    suspend fun deleteExpired(review: MemoryExpiryReview): Int
}
