package dev.metis.agent.data.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PlanningMigration {
    val FROM_4_TO_5: Migration = object : Migration(PREFERENCE_VERSION, PLANNING_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE projects (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                    revision INTEGER NOT NULL, title BLOB NOT NULL, description BLOB, status TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_projects_status ON projects(status)")
            db.execSQL("""
                CREATE TABLE goals (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                    revision INTEGER NOT NULL, title BLOB NOT NULL, description BLOB, project_id TEXT,
                    target_at INTEGER, priority INTEGER NOT NULL, status TEXT NOT NULL,
                    FOREIGN KEY(project_id) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_goals_project_id_status ON goals(project_id,status)")
            db.execSQL("CREATE INDEX index_goals_status_target_at ON goals(status,target_at)")
            // Nullable columns preserve existing ciphertext, bindings, identities and unknown associations.
            db.execSQL("ALTER TABLE tasks ADD COLUMN project_id TEXT REFERENCES projects(id) ON DELETE SET NULL")
            db.execSQL("ALTER TABLE tasks ADD COLUMN goal_id TEXT REFERENCES goals(id) ON DELETE SET NULL")
            db.execSQL("CREATE INDEX index_tasks_project_id_status ON tasks(project_id,status)")
            db.execSQL("CREATE INDEX index_tasks_goal_id_status ON tasks(goal_id,status)")
            RecordConstraints.installPlanning(db)
        }
    }
}

private const val PREFERENCE_VERSION = 4
private const val PLANNING_VERSION = 5
