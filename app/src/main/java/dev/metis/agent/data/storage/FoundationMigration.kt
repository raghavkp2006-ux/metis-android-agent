package dev.metis.agent.data.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object FoundationMigration {
    val FROM_5_TO_6: Migration = object : Migration(PLANNING_VERSION, FOUNDATION_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            FoundationPeopleSchema.create(db)
            FoundationTimeSchema.create(db)
            FoundationLinksSchema.create(db)
            FoundationHistorySchema.create(db)
            FoundationAnalysisSchema.create(db)
            FoundationCoreLinks.migrate(db)
            RecordConstraints.rebuild(db)
            RecordConstraints.installDependencies(db)
            RecordConstraints.installPreferences(db)
            RecordConstraints.installPlanning(db)
            FoundationConstraints.install(db)
        }
    }
}

private const val PLANNING_VERSION = 5
private const val FOUNDATION_VERSION = 6
