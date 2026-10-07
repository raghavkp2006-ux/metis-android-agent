package dev.metis.agent.data.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PersonalMigrations {
    val FROM_1_TO_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Existing ciphertext/AAD and metadata remain untouched; unknown links remain NULL.
            db.execSQL("ALTER TABLE memories ADD COLUMN entity_type TEXT")
            db.execSQL("ALTER TABLE memories ADD COLUMN entity_id TEXT")
            db.execSQL("CREATE INDEX index_memories_entity_type_entity_id ON memories(entity_type, entity_id)")
            RecordConstraints.rebuild(db)
        }
    }
}
