package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.SavedEvent
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedRelationship
import dev.metis.agent.domain.storage.SavedReminder
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationReferenceCleanupTest {
    private val fixture = StorageTestFixture()
    private val repository = fixture.repository
    private val stores = repository.foundation
    @After fun cleanup() = fixture.close()

    @Test
    fun memoryCyclesAndEventSourcesAreCleanedWithoutDeletingUnrelatedMemory(): Unit = runBlocking {
        val first = SavedMemory("Synthetic first fact")
        val unrelated = SavedMemory("Synthetic unrelated fact")
        repository.saveMemory(first)
        repository.saveMemory(unrelated)
        val event = SavedEvent("TASK_CREATED", "USER", 10, 0.5f, 1,
            entityType = "MEMORY", entityId = first.metadata.id, payloadMetadata = "{}")
        stores.event.save(event)
        val second = SavedMemory("Synthetic second fact", sourceEventId = event.metadata.id,
            entityType = MemoryEntityType.MEMORY, entityId = first.metadata.id)
        repository.saveMemory(second)
        repository.saveMemory(first.copy(sourceEventId = event.metadata.id,
            entityType = MemoryEntityType.MEMORY, entityId = second.metadata.id))
        val updated = repository.observeMemories().first().single { it.metadata.id == first.metadata.id }
        assertThrows(Exception::class.java) { runBlocking { repository.deleteMemory(first.metadata.id, 0) } }
        assertEquals(3, repository.observeMemories().first().size)
        assertEquals(event, stores.event.observe().first().single())
        repository.deleteMemory(first.metadata.id, updated.metadata.revision)
        assertEquals(listOf(unrelated), repository.observeMemories().first())
        val history = stores.event.observe().first().single()
        assertNull(history.entityId)
        assertNull(history.payloadMetadata)
    }

    @Test
    fun ownedChildReferencesAreRemovedWhileDetachedReminderReferencesSurvive(): Unit = runBlocking {
        val person = SavedPerson("Synthetic person")
        stores.person.save(person)
        val relationship = SavedRelationship(person.metadata.id, "FRIEND")
        val promise = SavedPromise(person.metadata.id, "Synthetic promise", "OPEN")
        stores.relationship.save(relationship)
        stores.promise.save(promise)
        val reminder = SavedReminder("Synthetic reminder", 10, "1970-01-01T00:00:00.010", "UTC",
            "APPROXIMATE", "PENDING", UUID.randomUUID().toString(), personId = person.metadata.id)
        stores.reminder.save(reminder)
        listOf(MemoryEntityType.RELATIONSHIP to relationship.metadata.id,
            MemoryEntityType.PROMISE to promise.metadata.id,
            MemoryEntityType.REMINDER to reminder.metadata.id).forEach { (type, id) ->
            repository.saveMemory(SavedMemory("Synthetic linked fact", entityType = type, entityId = id))
        }
        val event = SavedEvent("TASK_CREATED", "USER", 10, 0.5f, 1,
            entityType = "REMINDER", entityId = reminder.metadata.id, payloadMetadata = "{}")
        stores.event.save(event)
        stores.person.delete(person.metadata.id, 0)
        assertTrue(stores.relationship.observe().first().isEmpty())
        assertTrue(stores.promise.observe().first().isEmpty())
        assertEquals(MemoryEntityType.REMINDER, repository.observeMemories().first().single().entityType)
        assertNull(stores.reminder.observe().first().single().personId)
        stores.reminder.delete(reminder.metadata.id, 0)
        assertTrue(repository.observeMemories().first().isEmpty())
        assertNull(stores.event.observe().first().single().entityId)
        assertNull(stores.event.observe().first().single().payloadMetadata)
    }
}
