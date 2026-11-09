package dev.metis.agent

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.ScheduleMigration
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScheduleMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PersonalDatabase::class.java)
    private val fixture = StorageTestFixture()
    @After fun cleanup() = fixture.close()

    @Test
    fun v8RowsRemainValidAndNewTimetableTablesInstalled(): Unit {
        val taskId = UUID.randomUUID().toString()
        val scheduleId = UUID.randomUUID().toString()
        val habitId = UUID.randomUUID().toString()

        helper.createDatabase(fixture.name, 8).use { db ->
            // Insert v8 task
            db.execSQL("""
                INSERT INTO tasks(id, created_at, updated_at, revision, title, notes, due_at, due_zone_id,
                    status, priority, completed_at, estimated_seconds)
                VALUES(?, 100, 100, 0, ?, ?, NULL, NULL, 'OPEN', 1, NULL, 3600)
            """.trimIndent(), arrayOf(taskId,
                fixture.cipher.encrypt("Test task v8", "tasks/$taskId/title"),
                fixture.cipher.encrypt("Test notes v8", "tasks/$taskId/notes")))

            // Insert v8 habit definition
            db.execSQL("""
                INSERT INTO habit_definitions(id, created_at, updated_at, revision, name, description,
                    cadence, active, start_local_date, archived_at)
                VALUES(?, 100, 100, 0, ?, NULL, 127, 1, '2026-01-01', NULL)
            """.trimIndent(), arrayOf(habitId,
                fixture.cipher.encrypt("Daily Reading", "habit_definitions/$habitId/name")))
        }

        // Migrate to version 9 with ScheduleMigration.FROM_8_TO_9
        helper.runMigrationsAndValidate(fixture.name, 9, true, ScheduleMigration.FROM_8_TO_9).use { db ->
            // Verify v8 data preserved
            val taskCount = db.query("SELECT COUNT(*) FROM tasks WHERE id = ?", arrayOf(taskId)).use {
                it.moveToFirst(); it.getInt(0)
            }
            assertEquals(1, taskCount)

            val habitCount = db.query("SELECT COUNT(*) FROM habit_definitions WHERE id = ?", arrayOf(habitId)).use {
                it.moveToFirst(); it.getInt(0)
            }
            assertEquals(1, habitCount)

            // Foreign keys intact
            assertTrue(db.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })

            // Test inserting valid timetable block
            val blockId = UUID.randomUUID().toString()
            db.execSQL("""
                INSERT INTO timetable_blocks(id, weekday, start_min, end_min, title, kind, fixed, tag)
                VALUES(?, 1, 540, 630, ?, 'CLASS', 1, NULL)
            """.trimIndent(), arrayOf(blockId,
                fixture.cipher.encrypt("Mathematics", "timetable_blocks/$blockId/title")))

            assertEquals(1, db.query("SELECT COUNT(*) FROM timetable_blocks WHERE id = ?", arrayOf(blockId)).use {
                it.moveToFirst(); it.getInt(0)
            })

            // Test inserting valid routine rule
            val ruleId = UUID.randomUUID().toString()
            db.execSQL("""
                INSERT INTO routine_rules(id, key, window_start_min, window_end_min, weekdays_mask, hardness, note)
                VALUES(?, 'gym', 1020, 1110, 127, 'SOFT', NULL)
            """.trimIndent(), arrayOf(ruleId))

            assertEquals(1, db.query("SELECT COUNT(*) FROM routine_rules WHERE id = ?", arrayOf(ruleId)).use {
                it.moveToFirst(); it.getInt(0)
            })

            // Test constraint trigger rejects end_min <= start_min
            val badBlockId = UUID.randomUUID().toString()
            assertThrows(Exception::class.java) {
                db.execSQL("""
                    INSERT INTO timetable_blocks(id, weekday, start_min, end_min, title, kind, fixed, tag)
                    VALUES(?, 1, 630, 540, ?, 'CLASS', 1, NULL)
                """.trimIndent(), arrayOf(badBlockId,
                    fixture.cipher.encrypt("Bad", "timetable_blocks/$badBlockId/title")))
            }

            // Test constraint trigger rejects unknown kind
            val badKindId = UUID.randomUUID().toString()
            assertThrows(Exception::class.java) {
                db.execSQL("""
                    INSERT INTO timetable_blocks(id, weekday, start_min, end_min, title, kind, fixed, tag)
                    VALUES(?, 1, 540, 630, ?, 'PARTY', 1, NULL)
                """.trimIndent(), arrayOf(badKindId,
                    fixture.cipher.encrypt("Bad", "timetable_blocks/$badKindId/title")))
            }

            // Test backup tables exist
            assertEquals(0, db.query("SELECT COUNT(*) FROM timetable_blocks_backup").use {
                it.moveToFirst(); it.getInt(0)
            })
            assertEquals(0, db.query("SELECT COUNT(*) FROM routine_rules_backup").use {
                it.moveToFirst(); it.getInt(0)
            })
        }
    }
}

