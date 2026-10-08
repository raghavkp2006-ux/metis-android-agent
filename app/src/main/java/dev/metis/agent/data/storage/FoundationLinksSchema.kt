package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase

internal object FoundationLinksSchema {
    fun create(db: SupportSQLiteDatabase) {
        createPromise(db)
        createRoutine(db)
        createActionRun(db)
    }

    private fun createPromise(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE promises (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    person_id TEXT NOT NULL,
                    content BLOB NOT NULL,
                    status TEXT NOT NULL,
                    task_id TEXT,
                    due_at INTEGER,
                    source_event_id TEXT,
                    FOREIGN KEY(person_id) REFERENCES persons(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(task_id) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(source_event_id) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_promises_person_id_status_due_at ON promises(person_id,status,due_at)")
            db.execSQL("CREATE INDEX index_promises_task_id ON promises(task_id)")
            db.execSQL("CREATE INDEX index_promises_source_event_id ON promises(source_event_id)")
    }
    
    private fun createRoutine(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE routines (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    name BLOB NOT NULL,
                    recurrence_rule TEXT NOT NULL,
                    zone_id TEXT NOT NULL,
                    definition BLOB NOT NULL,
                    enabled INTEGER NOT NULL
                )
            """.trimIndent())
    }
    
    private fun createActionRun(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE action_runs (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    request_id TEXT NOT NULL,
                    proposal_id TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    payload BLOB NOT NULL,
                    risk TEXT NOT NULL,
                    status TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    verification TEXT NOT NULL,
                    finished_at INTEGER,
                    receipt BLOB,
                    safe_error_code TEXT,
                    entity_type TEXT,
                    entity_id TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX index_action_runs_idempotency_key ON action_runs(idempotency_key)")
            db.execSQL("CREATE INDEX index_action_runs_status_started_at ON action_runs(status,started_at)")
            db.execSQL("CREATE INDEX index_action_runs_entity_type_entity_id ON action_runs(entity_type,entity_id)")
    }
}
