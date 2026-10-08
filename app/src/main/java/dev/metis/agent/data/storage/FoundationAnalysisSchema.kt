package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase

internal object FoundationAnalysisSchema {
    fun create(db: SupportSQLiteDatabase) {
        createExperiment(db)
        createHabit(db)
        createDerivedInsight(db)
    }

    private fun createExperiment(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE experiments (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    name BLOB NOT NULL,
                    hypothesis BLOB NOT NULL,
                    started_at INTEGER NOT NULL,
                    consented_at INTEGER NOT NULL,
                    definition BLOB NOT NULL,
                    status TEXT NOT NULL,
                    ended_at INTEGER
                )
            """.trimIndent())
    }
    
    private fun createHabit(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE habits (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    name BLOB NOT NULL,
                    definition BLOB NOT NULL,
                    sample_count INTEGER NOT NULL,
                    confidence REAL NOT NULL,
                    observation_start INTEGER NOT NULL,
                    observation_end INTEGER NOT NULL,
                    method_version TEXT NOT NULL,
                    consent_required INTEGER NOT NULL
                )
            """.trimIndent())
    }
    
    private fun createDerivedInsight(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE derived_insights (
                    id TEXT NOT NULL PRIMARY KEY,
                    kind TEXT NOT NULL,
                    computed_at INTEGER NOT NULL,
                    observation_start INTEGER NOT NULL,
                    observation_end INTEGER NOT NULL,
                    sample_count INTEGER NOT NULL,
                    method_version TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    statistics_json BLOB,
                    source_watermark INTEGER NOT NULL,
                    entity_type TEXT,
                    entity_id TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_derived_insights_kind_computed_at ON derived_insights(kind,computed_at)")
            db.execSQL(
                "CREATE INDEX index_derived_insights_entity_type_entity_id ON " +
                "derived_insights(entity_type,entity_id)"
            )
    }
}
