package dev.metis.agent

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.PersonalMigrations
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import dev.metis.agent.domain.storage.MemoryEntityType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PersonalDatabase::class.java)
    private val fixture = StorageTestFixture()

    @After fun cleanup() = fixture.close()

    @Test
    fun migrationPreservesEncryptedRecordsMetadataAndTaskLinks(): Unit = runBlocking {
        val original = seedV1()
        helper.runMigrationsAndValidate(
            fixture.name, 3, true, PersonalMigrations.FROM_1_TO_2, PersonalMigrations.FROM_2_TO_3,
        ).close()
        val repository = fixture.repository
        assertEquals(original.task, repository.observeTasks().first().single())
        assertEquals(original.schedule, repository.observeSchedules().first().single())
        assertEquals(original.memory, repository.observeMemories().first().single())
        assertTrue(original.taskTitle.contentEquals(requireNotNull(fixture.database.records().task(original.task.metadata.id)).title))
        assertTrue(original.memoryContent.contentEquals(requireNotNull(fixture.database.records().memory(original.memory.metadata.id)).content))
        val sql = fixture.database.openHelper.writableDatabase
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE memories SET entity_type = 'TASK'") }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE tasks SET priority = 99") }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE schedule_blocks SET end_at = start_at") }
        repository.saveTask(original.task.copy(title = "Synthetic migrated update"))
        assertEquals(original.task.metadata.revision + 1, repository.observeTasks().first().single().metadata.revision)
        repository.deleteTask(original.task.metadata.id, original.task.metadata.revision + 1)
        assertEquals(null, repository.observeSchedules().first().single().taskId)
    }

    @Test
    fun freshV3SchemaValidatesAgainstExport() {
        helper.createDatabase(fixture.name, 3).close()
        helper.runMigrationsAndValidate(fixture.name, 3, true).close()
        assertEquals(3, fixture.database.openHelper.readableDatabase.version)
    }

    @Test
    fun v2MigrationPreservesLinkedEncryptedMemoryAndInstallsDependencyConstraints(): Unit = runBlocking {
        val original = seedV1()
        helper.runMigrationsAndValidate(fixture.name, 2, true, PersonalMigrations.FROM_1_TO_2).use { db ->
            db.execSQL("UPDATE memories SET entity_type = 'TASK', entity_id = ?", arrayOf(original.task.metadata.id))
        }
        helper.runMigrationsAndValidate(fixture.name, 3, true, PersonalMigrations.FROM_2_TO_3).close()
        assertEquals(original.memory.copy(entityType = MemoryEntityType.TASK, entityId = original.task.metadata.id),
            fixture.repository.observeMemories().first().single())
        assertTrue(original.memoryContent.contentEquals(
            requireNotNull(fixture.database.records().memory(original.memory.metadata.id)).content,
        ))
        assertEquals(emptyList<SavedTaskDependency>(), fixture.repository.dependencies.observeDependencies().first())
        val prerequisite = SavedTask("Synthetic upgraded prerequisite")
        fixture.repository.saveTask(prerequisite)
        val edge = SavedTaskDependency(original.task.metadata.id, prerequisite.metadata.id)
        fixture.repository.dependencies.saveDependency(edge)
        assertThrows(Exception::class.java) { fixture.database.openHelper.writableDatabase.execSQL(
            "UPDATE task_dependencies SET depends_on_task_id = task_id",
        ) }
        fixture.repository.deleteTask(prerequisite.metadata.id, 0)
        assertTrue(fixture.repository.dependencies.observeDependencies().first().isEmpty())
        assertEquals(original.task, fixture.repository.observeTasks().first().single())
    }

    @Test
    fun missingMigrationFailsWithoutDeletingV1Data() {
        val original = seedV1()
        val unconfigured = Room.databaseBuilder(fixture.context, PersonalDatabase::class.java, fixture.name).build()
        try {
            assertThrows(IllegalStateException::class.java) { unconfigured.openHelper.writableDatabase }
        } finally {
            unconfigured.close()
        }
        SQLiteDatabase.openDatabase(
            fixture.context.getDatabasePath(fixture.name).path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { old ->
            assertEquals(1, old.version)
            old.rawQuery("SELECT content FROM memories", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(original.memoryContent.contentEquals(cursor.getBlob(0)))
            }
        }
    }

    private fun seedV1(): V1Fixture {
        val task = SavedTask("Synthetic migrated task", metadata = RecordMetadata(createdAt = 10, updatedAt = 20, revision = 2))
        val schedule = SavedSchedule(
            "Synthetic migrated schedule", 100, 200, "UTC", "Synthetic migrated reason",
            taskId = task.metadata.id, metadata = RecordMetadata(createdAt = 10, updatedAt = 20, revision = 3),
        )
        val memory = SavedMemory(
            "Synthetic migrated memory", metadata = RecordMetadata(createdAt = 10, updatedAt = 20, revision = 4),
        )
        val taskTitle = fixture.cipher.encrypt(task.title, "tasks/${task.metadata.id}/title")
        val memoryContent = fixture.cipher.encrypt(memory.content, "memories/${memory.metadata.id}/content")
        helper.createDatabase(fixture.name, 1).use { db ->
            db.execSQL("""
                INSERT INTO tasks(id, created_at, updated_at, revision, title, notes,
                    due_at, due_zone_id, estimated_seconds, priority, status, completed_at)
                VALUES(?,10,20,2,?,NULL,NULL,NULL,NULL,0,'OPEN',NULL)
            """.trimIndent(), arrayOf(task.metadata.id, taskTitle))
            db.execSQL("""
                INSERT INTO schedule_blocks(id,created_at,updated_at,revision,plan_id,task_id,
                    title,start_at,end_at,zone_id,status,reason)
                VALUES(?,10,20,3,?,?,?,100,200,'UTC','PROPOSED',?)
            """.trimIndent(), arrayOf(
                schedule.metadata.id, schedule.planId, task.metadata.id,
                fixture.cipher.encrypt(schedule.title, "schedule_blocks/${schedule.metadata.id}/title"),
                fixture.cipher.encrypt(schedule.reason, "schedule_blocks/${schedule.metadata.id}/reason"),
            ))
            db.execSQL("""
                INSERT INTO memories(id,created_at,updated_at,revision,memory_type,origin,content,
                    importance,confidence,expires_at)
                VALUES(?,10,20,4,'SEMANTIC','EXPLICIT',?,0.5,1,NULL)
            """.trimIndent(), arrayOf(memory.metadata.id, memoryContent))
        }
        return V1Fixture(task, schedule, memory, taskTitle, memoryContent)
    }
}

private data class V1Fixture(
    val task: SavedTask, val schedule: SavedSchedule, val memory: SavedMemory,
    val taskTitle: ByteArray, val memoryContent: ByteArray,
)
