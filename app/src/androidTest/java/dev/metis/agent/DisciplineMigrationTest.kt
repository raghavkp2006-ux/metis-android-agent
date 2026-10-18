package dev.metis.agent

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.DisciplineMigration
import dev.metis.agent.data.storage.FoundationConstraints
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordConstraints
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DisciplineMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PersonalDatabase::class.java)
    private val fixture = StorageTestFixture()
    private val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open(
        "dev.metis.agent.data.storage.PersonalDatabase/6.json").bufferedReader().use { it.readText() })
        .getJSONObject("database").getJSONArray("entities")
    private val tables = (0 until schema.length()).map { schema.getJSONObject(it).getString("tableName") }
    @After fun cleanup() = fixture.close()

    @Test fun populatedV6PreservesEveryRowCiphertextLinkAndExistingSchemaObject(): Unit = runBlocking {
        lateinit var before: Map<String, List<List<String?>>>
        lateinit var objects: Map<String, String>
        helper.createDatabase(fixture.name, 6).use { db ->
            installV6Constraints(db)
            populate(db)
            before = snapshot(db)
            objects = oldSchemaObjects(db)
            assertEquals(22, before.size)
            assertEquals(23, before.values.sumOf { it.size })
            assertTrue(before.values.all { it.isNotEmpty() })
            assertFalse(db.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
        }
        helper.runMigrationsAndValidate(fixture.name, 7, true, DisciplineMigration.FROM_6_TO_7).use { db ->
            assertEquals(before, snapshot(db))
            assertEquals(objects, oldSchemaObjects(db))
            assertFalse(db.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
            listOf("habit_definitions", "daily_checkins", "habit_ticks").forEach { table ->
                assertEquals(0L, db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getLong(0) })
            }
        }
        assertEquals(7, fixture.database.openHelper.readableDatabase.version)
        val tasks = fixture.repository.observeTasks().first()
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.title == "Synthetic v6 private text" })
        assertEquals(1, fixture.repository.dependencies.observeDependencies().first().size)
        assertEquals(1, fixture.repository.preferences.observePreferences().first().size)
        val legacyHabit = requireNotNull(fixture.database.habit().find(id("habits")))
        assertTrue(legacyHabit.consentRequired)
        assertEquals("Synthetic v6 private text", fixture.cipher.decrypt(legacyHabit.name,
            "habits/${legacyHabit.metadata.id}/name"))
    }

    @Test fun freshV7ExportValidatesAndRejectsInvalidDatesAndArchiveWindows() {
        helper.createDatabase(fixture.name, 7).close()
        helper.runMigrationsAndValidate(fixture.name, 7, true).close()
        // A normal Room open installs creation callbacks; the helper alone does not run them.
        val other = StorageTestFixture()
        try {
            val sql = other.database.openHelper.writableDatabase
            val binding = "habit_definitions/${id("new")}/name"
            val name = other.cipher.encrypt("Synthetic v7 habit", binding)
            org.junit.Assert.assertThrows(Exception::class.java) { sql.execSQL("""
                INSERT INTO habit_definitions(id,created_at,updated_at,revision,name,description,cadence,
                    active,start_local_date,archived_at) VALUES(?,10,10,0,?,NULL,0,1,'2026-02-30',NULL)
            """.trimIndent(), arrayOf(id("new"), name)) }
            org.junit.Assert.assertThrows(Exception::class.java) { sql.execSQL("""
                INSERT INTO habit_definitions(id,created_at,updated_at,revision,name,description,cadence,
                    active,start_local_date,archived_at) VALUES(?,10,10,0,?,NULL,0,0,'2026-03-10','2026-03-10')
            """.trimIndent(), arrayOf(id("new"), name)) }
        } finally { other.close() }
    }

    private fun populate(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            (0 until schema.length()).forEach { index ->
                val entity = schema.getJSONObject(index)
                insert(db, entity, id(entity.getString("tableName")))
            }
            insert(db, schema.getJSONObject(tables.indexOf("tasks")), id("second_task"))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun insert(db: SupportSQLiteDatabase, entity: JSONObject, recordId: String) {
        val table = entity.getString("tableName")
        val fields = entity.getJSONArray("fields")
        val columns = (0 until fields.length()).map { fields.getJSONObject(it) }
        val foreign = entity.optJSONArray("foreignKeys")
        val links = (0 until (foreign?.length() ?: 0)).associate { index ->
            val link = requireNotNull(foreign).getJSONObject(index)
            link.getJSONArray("columns").getString(0) to id(link.getString("table"))
        }
        val values = columns.map { field ->
            val column = field.getString("columnName")
            when {
                column == "id" -> recordId
                column == "depends_on_task_id" -> id("second_task")
                column in links -> links[column]
                field.getString("affinity") == "BLOB" -> fixture.cipher.encrypt(
                    if (column == "typed_value") "40" else if (column in JSON_FIELDS) "{}"
                    else "Synthetic v6 private text", "$table/$recordId/$column")
                else -> scalar(table, column, field.optBoolean("notNull"))
            }
        }
        db.execSQL("INSERT INTO `$table` (${columns.joinToString { "`${it.getString("columnName")}`" }}) " +
            "VALUES (${columns.joinToString { "?" }})", values.toTypedArray())
    }

    private fun scalar(table: String, column: String, required: Boolean): Any? = when (column) {
        "status" -> STATUSES.getValue(table)
        "created_at", "started_at", "trigger_at", "timestamp", "generated_at", "consented_at",
        "observation_start", "computed_at", "source_watermark" -> 10L
        "updated_at", "observation_end", "end_at" -> 20L
        "expires_at" -> 30L
        "revision" -> 3L
        "active", "consent_required", "value_schema_version", "schema_version" -> 1L
        "priority", "autonomy_level", "behavioral_analysis_consent", "enabled" -> 0L
        "sample_count" -> 2L
        "planned_seconds" -> 60L
        "start_at" -> 10L
        "importance", "confidence", "score" -> 0.5
        "zone_id" -> "UTC"
        "local_date_time" -> "1970-01-01T00:00:00.010"
        "locale" -> "en-US"
        "memory_type" -> "SEMANTIC"
        "origin" -> "EXPLICIT"
        "kind" -> if (table == "relationships") "FRIEND" else "SYNTHETIC"
        "source" -> if (table == "preferences") "EXPLICIT" else "USER"
        "key" -> "FOCUS_BLOCK_MINUTES"
        "value_kind" -> "DURATION_MINUTES"
        "type" -> "TASK_CREATED"
        "precision" -> "APPROXIMATE"
        "scheduling_state" -> "PENDING"
        "outcome" -> "RUNNING"
        "action_type" -> "TASK"
        "risk" -> "LOW"
        "verification" -> "UNVERIFIED"
        "decision" -> "ALLOW"
        "method_version", "policy_version" -> "SYNTHETIC_V1"
        "recurrence_rule" -> if (table == "routines") "FREQ=DAILY" else null
        else -> if (required) id("$table/$column") else null
    }

    private fun snapshot(db: SupportSQLiteDatabase) = tables.associateWith { table ->
        db.query("SELECT * FROM `$table` ORDER BY id").use { cursor -> buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { column -> when (cursor.getType(column)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_BLOB -> "BLOB:" + Base64.getEncoder().encodeToString(cursor.getBlob(column))
                else -> "${cursor.getType(column)}:" + cursor.getString(column)
            } })
        } }
    }

    private fun oldSchemaObjects(db: SupportSQLiteDatabase) = db.query(
        "SELECT name, tbl_name, sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY name").use { cursor ->
        buildMap { while (cursor.moveToNext()) if (cursor.getString(1) in tables) {
            put(cursor.getString(0), cursor.getString(2))
        } }
    }

    private fun installV6Constraints(db: SupportSQLiteDatabase) {
        RecordConstraints.rebuild(db)
        RecordConstraints.installDependencies(db)
        RecordConstraints.installPreferences(db)
        RecordConstraints.installPlanning(db)
        FoundationConstraints.install(db)
    }

    private fun id(label: String) = UUID.nameUUIDFromBytes("Synthetic v6 $label".toByteArray()).toString()

    private companion object {
        val JSON_FIELDS = setOf("definition", "payload", "metadata", "score_components_json", "evidence",
            "permission_snapshot_json")
        val STATUSES = mapOf("tasks" to "OPEN", "schedule_blocks" to "PROPOSED", "projects" to "ACTIVE",
            "goals" to "ACTIVE", "promises" to "OPEN", "action_runs" to "PENDING",
            "recommendations" to "SHOWN", "experiments" to "ACTIVE")
    }
}
