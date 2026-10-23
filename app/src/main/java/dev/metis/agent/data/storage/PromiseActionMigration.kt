package dev.metis.agent.data.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Replaces only the two action_runs integrity triggers; every v7 row and table stays intact. */
object PromiseActionMigration {
    private const val OLD_VERSION = 7
    private const val NEW_VERSION = 8
    val FROM_7_TO_8: Migration = object : Migration(OLD_VERSION, NEW_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            FoundationConstraints.installActionRuns(db)
        }
    }
}
