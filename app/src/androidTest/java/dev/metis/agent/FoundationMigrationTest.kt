package dev.metis.agent

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.DebugStorageInspector
import dev.metis.agent.data.storage.FoundationMigration
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PersonalDatabase::class.java)
    private val fixture = StorageTestFixture()
    @After fun cleanup() = fixture.close()

    @Test
    fun v5UpgradePreservesTaskProjectLinksCiphertextAndMetadata(): Unit = runBlocking {
        val project = SavedProject("Synthetic v5 project", metadata = RecordMetadata(createdAt = 10, revision = 2))
        val task = SavedTask("Synthetic v5 task", metadata = RecordMetadata(createdAt = 10, revision = 3),
            projectId = project.metadata.id)
        val projectTitle = fixture.cipher.encrypt(project.title, "projects/${project.metadata.id}/title")
        val taskTitle = fixture.cipher.encrypt(task.title, "tasks/${task.metadata.id}/title")
        helper.createDatabase(fixture.name, 5).use { db ->
            db.execSQL("INSERT INTO projects(id,created_at,updated_at,revision,title,description,status) " +
                "VALUES(?,10,10,2,?,NULL,'ACTIVE')", arrayOf(project.metadata.id, projectTitle))
            db.execSQL("""
                INSERT INTO tasks(id,created_at,updated_at,revision,title,notes,due_at,due_zone_id,
                    estimated_seconds,priority,status,completed_at,project_id,goal_id)
                VALUES(?,10,10,3,?,NULL,NULL,NULL,NULL,0,'OPEN',NULL,?,NULL)
            """.trimIndent(), arrayOf(task.metadata.id, taskTitle, project.metadata.id))
        }
        helper.runMigrationsAndValidate(fixture.name, 6, true, FoundationMigration.FROM_5_TO_6).close()
        assertEquals(task, fixture.repository.observeTasks().first().single())
        assertEquals(project, fixture.repository.planning.observeProjects().first().single())
        assertTrue(taskTitle.contentEquals(requireNotNull(fixture.database.records().task(task.metadata.id)).title))
        assertTrue(projectTitle.contentEquals(requireNotNull(fixture.database.planning().project(project.metadata.id)).title))
        val snapshot = DebugStorageInspector(fixture.database).inspect()
        assertEquals(22, snapshot.rowCounts.size)
        assertTrue(snapshot.foreignKeysEnabled && snapshot.foreignKeysValid && snapshot.quickCheckPassed)
        assertEquals(2L, snapshot.rowCounts.values.sum())
        fixture.repository.planning.deleteProject(project.metadata.id, 2)
        assertEquals(task.copy(projectId = null), fixture.repository.observeTasks().first().single())
    }

    @Test
    fun freshV6SchemaMatchesExportAndAllTablesStartEmpty(): Unit = runBlocking {
        helper.createDatabase(fixture.name, 6).close()
        helper.runMigrationsAndValidate(fixture.name, 6, true).close()
        assertEquals(6, fixture.database.openHelper.readableDatabase.version)
        val snapshot = DebugStorageInspector(fixture.database).inspect()
        assertEquals(22, snapshot.rowCounts.size)
        assertEquals(0L, snapshot.rowCounts.values.sum())
        assertTrue(snapshot.foreignKeysValid && snapshot.quickCheckPassed)
    }
}
