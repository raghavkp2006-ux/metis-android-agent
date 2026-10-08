package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.DebugStorageInspector
import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedPreference
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugStorageInspectionTest {
    private val fixture = StorageTestFixture()
    private val inspector = DebugStorageInspector(fixture.database)

    @After fun cleanup() = fixture.close()

    @Test
    fun aggregateSnapshotDoesNotDecryptOrReplaceMissingKey(): Unit = runBlocking {
        val task = SavedTask("Synthetic inspection task")
        val prerequisite = SavedTask("Synthetic prerequisite")
        fixture.repository.saveTask(task)
        fixture.repository.saveTask(prerequisite)
        fixture.repository.saveSchedule(
            SavedSchedule("Synthetic block", 10, 20, "UTC", "Synthetic reason", taskId = task.metadata.id),
        )
        fixture.repository.saveMemory(SavedMemory("Synthetic inspection memory"))
        fixture.repository.dependencies.saveDependency(SavedTaskDependency(task.metadata.id, prerequisite.metadata.id))
        fixture.repository.preferences.savePreference(
            SavedPreference(PreferenceKey.FOCUS_BLOCK_MINUTES, PreferenceValue.FocusMinutes(40)),
        )
        val decryptions = fixture.cipher.decryptions
        val snapshot = inspector.inspect()
        assertEquals(5, snapshot.schemaVersion)
        assertEquals(mapOf("tasks" to 2L, "schedule_blocks" to 1L, "memories" to 1L,
            "task_dependencies" to 1L, "preferences" to 1L, "projects" to 0L, "goals" to 0L), snapshot.rowCounts)
        assertTrue(snapshot.foreignKeysEnabled)
        assertTrue(snapshot.foreignKeysValid)
        assertTrue(snapshot.quickCheckPassed)
        assertEquals(decryptions, fixture.cipher.decryptions)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(fixture.alias) }
        assertEquals(snapshot, inspector.inspect())
        assertEquals(decryptions, fixture.cipher.decryptions)
        assertNull(keyStore.getKey(fixture.alias, null))
    }

    @Test
    fun detectsDanglingForeignKeyWithoutRepairingOrDecrypting(): Unit = runBlocking {
        val task = SavedTask("Synthetic inspection task")
        val prerequisite = SavedTask("Synthetic prerequisite")
        fixture.repository.saveTask(task)
        fixture.repository.saveTask(prerequisite)
        val edge = SavedTaskDependency(task.metadata.id, prerequisite.metadata.id)
        fixture.repository.dependencies.saveDependency(edge)
        withContext(Dispatchers.IO) {
            val connection = fixture.database.openHelper.writableDatabase
            connection.execSQL("PRAGMA foreign_keys = OFF")
            try {
                connection.execSQL("UPDATE task_dependencies SET depends_on_task_id = ? WHERE id = ?",
                    arrayOf(UUID.randomUUID().toString(), edge.metadata.id))
            } finally { connection.execSQL("PRAGMA foreign_keys = ON") }
        }
        val decryptions = fixture.cipher.decryptions
        val snapshot = inspector.inspect()
        assertTrue(snapshot.foreignKeysEnabled)
        assertFalse(snapshot.foreignKeysValid)
        assertTrue(snapshot.quickCheckPassed)
        assertEquals(1L, snapshot.rowCounts["task_dependencies"])
        assertEquals(snapshot, inspector.inspect())
        assertEquals(decryptions, fixture.cipher.decryptions)
    }
}
