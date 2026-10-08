package dev.metis.agent.data.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PersonalMigrations {
    val FROM_2_TO_3: Migration = object : Migration(2, DEPENDENCY_SCHEMA_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE task_dependencies (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                    revision INTEGER NOT NULL, task_id TEXT NOT NULL, depends_on_task_id TEXT NOT NULL,
                    FOREIGN KEY(task_id) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(depends_on_task_id) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                CREATE UNIQUE INDEX index_task_dependencies_task_id_depends_on_task_id
                ON task_dependencies(task_id, depends_on_task_id)
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX index_task_dependencies_depends_on_task_id ON task_dependencies(depends_on_task_id)
            """.trimIndent())
            RecordConstraints.rebuild(db)
            RecordConstraints.installDependencies(db)
        }
    }

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

private const val DEPENDENCY_SCHEMA_VERSION = 3
