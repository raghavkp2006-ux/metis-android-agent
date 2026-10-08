package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase

internal object FoundationTimeSchema {
    fun create(db: SupportSQLiteDatabase) {
        createReminder(db)
        createFocusSession(db)
        createEvent(db)
    }

    private fun createReminder(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE reminders (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    title BLOB NOT NULL,
                    trigger_at INTEGER NOT NULL,
                    local_date_time TEXT NOT NULL,
                    zone_id TEXT NOT NULL,
                    precision TEXT NOT NULL,
                    scheduling_state TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL,
                    task_id TEXT,
                    person_id TEXT,
                    platform_token TEXT,
                    delivered_at INTEGER,
                    FOREIGN KEY(task_id) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(person_id) REFERENCES persons(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX index_reminders_idempotency_key ON reminders(idempotency_key)")
            db.execSQL(
                "CREATE INDEX index_reminders_scheduling_state_trigger_at ON " +
                "reminders(scheduling_state,trigger_at)"
            )
            db.execSQL("CREATE INDEX index_reminders_task_id ON reminders(task_id)")
            db.execSQL("CREATE INDEX index_reminders_person_id ON reminders(person_id)")
    }
    
    private fun createFocusSession(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE focus_sessions (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    started_at INTEGER NOT NULL,
                    planned_seconds INTEGER NOT NULL,
                    outcome TEXT NOT NULL,
                    task_id TEXT,
                    ended_at INTEGER,
                    FOREIGN KEY(task_id) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_focus_sessions_task_id ON focus_sessions(task_id)")
    }
    
    private fun createEvent(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE events (
                    id TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    source TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    importance REAL NOT NULL,
                    schema_version INTEGER NOT NULL,
                    entity_type TEXT,
                    entity_id TEXT,
                    metadata BLOB,
                    request_id TEXT,
                    action_id TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_events_timestamp ON events(timestamp)")
            db.execSQL("CREATE INDEX index_events_entity_type_entity_id ON events(entity_type,entity_id)")
    }
}
