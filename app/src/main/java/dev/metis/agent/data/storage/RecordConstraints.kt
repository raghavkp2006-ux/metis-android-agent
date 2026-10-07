package dev.metis.agent.data.storage

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room exports table/index/FK shape; these additional invariants are installed on fresh v1 creation.
 * Future migrations must recreate these triggers and test both schema and data preservation.
 */
internal object RecordConstraints : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        install(db, "tasks", """
            NEW.status NOT IN ('OPEN','COMPLETED','CANCELLED') OR
            NEW.priority NOT BETWEEN 0 AND 3 OR
            (NEW.due_at IS NULL) != (NEW.due_zone_id IS NULL) OR
            (NEW.estimated_seconds IS NOT NULL AND NEW.estimated_seconds <= 0) OR
            (NEW.status = 'COMPLETED') != (NEW.completed_at IS NOT NULL)
        """.trimIndent())
        install(db, "schedule_blocks", """
            NEW.end_at <= NEW.start_at OR
            NEW.status NOT IN ('PROPOSED','ACCEPTED','COMPLETED','CANCELLED')
        """.trimIndent())
        install(db, "memories", """
            NEW.memory_type NOT IN ('WORKING','EPISODIC','SEMANTIC','PROCEDURAL','GOAL','RELATIONSHIP') OR
            NEW.origin NOT IN ('EXPLICIT','DERIVED') OR
            NEW.importance NOT BETWEEN 0 AND 1 OR NEW.confidence NOT BETWEEN 0 AND 1
        """.trimIndent())
    }

    private fun install(db: SupportSQLiteDatabase, table: String, condition: String) {
        listOf("INSERT", "UPDATE").forEach { operation ->
            db.execSQL("""
                CREATE TRIGGER ${table}_${operation.lowercase()}_integrity BEFORE $operation ON $table
                WHEN NEW.revision < 0 OR NEW.updated_at < NEW.created_at OR ($condition)
                BEGIN SELECT RAISE(ABORT, 'Invalid record metadata'); END
            """.trimIndent())
        }
    }
}
