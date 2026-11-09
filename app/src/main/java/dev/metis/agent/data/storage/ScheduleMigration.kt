@file:Suppress("MaxLineLength") // SQL index names exceed line limit

package dev.metis.agent.data.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ScheduleMigration {
    const val OLD_VERSION = 8
    const val NEW_VERSION = 9

    val FROM_8_TO_9: Migration = object : Migration(OLD_VERSION, NEW_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `timetable_blocks` (
                    `id` TEXT NOT NULL,
                    `weekday` INTEGER NOT NULL,
                    `start_min` INTEGER NOT NULL,
                    `end_min` INTEGER NOT NULL,
                    `title` BLOB NOT NULL,
                    `kind` TEXT NOT NULL,
                    `fixed` INTEGER NOT NULL,
                    `tag` BLOB,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_timetable_blocks_weekday` ON `timetable_blocks` (`weekday`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_timetable_blocks_fixed` ON `timetable_blocks` (`fixed`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `routine_rules` (
                    `id` TEXT NOT NULL,
                    `key` TEXT NOT NULL,
                    `window_start_min` INTEGER NOT NULL,
                    `window_end_min` INTEGER NOT NULL,
                    `weekdays_mask` INTEGER NOT NULL,
                    `hardness` TEXT NOT NULL,
                    `note` BLOB,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_rules_key` ON `routine_rules` (`key`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `timetable_blocks_backup` (
                    `id` TEXT NOT NULL,
                    `weekday` INTEGER NOT NULL,
                    `start_min` INTEGER NOT NULL,
                    `end_min` INTEGER NOT NULL,
                    `title` BLOB NOT NULL,
                    `kind` TEXT NOT NULL,
                    `fixed` INTEGER NOT NULL,
                    `tag` BLOB,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_timetable_blocks_backup_weekday` ON `timetable_blocks_backup` (`weekday`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_timetable_blocks_backup_fixed` ON `timetable_blocks_backup` (`fixed`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `routine_rules_backup` (
                    `id` TEXT NOT NULL,
                    `key` TEXT NOT NULL,
                    `window_start_min` INTEGER NOT NULL,
                    `window_end_min` INTEGER NOT NULL,
                    `weekdays_mask` INTEGER NOT NULL,
                    `hardness` TEXT NOT NULL,
                    `note` BLOB,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_rules_backup_key` ON `routine_rules_backup` (`key`)")

            installConstraints(db)
        }
    }

    internal fun installConstraints(db: SupportSQLiteDatabase) {
        install(db, "timetable_blocks", """
            NEW.weekday NOT BETWEEN 1 AND 7 OR
            NEW.start_min NOT BETWEEN 0 AND 1439 OR
            NEW.end_min NOT BETWEEN 1 AND 1440 OR
            NEW.end_min <= NEW.start_min OR
            NEW.kind NOT IN ('CLASS','LAB','ROUTINE','FREE') OR
            NEW.fixed NOT IN (0,1)
        """.trimIndent())

        install(db, "routine_rules", """
            NEW.window_start_min NOT BETWEEN 0 AND 1439 OR
            NEW.window_end_min NOT BETWEEN 1 AND 1440 OR
            NEW.window_end_min <= NEW.window_start_min OR
            NEW.weekdays_mask NOT BETWEEN 1 AND 127 OR
            NEW.hardness NOT IN ('SOFT','FIRM')
        """.trimIndent())
    }

    private fun install(db: SupportSQLiteDatabase, table: String, condition: String) {
        listOf("INSERT", "UPDATE").forEach { operation ->
            db.execSQL("DROP TRIGGER IF EXISTS ${table}_${operation.lowercase()}_integrity")
            db.execSQL("""
                CREATE TRIGGER ${table}_${operation.lowercase()}_integrity BEFORE $operation ON $table
                WHEN $condition
                BEGIN SELECT RAISE(ABORT, 'Invalid timetable record'); END
            """.trimIndent())
        }
    }
}

