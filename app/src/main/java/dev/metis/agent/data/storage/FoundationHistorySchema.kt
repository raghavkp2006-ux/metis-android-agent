package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase

internal object FoundationHistorySchema {
    fun create(db: SupportSQLiteDatabase) {
        createActionAudit(db)
        createAgentSession(db)
        createRecommendation(db)
    }

    private fun createActionAudit(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE action_audit (
                    id TEXT NOT NULL PRIMARY KEY,
                    action_run_id TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    policy_version TEXT NOT NULL,
                    autonomy_level INTEGER NOT NULL,
                    permission_snapshot_json BLOB NOT NULL,
                    decision TEXT NOT NULL,
                    reason BLOB NOT NULL,
                    evidence BLOB NOT NULL,
                    confirmation_id TEXT,
                    FOREIGN KEY(action_run_id) REFERENCES action_runs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_action_audit_action_run_id ON action_audit(action_run_id)")
    }
    
    private fun createAgentSession(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE agent_sessions (
                    id TEXT NOT NULL PRIMARY KEY,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER,
                    summary BLOB
                )
            """.trimIndent())
    }
    
    private fun createRecommendation(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE recommendations (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    generated_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    score REAL NOT NULL,
                    score_components_json BLOB NOT NULL,
                    reason BLOB NOT NULL,
                    evidence BLOB NOT NULL,
                    status TEXT NOT NULL,
                    entity_type TEXT,
                    entity_id TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_recommendations_status_expires_at ON recommendations(status,expires_at)")
            db.execSQL(
                "CREATE INDEX index_recommendations_entity_type_entity_id ON " +
                "recommendations(entity_type,entity_id)"
            )
    }
}
