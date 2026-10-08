package dev.metis.agent.data.storage

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class StorageInspectionSnapshot(
    val schemaVersion: Int,
    val rowCounts: Map<String, Long>,
    val foreignKeysEnabled: Boolean,
    val foreignKeysValid: Boolean,
    val quickCheckPassed: Boolean,
)

/** Fixed aggregate queries only. No personal fields or encryption keys are accessed. */
internal class DebugStorageInspector(private val database: PersonalDatabase) {
    suspend fun inspect(): StorageInspectionSnapshot = withContext(Dispatchers.IO) {
        database.withTransaction {
            val connection = database.openHelper.writableDatabase
            val counts = TABLES.associateWith { table ->
                connection.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getLong(0)
                }
            }
            val enabled = connection.query("PRAGMA foreign_keys").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0) == 1
            }
            val valid = connection.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }
            // Discard diagnostic strings: they may include row identifiers or schema details.
            val quickCheck = connection.query("PRAGMA quick_check(1)").use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()
            }
            StorageInspectionSnapshot(connection.version, counts, enabled, valid, quickCheck)
        }
    }

    private companion object {
        val TABLES = listOf("tasks", "schedule_blocks", "memories", "task_dependencies", "preferences", "projects", "goals")
    }
}
