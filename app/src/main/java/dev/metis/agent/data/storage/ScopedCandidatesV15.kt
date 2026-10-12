package dev.metis.agent.data.storage

import androidx.sqlite.db.SimpleSQLiteQuery
import dev.metis.agent.domain.agent.ReadDateV15
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedTask
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CandidateSliceV15<T>(val records: List<T>, val truncated: Boolean)

/** Metadata predicates run in SQL before any bounded content is decrypted. No plaintext is persisted. */
internal class ScopedCandidatesV15(private val database: PersonalDatabase, private val codec: RecordCodec) {
    suspend fun tasks(scope: ReadScopeV15.Tasks, now: Long, zone: ZoneId): CandidateSliceV15<SavedTask> =
        withContext(Dispatchers.IO) {
            require(scope.status in setOf("ANY", "OPEN", "COMPLETED", "CANCELLED"))
            requireReadableKey(database.records(), codec)
            val query = query("tasks", scope.status, dateFilter(scope.date, now, zone), null)
            val rows = database.records().scopedTasks(query)
            CandidateSliceV15(rows.take(MAX_CANDIDATES).map(codec::decode), rows.size > MAX_CANDIDATES)
        }

    suspend fun person(name: String): Pair<String?, Boolean> = withContext(Dispatchers.IO) {
        requireReadableKey(database.records(), codec)
        val rows = database.person().boundedPeople(MAX_PEOPLE + 1)
        if (rows.size > MAX_PEOPLE) return@withContext null to true
        val matches = rows.filter {
            codec.decrypt(it.displayName, "persons", it.metadata.id, "display_name").equals(name, true)
        }
        matches.singleOrNull()?.metadata?.id to (matches.size > 1)
    }

    suspend fun promises(scope: ReadScopeV15.Promises, personId: String, now: Long,
        zone: ZoneId): CandidateSliceV15<SavedPromise> =
        withContext(Dispatchers.IO) {
            require(scope.status in setOf("ANY", "OPEN", "FULFILLED", "CANCELLED"))
            requireReadableKey(database.records(), codec)
            val rows = database.promise().scopedPromises(query("promises", scope.status, dateFilter(scope.date,
                now, zone), personId))
            CandidateSliceV15(rows.take(MAX_CANDIDATES).map(PromiseCodec(codec)::decode), rows.size > MAX_CANDIDATES)
        }

    private fun dateFilter(date: ReadDateV15?, now: Long, zone: ZoneId) =
        date?.let { it.field to it.window(Instant.ofEpochMilli(now), zone) }

    private fun query(table: String, status: String, date: Pair<ReadDateV15.Field, Pair<Long, Long>>?,
        personId: String?): SimpleSQLiteQuery {
        require(table in setOf("tasks", "promises"))
        val predicates = mutableListOf("(? = 'ANY' OR status = ?)")
        val args = mutableListOf<Any?>(status, status)
        if (personId != null) { predicates.add("person_id = ?"); args.add(personId) }
        if (date != null) {
            val column = when (date.first) {
                ReadDateV15.Field.DUE -> "due_at"
                ReadDateV15.Field.COMPLETED -> { require(table == "tasks"); "completed_at" }
                else -> error("Unsupported metadata date field")
            }
            val (from, until) = date.second
            predicates.add("$column >= ? AND $column < ?")
            args.add(from); args.add(until)
        }
        args.add(MAX_CANDIDATES + 1)
        return SimpleSQLiteQuery("SELECT * FROM $table WHERE ${predicates.joinToString(" AND ")} " +
            "ORDER BY updated_at DESC, id LIMIT ?", args.toTypedArray())
    }

    companion object {
        const val MAX_CANDIDATES = 20
        private const val MAX_PEOPLE = 100
    }
}
