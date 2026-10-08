package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Query

data class MemoryExpiryRow(val id: String, val revision: Long, @ColumnInfo(name = "expires_at") val expiresAt: Long)

interface MemoryRetentionQueries {
    @Query("SELECT id, revision, expires_at FROM memories WHERE expires_at IS NOT NULL AND expires_at <= :at " +
        "ORDER BY expires_at, id LIMIT :limit")
    suspend fun expiredMemories(at: Long, limit: Int): List<MemoryExpiryRow>
}
