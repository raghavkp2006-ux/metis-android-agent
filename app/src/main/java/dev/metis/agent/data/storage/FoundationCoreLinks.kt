package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase

internal object FoundationCoreLinks {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence_rule TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence_zone_id TEXT")
        db.execSQL("ALTER TABLE schedule_blocks ADD COLUMN routine_id TEXT REFERENCES routines(id) ON DELETE SET NULL")
        db.execSQL("ALTER TABLE schedule_blocks ADD COLUMN score_components_json BLOB")
        db.execSQL("CREATE INDEX index_schedule_blocks_routine_id ON schedule_blocks(routine_id)")
        db.execSQL("ALTER TABLE memories ADD COLUMN source_event_id TEXT REFERENCES events(id) ON DELETE SET NULL")
        db.execSQL("CREATE INDEX index_memories_source_event_id ON memories(source_event_id)")
    }
}
