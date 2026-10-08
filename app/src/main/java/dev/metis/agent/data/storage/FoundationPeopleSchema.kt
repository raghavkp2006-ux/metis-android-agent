package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase

internal object FoundationPeopleSchema {
    fun create(db: SupportSQLiteDatabase) {
        createPerson(db)
        createRelationship(db)
        createUserProfile(db)
    }

    private fun createPerson(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE persons (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    display_name BLOB NOT NULL,
                    phone BLOB,
                    email BLOB,
                    contact_lookup_key BLOB
                )
            """.trimIndent())
    }
    
    private fun createRelationship(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE relationships (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    person_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    description BLOB,
                    FOREIGN KEY(person_id) REFERENCES persons(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX index_relationships_person_id_kind ON relationships(person_id,kind)")
    }
    
    private fun createUserProfile(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE user_profile (
                    id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL, revision INTEGER NOT NULL,
                    display_name BLOB NOT NULL,
                    zone_id TEXT NOT NULL,
                    locale TEXT NOT NULL,
                    autonomy_level INTEGER NOT NULL,
                    behavioral_analysis_consent INTEGER NOT NULL,
                    active INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX index_user_profile_active ON user_profile(active)")
    }
}
