package dev.metis.agent.data.storage

import androidx.sqlite.db.SimpleSQLiteQuery
import dev.metis.agent.domain.storage.MemorySearchQuery

/** All values are bound; the query text and table/column identifiers are fixed. */
internal object MemoryCandidateQuery {
    fun build(query: MemorySearchQuery) = SimpleSQLiteQuery("""
        SELECT * FROM memories
        WHERE (? IS NULL OR memory_type = ?) AND (? IS NULL OR origin = ?)
        AND (? IS NULL OR (entity_type = ? AND entity_id = ?))
        AND (expires_at IS NULL OR expires_at > ?)
        AND (memory_type != 'WORKING' OR expires_at IS NOT NULL)
        ORDER BY updated_at DESC, id LIMIT ?
    """.trimIndent(), arrayOf<Any?>(
        query.type?.name, query.type?.name, query.origin?.name, query.origin?.name,
        query.entityType?.name, query.entityType?.name, query.entityId, query.at, query.candidateLimit + 1,
    ))
}
