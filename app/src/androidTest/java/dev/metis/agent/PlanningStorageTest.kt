package dev.metis.agent

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.PlanningMigration
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedTask
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningStorageTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PersonalDatabase::class.java)
    private val fixture = StorageTestFixture()
    private val planning = fixture.repository.planning

    @After fun cleanup() = fixture.close()

    @Test
    fun encryptedPlanningRecordsAndTaskLinksSurviveReopen(): Unit = runBlocking {
        val project = SavedProject("Synthetic project", "Synthetic confidential project detail")
        val goal = SavedGoal("Synthetic goal", "Synthetic confidential goal detail", project.metadata.id, 100, 2)
        planning.saveProject(project)
        planning.saveGoal(goal)
        val task = SavedTask("Synthetic linked task", projectId = project.metadata.id, goalId = goal.metadata.id)
        fixture.repository.saveTask(task)
        val raw = requireNotNull(fixture.database.planning().project(project.metadata.id))
        assertFalse(String(raw.title, Charsets.UTF_8).contains(project.title))
        fixture.database.close()
        val diskText = String(fixture.context.getDatabasePath(fixture.name).readBytes(), Charsets.ISO_8859_1)
        assertFalse(diskText.contains(project.title))
        assertFalse(diskText.contains(requireNotNull(goal.description)))
        val reopened = Room.databaseBuilder(fixture.context, PersonalDatabase::class.java, fixture.name)
            .addCallback(RecordConstraints).build()
        try {
            val repository = LocalPersonalRepository(reopened, fixture.cipher)
            assertEquals(listOf(project), repository.planning.observeProjects().first())
            assertEquals(listOf(goal), repository.planning.observeGoals().first())
            assertEquals(listOf(task), repository.observeTasks().first())
        } finally { reopened.close() }
    }

    @Test
    fun revisionsAndDeletionKeepUnrelatedRowsAndDetachAssociations(): Unit = runBlocking {
        val project = SavedProject("Synthetic project")
        planning.saveProject(project)
        val goal = SavedGoal("Synthetic goal", projectId = project.metadata.id)
        planning.saveGoal(goal)
        val task = SavedTask("Synthetic task", projectId = project.metadata.id, goalId = goal.metadata.id)
        fixture.repository.saveTask(task)
        planning.saveProject(project.copy(title = "Synthetic updated project"))
        assertThrows(RevisionConflictException::class.java) { runBlocking { planning.saveProject(project) } }
        assertThrows(RevisionConflictException::class.java) { runBlocking { planning.deleteProject(project.metadata.id, 0) } }
        planning.deleteProject(project.metadata.id, 1)
        assertEquals(goal.copy(projectId = null), planning.observeGoals().first().single())
        assertEquals(task.copy(projectId = null), fixture.repository.observeTasks().first().single())
        planning.saveGoal(goal.copy(projectId = null, title = "Synthetic updated goal"))
        assertThrows(RevisionConflictException::class.java) { runBlocking { planning.saveGoal(goal.copy(projectId = null)) } }
        planning.deleteGoal(goal.metadata.id, 1)
        assertEquals(task.copy(projectId = null, goalId = null), fixture.repository.observeTasks().first().single())
    }

    @Test
    fun rejectsMissingAndConflictingLinksAndInvalidSqlWithoutPartialWrites(): Unit = runBlocking {
        val first = SavedProject("Synthetic first project")
        val second = SavedProject("Synthetic second project")
        planning.saveProject(first)
        planning.saveProject(second)
        val goal = SavedGoal("Synthetic goal", projectId = first.metadata.id)
        planning.saveGoal(goal)
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            fixture.repository.saveTask(SavedTask("Synthetic mismatch", projectId = second.metadata.id, goalId = goal.metadata.id))
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            planning.saveGoal(goal.copy(projectId = UUID.randomUUID().toString()))
        } }
        val task = SavedTask("Synthetic valid task", projectId = first.metadata.id, goalId = goal.metadata.id)
        fixture.repository.saveTask(task)
        assertThrows(IllegalStateException::class.java) { runBlocking { planning.saveGoal(goal.copy(projectId = second.metadata.id)) } }
        assertEquals(goal, planning.observeGoals().first().single())
        assertEquals(listOf(task), fixture.repository.observeTasks().first())
        val sql = fixture.database.openHelper.writableDatabase
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE goals SET priority = 10") }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE projects SET status = 'UNKNOWN'") }
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE tasks SET goal_id = ?", arrayOf(UUID.randomUUID().toString())) }
    }

    @Test
    fun keyLossInProjectOnlyOrGoalOnlyStorageCannotReplaceKey(): Unit = runBlocking {
        planning.saveProject(SavedProject("Synthetic project"))
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(fixture.alias) }
        assertThrows(Exception::class.java) { runBlocking { planning.observeProjects().first() } }
        assertThrows(Exception::class.java) { runBlocking { fixture.repository.saveTask(SavedTask("Synthetic blocked task")) } }
        assertNull(keyStore.getKey(fixture.alias, null))
        assertEquals(1, fixture.database.records().recordCount())
        StorageTestFixture().use { other ->
            other.repository.planning.saveGoal(SavedGoal("Synthetic goal"))
            val otherKeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(other.alias) }
            assertThrows(Exception::class.java) { runBlocking {
                other.repository.planning.saveProject(SavedProject("Synthetic blocked project"))
            } }
            assertNull(otherKeyStore.getKey(other.alias, null))
            assertEquals(1, other.database.records().recordCount())
        }
    }

    @Test
    fun v4UpgradePreservesCiphertextAndAddsNullAssociations(): Unit = runBlocking {
        val task = SavedTask("Synthetic v4 upgrade", metadata = RecordMetadata(createdAt = 10, updatedAt = 20, revision = 3))
        val encrypted = fixture.cipher.encrypt(task.title, "tasks/${task.metadata.id}/title")
        helper.createDatabase(fixture.name, 4).use { db ->
            db.execSQL("""
                INSERT INTO tasks(id,created_at,updated_at,revision,title,notes,due_at,due_zone_id,
                    estimated_seconds,priority,status,completed_at)
                VALUES(?,10,20,3,?,NULL,NULL,NULL,NULL,0,'OPEN',NULL)
            """.trimIndent(), arrayOf(task.metadata.id, encrypted))
        }
        helper.runMigrationsAndValidate(fixture.name, 5, true, PlanningMigration.FROM_4_TO_5).close()
        assertEquals(task, fixture.repository.observeTasks().first().single())
        assertTrue(encrypted.contentEquals(requireNotNull(fixture.database.records().task(task.metadata.id)).title))
        assertTrue(planning.observeProjects().first().isEmpty())
        assertTrue(planning.observeGoals().first().isEmpty())
        val project = SavedProject("Synthetic upgraded project")
        planning.saveProject(project)
        fixture.repository.saveTask(task.copy(projectId = project.metadata.id))
        planning.deleteProject(project.metadata.id, 0)
        assertNull(fixture.repository.observeTasks().first().single().projectId)
    }
}
