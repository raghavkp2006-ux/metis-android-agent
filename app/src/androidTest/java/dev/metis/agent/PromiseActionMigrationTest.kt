package dev.metis.agent

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.PromiseActionMigration
import dev.metis.agent.domain.agent.ActionType
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PromiseActionMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PersonalDatabase::class.java)
    private val fixture = StorageTestFixture()
    @After fun cleanup() = fixture.close()

    @Test fun v7RowsRemainValidAndOnlyActionTriggersChange(): Unit {
        val ids = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        lateinit var before: List<List<String>>
        lateinit var oldAudit: List<List<String>>
        helper.createDatabase(fixture.name, 7).use { db ->
            listOf("INSERT", "UPDATE").forEach { operation -> db.execSQL("""
                CREATE TRIGGER action_runs_${operation.lowercase()}_integrity BEFORE $operation ON action_runs
                WHEN NEW.action_type NOT IN ('TASK','CREATE_REMINDER')
                BEGIN SELECT RAISE(ABORT, 'Invalid foundation record'); END
            """.trimIndent()) }
            insert(db, ids[0], "TASK")
            insert(db, ids[1], "CREATE_REMINDER", completed = true)
            val auditId = UUID.randomUUID().toString()
            db.execSQL("""
                INSERT INTO action_audit(id,action_run_id,timestamp,policy_version,autonomy_level,
                    permission_snapshot_json,decision,reason,evidence,confirmation_id)
                VALUES(?,?,11,'LEGACY_V7',1,?,'ALLOW',?,?,?)
            """.trimIndent(), arrayOf(auditId, ids[1],
                fixture.cipher.encrypt("{}", "action_audit/$auditId/permission_snapshot_json"),
                fixture.cipher.encrypt("old receipt allowed", "action_audit/$auditId/reason"),
                fixture.cipher.encrypt("{}", "action_audit/$auditId/evidence"), UUID.randomUUID().toString()))
            before = rows(db)
            oldAudit = audits(db)
            assertThrows(Exception::class.java) { insert(db, UUID.randomUUID().toString(), "CREATE_PROMISE") }
        }
        helper.runMigrationsAndValidate(fixture.name, 8, true, PromiseActionMigration.FROM_7_TO_8).use { db ->
            assertEquals(before, rows(db))
            assertEquals(oldAudit, audits(db))
            assertTrue(db.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
            db.execSQL("UPDATE action_runs SET revision = 1 WHERE id = ?", arrayOf(ids[0]))
            assertEquals(1L, db.query("SELECT revision FROM action_runs WHERE id = ?", arrayOf(ids[0])).use {
                it.moveToFirst(); it.getLong(0)
            })
            insert(db, UUID.randomUUID().toString(), "CREATE_PROMISE")
            assertThrows(Exception::class.java) { insert(db, UUID.randomUUID().toString(), "UNKNOWN_ACTION") }
            val fresh = StorageTestFixture()
            try {
                assertEquals(triggers(fresh.database.openHelper.writableDatabase), triggers(db))
            } finally { fresh.close() }
            assertEquals(2, triggers(db).size)
            assertTrue(triggers(db).all { "'CREATE_PROMISE'" in it })
        }
        runBlocking {
            val oldRuns = fixture.repository.foundation.actionRun.observe().first()
                .filter { it.metadata.id in ids }
            assertEquals(2, oldRuns.size)
            assertEquals("PENDING", oldRuns.single { it.metadata.id == ids[0] }.status)
            val receipt = oldRuns.single { it.metadata.id == ids[1] }
            assertEquals("SUCCEEDED", receipt.status)
            assertTrue(requireNotNull(receipt.receipt).contains("id"))
            assertEquals("old receipt allowed", fixture.repository.foundation.actionAudit.observe().first()
                .single().reason)
        }
    }

    @Test fun freshV8IntegrityAcceptsEveryProtocolActionAndRejectsUnknown() {
        val db = fixture.database.openHelper.writableDatabase
        ActionType.entries.forEach { insert(db, UUID.randomUUID().toString(), it.name) }
        assertEquals(ActionType.entries.size.toLong(), db.query("SELECT COUNT(*) FROM action_runs").use {
            it.moveToFirst(); it.getLong(0)
        })
        assertThrows(Exception::class.java) { insert(db, UUID.randomUUID().toString(), "UNKNOWN_ACTION") }
    }

    private fun insert(db: SupportSQLiteDatabase, id: String, type: String, completed: Boolean = false) {
        db.execSQL("""
            INSERT INTO action_runs(id,created_at,updated_at,revision,request_id,proposal_id,idempotency_key,
                action_type,payload,risk,status,started_at,verification,finished_at,receipt,safe_error_code,
                entity_type,entity_id)
            VALUES(?,10,10,0,?,?,?,?,?,'MEDIUM',?,10,?,?,?,NULL,NULL,NULL)
        """.trimIndent(), arrayOf(id, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), type, fixture.cipher.encrypt("{}", "action_runs/$id/payload"),
            if (completed) "SUCCEEDED" else "PENDING",
            if (completed) "VERIFIED_LOCAL" else "UNVERIFIED",
            if (completed) 11L else null,
            if (completed) fixture.cipher.encrypt("{\"id\":\"${UUID.randomUUID()}\"}",
                "action_runs/$id/receipt") else null))
    }

    private fun rows(db: SupportSQLiteDatabase): List<List<String>> =
        db.query("SELECT id,action_type,status,hex(payload),hex(receipt) FROM action_runs ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0..4).map { cursor.getString(it).orEmpty() }) }
        }

    private fun audits(db: SupportSQLiteDatabase): List<List<String>> =
        db.query("SELECT id,action_run_id,decision,hex(reason) FROM action_audit ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0..3).map(cursor::getString)) }
        }

    private fun triggers(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT sql FROM sqlite_master WHERE type='trigger' AND name LIKE 'action_runs_%_integrity' " +
            "ORDER BY name").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
}
