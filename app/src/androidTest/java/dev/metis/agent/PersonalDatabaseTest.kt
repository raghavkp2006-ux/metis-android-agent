package dev.metis.agent

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.FieldCipher
import dev.metis.agent.data.storage.KeystoreFieldCipher
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalDatabaseTest {
    @get:Rule
    val schemaHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PersonalDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "persistence-test-${UUID.randomUUID()}.db"
    private val alias = "metis.test.${UUID.randomUUID()}"
    private val cipher = KeystoreFieldCipher(alias)
    private var database = open()
    private var repository = LocalPersonalRepository(database, cipher)

    private fun open() = Room.databaseBuilder(context, PersonalDatabase::class.java, name)
        .addCallback(RecordConstraints).build()

    @After
    fun cleanup() {
        database.close()
        context.deleteDatabase(name)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }

    @Test
    fun encryptedRecordsSurviveReopenAndForeignKeysDetachDeletedTasks() = runBlocking {
        val task = SavedTask("Synthetic private task", notes = "Synthetic private notes", dueAt = 10, dueZoneId = "UTC")
        val schedule = SavedSchedule("Synthetic block", 10, 20, "UTC", "Synthetic reason", taskId = task.metadata.id)
        val memory = SavedMemory("Synthetic explicit memory")
        repository.saveTask(task)
        repository.saveSchedule(schedule)
        repository.saveMemory(memory)
        val ciphertext = requireNotNull(database.records().task(task.metadata.id)).title
        assertFalse(String(ciphertext, Charsets.UTF_8).contains(task.title))
        database.close()
        val diskText = String(context.getDatabasePath(name).readBytes(), Charsets.ISO_8859_1)
        listOf(task.title, requireNotNull(task.notes), schedule.title, schedule.reason, memory.content).forEach {
            assertFalse("Personal text must not appear in the persisted database", diskText.contains(it))
        }
        database = open()
        repository = LocalPersonalRepository(database, cipher)
        assertEquals(task, repository.observeTasks().first().single())
        assertEquals(schedule, repository.observeSchedules().first().single())
        assertEquals(memory, repository.observeMemories().first().single())
        repository.deleteTask(task.metadata.id, 0)
        assertTrue(repository.observeTasks().first().isEmpty())
        assertNull(repository.observeSchedules().first().single().taskId)
        assertEquals(memory, repository.observeMemories().first().single())
    }

    @Test
    fun staleWritesFailAndQueriesUseStableOrdering() = runBlocking {
        val late = SavedTask("Late", dueAt = 20, dueZoneId = "UTC")
        val early = SavedTask("Early", dueAt = 10, dueZoneId = "UTC")
        val undated = SavedTask("Undated")
        listOf(late, undated, early).forEach { repository.saveTask(it) }
        assertEquals(listOf("Early", "Late", "Undated"), repository.observeTasks().first().map { it.title })
        repository.saveTask(early.copy(title = "Updated"))
        assertEquals(1L, repository.observeTasks().first().first().metadata.revision)
        assertThrows(RevisionConflictException::class.java) { runBlocking { repository.saveTask(early) } }
        assertThrows(RevisionConflictException::class.java) { runBlocking { repository.deleteTask(early.metadata.id, 0) } }
        assertEquals("Updated", repository.observeTasks().first().first().title)
        assertThrows(Exception::class.java) {
            runBlocking { repository.saveSchedule(SavedSchedule("Orphan", 1, 2, "UTC", "Reason", taskId = UUID.randomUUID().toString())) }
        }
        assertTrue(repository.observeSchedules().first().isEmpty())
    }

    @Test
    fun invalidSqlIsRejectedByConstraints() = runBlocking {
        val task = SavedTask("Synthetic task")
        repository.saveTask(task)
        val sql = database.openHelper.writableDatabase
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE tasks SET priority = 99") }
        val memory = SavedMemory("Synthetic memory")
        repository.saveMemory(memory)
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE memories SET confidence = 2") }
        val schedule = SavedSchedule("Synthetic schedule", 1, 2, "UTC", "Reason")
        repository.saveSchedule(schedule)
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE schedule_blocks SET end_at = start_at") }
        assertEquals(task, repository.observeTasks().first().single())
    }

    @Test
    fun encryptionFailureRollsBackAndKeyLossDoesNotResetStorage() = runBlocking {
        val failedCipher = object : FieldCipher {
            override fun encrypt(value: String, binding: String): ByteArray = error("Synthetic encryption failure")
            override fun decrypt(value: ByteArray, binding: String): String = error("Synthetic decryption failure")
        }
        val failing = LocalPersonalRepository(database, failedCipher)
        assertThrows(IllegalStateException::class.java) { runBlocking { failing.saveTask(SavedTask("Synthetic")) } }
        assertEquals(0, database.records().recordCount())
        val memory = SavedMemory("Synthetic secret")
        repository.saveMemory(memory)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.observeMemories().first() } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.saveTask(SavedTask("Blocked write")) } }
        assertNull(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null))
        assertNotNull(database.records().memory(memory.metadata.id))
        assertEquals(1, database.records().recordCount())
    }

    @Test
    fun gcmUsesFreshNoncesAndRejectsTamperingOrDifferentBindings() {
        val original = cipher.encrypt("Synthetic secret", "memories/id/content")
        val second = cipher.encrypt("Synthetic secret", "memories/id/content")
        assertFalse(original.contentEquals(second))
        assertEquals("Synthetic secret", cipher.decrypt(original, "memories/id/content"))
        assertThrows(Exception::class.java) { cipher.decrypt(original, "tasks/id/title") }
        original[original.lastIndex] = (original.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { cipher.decrypt(original, "memories/id/content") }
    }

    @Test
    fun exportedV1SchemaMatchesRoomBaseline() {
        val schemaName = "schema-test-${UUID.randomUUID()}.db"
        try {
            schemaHelper.createDatabase(schemaName, 1).close()
            schemaHelper.runMigrationsAndValidate(schemaName, 1, true).close()
        } finally {
            context.deleteDatabase(schemaName)
        }
    }

    @Test
    fun memoryAndScheduleUpdatesAndDeletesPreserveRevisions() = runBlocking {
        val memory = SavedMemory("Original memory", metadata = RecordMetadata(createdAt = 1))
        val schedule = SavedSchedule("Original block", 10, 20, "UTC", "Reason")
        repository.saveMemory(memory)
        repository.saveSchedule(schedule)
        repository.saveMemory(memory.copy(content = "Updated memory"))
        repository.saveSchedule(schedule.copy(startAt = 11))
        val savedMemory = repository.observeMemories().first().single()
        val savedSchedule = repository.observeSchedules().first().single()
        assertEquals("Updated memory", savedMemory.content)
        assertEquals(1L, savedMemory.metadata.revision)
        assertEquals(1L, savedSchedule.metadata.revision)
        repository.deleteMemory(savedMemory.metadata.id, savedMemory.metadata.revision)
        repository.deleteSchedule(savedSchedule.metadata.id, savedSchedule.metadata.revision)
        assertEquals(0, database.records().recordCount())
    }
}
