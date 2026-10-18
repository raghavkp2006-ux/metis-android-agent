package dev.metis.agent.data.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: no v6 row, index, trigger, ciphertext binding or inferred habit is rewritten. */
object DisciplineMigration {
    val FROM_6_TO_7: Migration = object : Migration(FOUNDATION_SCHEMA_VERSION, DISCIPLINE_SCHEMA_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE habit_definitions (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                    revision INTEGER NOT NULL, name BLOB NOT NULL, description BLOB, cadence INTEGER NOT NULL,
                    active INTEGER NOT NULL, start_local_date TEXT NOT NULL, archived_at TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_habit_definitions_start_local_date_archived_at " +
                "ON habit_definitions(start_local_date, archived_at)")
            db.execSQL("""
                CREATE TABLE daily_checkins (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                    revision INTEGER NOT NULL, local_date TEXT NOT NULL, zone_id TEXT NOT NULL, mood BLOB, note BLOB
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX index_daily_checkins_local_date ON daily_checkins(local_date)")
            db.execSQL("""
                CREATE TABLE habit_ticks (
                    id TEXT NOT NULL PRIMARY KEY, checkin_id TEXT NOT NULL, habit_id TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    FOREIGN KEY(checkin_id) REFERENCES daily_checkins(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(habit_id) REFERENCES habit_definitions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX index_habit_ticks_checkin_id_habit_id ON habit_ticks(checkin_id, habit_id)")
            db.execSQL("CREATE INDEX index_habit_ticks_habit_id ON habit_ticks(habit_id)")
            installConstraints(db)
        }
    }

    internal fun installConstraints(db: SupportSQLiteDatabase) {
        install(db, "habit_definitions", """
            NEW.revision < 0 OR NEW.updated_at < NEW.created_at OR NEW.cadence NOT BETWEEN 0 AND 127 OR
            NEW.active NOT IN (0,1) OR (NEW.active = 1) != (NEW.archived_at IS NULL) OR
            ${invalidDate("NEW.start_local_date")} OR
            (NEW.archived_at IS NOT NULL AND (${invalidDate("NEW.archived_at")} OR
                NEW.archived_at <= NEW.start_local_date))
        """.trimIndent())
        install(db, "daily_checkins", "NEW.revision < 0 OR NEW.updated_at < NEW.created_at OR " +
            invalidDate("NEW.local_date"))
        install(db, "habit_ticks", "NEW.completed NOT IN (0,1)")
        db.execSQL("""
            CREATE TRIGGER habit_definition_window_immutable BEFORE UPDATE ON habit_definitions
            WHEN NEW.start_local_date != OLD.start_local_date OR NEW.cadence != OLD.cadence OR
                (OLD.archived_at IS NOT NULL AND (NEW.archived_at IS NULL OR NEW.archived_at != OLD.archived_at)) OR
                (NEW.archived_at IS NOT NULL AND EXISTS (
                    SELECT 1 FROM habit_ticks t JOIN daily_checkins c ON c.id = t.checkin_id
                    WHERE t.habit_id = OLD.id AND c.local_date >= NEW.archived_at))
            BEGIN SELECT RAISE(ABORT, 'Immutable habit window'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER checkin_day_immutable BEFORE UPDATE ON daily_checkins
            WHEN NEW.local_date != OLD.local_date OR NEW.zone_id != OLD.zone_id
            BEGIN SELECT RAISE(ABORT, 'Immutable check-in day'); END
        """.trimIndent())
    }

    private fun invalidDate(column: String) = """
        (length($column) != 10 OR $column NOT GLOB
        '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' OR substr($column,1,4) = '0000' OR
        date($column, '+0 days') IS NULL OR date($column, '+0 days') != $column)
    """.trimIndent()

    private fun install(db: SupportSQLiteDatabase, table: String, condition: String) {
        listOf("INSERT", "UPDATE").forEach { operation ->
            db.execSQL("""
                CREATE TRIGGER ${table}_${operation.lowercase()}_integrity BEFORE $operation ON $table
                WHEN $condition BEGIN SELECT RAISE(ABORT, 'Invalid discipline record'); END
            """.trimIndent())
        }
    }
}

private const val FOUNDATION_SCHEMA_VERSION = 6
private const val DISCIPLINE_SCHEMA_VERSION = 7
