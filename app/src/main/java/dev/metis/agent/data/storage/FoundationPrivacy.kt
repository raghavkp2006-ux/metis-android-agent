package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase

/** Runs in the caller's transaction, before FK cascades erase the links needed for cleanup. */
internal object FoundationPrivacy {
    fun detach(database: PersonalDatabase, codec: RecordCodec, type: String, id: String) {
        Cleanup(database.openHelper.writableDatabase, codec, type to id).run()
    }

    private class Cleanup(
        private val db: SupportSQLiteDatabase,
        private val codec: RecordCodec,
        private val root: Pair<String, String>,
    ) {
        private val pending = ArrayDeque<Pair<String, String>>()
        private val visited = mutableSetOf<Pair<String, String>>()

        fun run() {
            pending.add(root)
            while (pending.isNotEmpty()) {
                val ref = pending.removeFirst()
                if (visited.add(ref)) clean(ref)
            }
        }

        private fun clean(ref: Pair<String, String>) {
            owned(ref)
            val args = arrayOf(ref.first, ref.second)
            linkedMapOf("MEMORY" to "memories", "RECOMMENDATION" to "recommendations",
                "DERIVED_INSIGHT" to "derived_insights").forEach { (type, table) ->
                removeLinked(type, table, "entity_type = ? AND entity_id = ?", args)
            }
            ids("events", "entity_type = ? AND entity_id = ?", args).forEach { pending.add("EVENT" to it) }
            db.execSQL("UPDATE events SET metadata = NULL, entity_type = NULL, entity_id = NULL " +
                "WHERE entity_type = ? AND entity_id = ?", args)
            ids("action_runs", "entity_type = ? AND entity_id = ?", args).forEach {
                pending.add("ACTION_RUN" to it)
            }
            when (ref.first) {
                "EVENT" -> {
                    removeLinked("MEMORY", "memories", "source_event_id = ?", arrayOf(ref.second))
                    db.execSQL("UPDATE events SET metadata = NULL WHERE id = ?", arrayOf(ref.second))
                }
                "ACTION_RUN" -> action(ref)
                "ACTION_AUDIT" -> audit(ref.second)
            }
        }

        private fun owned(ref: Pair<String, String>) {
            val id = arrayOf(ref.second)
            when (ref.first) {
                "PERSON" -> {
                    ids("relationships", "person_id = ?", id).forEach { pending.add("RELATIONSHIP" to it) }
                    ids("promises", "person_id = ?", id).forEach { pending.add("PROMISE" to it) }
                }
                "TASK" -> ids("task_dependencies", "task_id = ? OR depends_on_task_id = ?",
                    arrayOf(ref.second, ref.second)).forEach { pending.add("TASK_DEPENDENCY" to it) }
                "ACTION_RUN" -> ids("action_audit", "action_run_id = ?", id).forEach {
                    pending.add("ACTION_AUDIT" to it)
                }
            }
        }

        private fun removeLinked(type: String, table: String, where: String, args: Array<String>) {
            ids(table, where, args).forEach { id ->
                val ref = type to id
                pending.add(ref)
                if (ref != root) db.execSQL("DELETE FROM $table WHERE id = ?", arrayOf(id))
            }
        }

        private fun ids(table: String, where: String, args: Array<String>): List<String> =
            db.query("SELECT id FROM $table WHERE $where", args).use {
                buildList { while (it.moveToNext()) add(it.getString(0)) }
            }

        private fun action(ref: Pair<String, String>) {
            val id = ref.second
            ids("events", "action_id = ?", arrayOf(id)).forEach { pending.add("EVENT" to it) }
            val correlation = if (ref == root) ", action_id = NULL" else ""
            db.execSQL("UPDATE events SET metadata = NULL$correlation WHERE action_id = ?", arrayOf(id))
            // Preserve the original revision for the caller's physical root deletion.
            if (ref == root) return
            val metadata = db.query("SELECT revision, updated_at FROM action_runs WHERE id = ?", arrayOf(id)).use {
                if (!it.moveToFirst()) return
                Math.addExact(it.getLong(0), 1L) to maxOf(System.currentTimeMillis(), it.getLong(1))
            }
            db.execSQL("""
                UPDATE action_runs SET payload = ?, receipt = NULL, entity_type = NULL, entity_id = NULL,
                    status = 'UNKNOWN', verification = 'UNVERIFIED', safe_error_code = 'PERSONAL_DATA_REMOVED',
                    revision = ?, updated_at = ? WHERE id = ?
            """.trimIndent(), arrayOf(codec.encryptJson("{}", "action_runs", id, "payload"),
                metadata.first, metadata.second, id))
        }

        private fun audit(id: String) {
            db.execSQL("UPDATE action_audit SET reason = ?, evidence = ?, permission_snapshot_json = ? WHERE id = ?",
                arrayOf(codec.encrypt("Personal data removed.", "action_audit", id, "reason"),
                    codec.encryptJson("{}", "action_audit", id, "evidence"),
                    codec.encryptJson("{}", "action_audit", id, "permission_snapshot_json"), id))
        }
    }
}
