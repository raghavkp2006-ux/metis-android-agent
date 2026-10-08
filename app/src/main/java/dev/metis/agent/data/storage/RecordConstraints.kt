package dev.metis.agent.data.storage

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room exports table/index/FK shape; these additional invariants are installed on creation and upgrades.
 * Future migrations must recreate these triggers and test both schema and data preservation.
 */
internal object RecordConstraints : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        rebuild(db)
        installDependencies(db)
        installPreferences(db)
        installPlanning(db)
    }

    fun installPlanning(db: SupportSQLiteDatabase) {
        install(db, "projects", "NEW.status NOT IN ('ACTIVE','COMPLETED','ARCHIVED')")
        install(db, "goals", "NEW.status NOT IN ('ACTIVE','ACHIEVED','CANCELLED') OR NEW.priority NOT BETWEEN 0 AND 3")
    }

    fun installPreferences(db: SupportSQLiteDatabase) {
        install(db, "preferences", """
            NEW.source != 'EXPLICIT' OR NEW.value_schema_version != 1 OR
            NOT ((NEW.`key` = 'DAY_START_TIME' AND NEW.value_kind = 'LOCAL_TIME') OR
                 (NEW.`key` = 'FOCUS_BLOCK_MINUTES' AND NEW.value_kind = 'DURATION_MINUTES') OR
                 (NEW.`key` = 'WEEK_START_DAY' AND NEW.value_kind = 'WEEKDAY'))
        """.trimIndent())
    }

    fun installDependencies(db: SupportSQLiteDatabase) {
        install(db, "task_dependencies", "NEW.task_id = NEW.depends_on_task_id")
    }

    fun rebuild(db: SupportSQLiteDatabase) {
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
            NEW.importance NOT BETWEEN 0 AND 1 OR NEW.confidence NOT BETWEEN 0 AND 1 OR
            (NEW.entity_type IS NULL) != (NEW.entity_id IS NULL) OR
            (NEW.entity_type IS NOT NULL AND NEW.entity_type NOT IN ('TASK','SCHEDULE'))
        """.trimIndent())
    }

    private fun install(db: SupportSQLiteDatabase, table: String, condition: String) {
        listOf("INSERT", "UPDATE").forEach { operation ->
            db.execSQL("DROP TRIGGER IF EXISTS ${table}_${operation.lowercase()}_integrity")
            db.execSQL("""
                CREATE TRIGGER ${table}_${operation.lowercase()}_integrity BEFORE $operation ON $table
                WHEN NEW.revision < 0 OR NEW.updated_at < NEW.created_at OR ($condition)
                BEGIN SELECT RAISE(ABORT, 'Invalid record metadata'); END
            """.trimIndent())
        }
    }
}
