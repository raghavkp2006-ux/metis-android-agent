package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedTaskDependency
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDependencyTest {
    private val fixture = StorageTestFixture()
    private val dependencies = fixture.repository.dependencies

    @After fun cleanup() = fixture.close()

    @Test
    fun rejectsDuplicateMissingTaskAndCycleWithoutChangingGraph(): Unit = runBlocking {
        val tasks = saveTasks(3)
        val first = SavedTaskDependency(tasks[0].metadata.id, tasks[1].metadata.id)
        val second = SavedTaskDependency(tasks[1].metadata.id, tasks[2].metadata.id)
        dependencies.saveDependency(first)
        dependencies.saveDependency(second)
        val original = dependencies.observeDependencies().first()
        assertThrows(Exception::class.java) { runBlocking {
            dependencies.saveDependency(first.copy(metadata = RecordMetadata()))
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            dependencies.saveDependency(SavedTaskDependency(tasks[2].metadata.id, tasks[0].metadata.id))
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            dependencies.saveDependency(SavedTaskDependency(tasks[0].metadata.id, UUID.randomUUID().toString()))
        } }
        assertEquals(original, dependencies.observeDependencies().first())
    }

    @Test
    fun revisionsProtectUpdatesDeletesAndCycleFailures(): Unit = runBlocking {
        val tasks = saveTasks(3)
        val original = SavedTaskDependency(tasks[0].metadata.id, tasks[1].metadata.id)
        dependencies.saveDependency(original)
        dependencies.saveDependency(original.copy(dependsOnTaskId = tasks[2].metadata.id))
        val updated = dependencies.observeDependencies().first().single()
        assertEquals(1L, updated.metadata.revision)
        assertThrows(RevisionConflictException::class.java) { runBlocking { dependencies.saveDependency(original) } }
        assertThrows(RevisionConflictException::class.java) { runBlocking {
            dependencies.deleteDependency(original.metadata.id, 0)
        } }
        dependencies.saveDependency(SavedTaskDependency(tasks[1].metadata.id, tasks[0].metadata.id))
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            dependencies.saveDependency(updated.copy(dependsOnTaskId = tasks[1].metadata.id))
        } }
        assertEquals(updated, dependencies.observeDependencies().first().first { it.metadata.id == updated.metadata.id })
        dependencies.deleteDependency(updated.metadata.id, 1)
        assertEquals(1, dependencies.observeDependencies().first().size)
    }

    @Test
    fun deletingEitherEndpointCascadesOnlyItsEdges(): Unit = runBlocking {
        val tasks = saveTasks(4)
        val edges = listOf(
            SavedTaskDependency(tasks[0].metadata.id, tasks[1].metadata.id),
            SavedTaskDependency(tasks[1].metadata.id, tasks[2].metadata.id),
            SavedTaskDependency(tasks[3].metadata.id, tasks[1].metadata.id),
            SavedTaskDependency(tasks[0].metadata.id, tasks[2].metadata.id),
        )
        edges.forEach { dependencies.saveDependency(it) }
        fixture.repository.deleteTask(tasks[1].metadata.id, 0)
        assertEquals(listOf(edges.last()), dependencies.observeDependencies().first())
        assertEquals(3, fixture.repository.observeTasks().first().size)
        fixture.repository.deleteTask(tasks[0].metadata.id, 0)
        assertEquals(emptyList<SavedTaskDependency>(), dependencies.observeDependencies().first())
        assertEquals(2, fixture.repository.observeTasks().first().size)
    }

    @Test
    fun competingBackEdgesAreSerialized(): Unit = runBlocking {
        val tasks = saveTasks(2)
        val results = listOf(
            SavedTaskDependency(tasks[0].metadata.id, tasks[1].metadata.id),
            SavedTaskDependency(tasks[1].metadata.id, tasks[0].metadata.id),
        ).map { edge -> async(Dispatchers.IO) { runCatching { dependencies.saveDependency(edge) } } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.exceptionOrNull() is IllegalArgumentException })
        assertEquals(1, dependencies.observeDependencies().first().size)
    }

    @Test
    fun sqlEnforcesPairUniquenessForeignKeysSelfEdgesAndMetadata(): Unit = runBlocking {
        val tasks = saveTasks(2)
        val edge = SavedTaskDependency(tasks[0].metadata.id, tasks[1].metadata.id)
        dependencies.saveDependency(edge)
        val db = fixture.database.openHelper.writableDatabase
        assertThrows(Exception::class.java) { db.execSQL("UPDATE task_dependencies SET depends_on_task_id = task_id") }
        assertThrows(Exception::class.java) { db.execSQL("UPDATE task_dependencies SET revision = -1") }
        assertThrows(Exception::class.java) { db.execSQL("UPDATE task_dependencies SET updated_at = created_at - 1") }
        assertThrows(Exception::class.java) { db.execSQL(
            "UPDATE task_dependencies SET task_id = ?", arrayOf(UUID.randomUUID().toString()),
        ) }
        assertThrows(Exception::class.java) { db.execSQL("""
            INSERT INTO task_dependencies(id,created_at,updated_at,revision,task_id,depends_on_task_id)
            SELECT ?,created_at,updated_at,revision,task_id,depends_on_task_id FROM task_dependencies
        """.trimIndent(), arrayOf(UUID.randomUUID().toString())) }
        assertEquals(listOf(edge), dependencies.observeDependencies().first())
    }

    @Test
    fun lostKeyBlocksDependencyAccessWithoutReplacementOrDeletion(): Unit = runBlocking {
        val tasks = saveTasks(2)
        val edge = SavedTaskDependency(tasks[0].metadata.id, tasks[1].metadata.id)
        dependencies.saveDependency(edge)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(fixture.alias) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { dependencies.observeDependencies().first() } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { dependencies.saveDependency(edge) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            dependencies.deleteDependency(edge.metadata.id, 0)
        } }
        assertEquals(1, fixture.database.dependencies().all().size)
        assertNull(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(fixture.alias, null))
    }

    @Test
    fun savedDependencySurvivesDatabaseReopen(): Unit = runBlocking {
        val tasks = saveTasks(2)
        val edge = SavedTaskDependency(tasks[0].metadata.id, tasks[1].metadata.id)
        dependencies.saveDependency(edge)
        fixture.database.close()
        val reopened = Room.databaseBuilder(fixture.context, PersonalDatabase::class.java, fixture.name)
            .addCallback(RecordConstraints).build()
        try {
            val repository = LocalPersonalRepository(reopened, fixture.cipher)
            assertEquals(listOf(edge), repository.dependencies.observeDependencies().first())
            assertEquals(tasks.toSet(), repository.observeTasks().first().toSet())
        } finally {
            reopened.close()
        }
    }

    private suspend fun saveTasks(count: Int) = List(count) { SavedTask("Synthetic prerequisite $it") }.also { tasks ->
        tasks.forEach { fixture.repository.saveTask(it) }
    }
}
