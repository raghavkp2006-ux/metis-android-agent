package dev.metis.agent.data.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.MemorySearchResult
import dev.metis.agent.domain.storage.MemorySearchText
import dev.metis.agent.domain.storage.MemoryRanking
import dev.metis.agent.domain.storage.SavedMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** A fresh :memory: connection per search. Never attach the personal database or write an FTS file. */
internal class InMemoryMemorySearch(private val dao: RecordDao, private val codec: RecordCodec) {
    suspend fun search(query: MemorySearchQuery): MemorySearchResult = withContext(Dispatchers.IO) {
        val expression = MemorySearchText.expression(query.text)
        if (expression == null && query.text.isNotEmpty()) {
            return@withContext MemorySearchResult(emptyList(), 0, false, false, query.candidateLimit)
        }
        val candidates = dao.memoryCandidates(MemoryCandidateQuery.build(query))
        val bounded = candidates.take(query.candidateLimit)
        // Decrypt only the bounded metadata-filtered candidates, fail without partial results.
        val decoded = bounded.map { coroutineContext.ensureActive(); codec.decode(it) }
        if (query.text.isEmpty()) return@withContext ranked(decoded, query, candidates.size > bounded.size)
        SQLiteDatabase.create(null).use { index ->
            check(index.path == ":memory:")
            index.execSQL("PRAGMA temp_store = MEMORY")
            index.execSQL("""
                CREATE VIRTUAL TABLE memory_search USING fts4(id, content, notindexed=id, tokenize=unicode61)
            """.trimIndent())
            decoded.forEach { memory ->
                coroutineContext.ensureActive()
                index.insertOrThrow("memory_search", null, ContentValues().apply {
                    put("id", memory.metadata.id)
                    put("content", memory.content)
                })
            }
            val matchingIds = index.rawQuery(
                "SELECT id FROM memory_search WHERE content MATCH ?", arrayOf(expression),
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            coroutineContext.ensureActive()
            val matches = decoded.filter { it.metadata.id in matchingIds }
            ranked(matches, query, candidates.size > bounded.size, decoded.size)
        }
    }

    private fun ranked(
        matches: List<SavedMemory>, query: MemorySearchQuery, truncated: Boolean, candidateCount: Int = matches.size,
    ): MemorySearchResult {
        val ranked = MemoryRanking.rank(matches, query).take(query.limit)
        return MemorySearchResult(ranked.map { it.first }, candidateCount, truncated, matches.size > query.limit,
            query.candidateLimit, ranked.map { it.second })
    }
}
