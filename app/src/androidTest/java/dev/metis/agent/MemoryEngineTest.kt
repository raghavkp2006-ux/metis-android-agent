package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.MemoryEntity
import dev.metis.agent.data.storage.StoredMetadata
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.MemoryExpiryChoice
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemoryReference
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.MemoryType
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedPerson
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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryEngineTest {
    private val fixture = StorageTestFixture()
    private val repository = LocalPersonalRepository(fixture.database, fixture.cipher, now = { 100 })
    private val engine = repository.memoryEngine
    @After fun cleanup() = fixture.close()

    @Test
    fun manualSaveUpdateAndForgetAreEncryptedAndRevisionChecked(): Unit = runBlocking {
        engine.saveExplicit("Synthetic phase five fact", MemoryType.SEMANTIC, 0.75f, MemoryExpiryChoice.FOREVER)
        val original = repository.observeMemories().first().single()
        assertEquals(MemoryOrigin.EXPLICIT, original.origin)
        assertNull(original.expiresAt)
        engine.saveExplicit("Synthetic changed fact", original.type, 1f, MemoryExpiryChoice.WEEK, original)
        val changed = repository.observeMemories().first().single()
        assertEquals(1L, changed.metadata.revision)
        assertEquals(604800100L, changed.expiresAt)
        assertThrows(RevisionConflictException::class.java) { runBlocking { engine.forget(original) } }
        fixture.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val disk = String(fixture.context.getDatabasePath(fixture.name).readBytes(), Charsets.ISO_8859_1)
        assertFalse(disk.contains("Synthetic phase five fact"))
        assertFalse(disk.contains("Synthetic changed fact"))
        engine.forget(changed)
        assertTrue(repository.observeMemories().first().isEmpty())
    }

    @Test
    fun workingAndRelatedRetrievalFilterBeforeDecryption(): Unit = runBlocking {
        engine.saveExplicit("Synthetic working fact", MemoryType.WORKING, 0.5f, MemoryExpiryChoice.WEEK)
        val working = repository.observeMemories().first().single()
        assertEquals(86400100L, working.expiresAt)
        val person = SavedPerson("Synthetic person")
        repository.foundation.person.save(person)
        val fact = SavedMemory("Synthetic linked fact", entityType = MemoryEntityType.PERSON, entityId = person.metadata.id)
        repository.saveMemory(fact)
        repository.saveMemory(fact.copy(metadata = RecordMetadata(), origin = MemoryOrigin.DERIVED))
        fixture.database.records().insert(MemoryEntity(StoredMetadata(UUID.randomUUID().toString(), 0, 0, 0),
            "PROCEDURAL", "EXPLICIT", byteArrayOf(0), 0.5f, 1f, null, null, null))
        // Legacy unbounded working rows remain archived, never eligible ephemeral context.
        fixture.database.records().insert(MemoryEntity(StoredMetadata(UUID.randomUUID().toString(), 0, 0, 0),
            "WORKING", "EXPLICIT", byteArrayOf(0), 0.5f, 1f, null, null, null))
        fixture.cipher.decryptions = 0
        assertEquals(listOf(working), engine.working().memories)
        assertEquals(1, fixture.cipher.decryptions)
        fixture.cipher.decryptions = 0
        assertEquals(listOf(fact), engine.related(MemoryReference(MemoryEntityType.PERSON, person.metadata.id)).memories)
        assertEquals(1, fixture.cipher.decryptions)
        assertEquals(2, engine.related(MemoryReference(MemoryEntityType.PERSON, person.metadata.id), true).memories.size)
    }

    @Test
    fun rankedSearchReturnsScoresInTheSameOrderAndDropsExpiredItems(): Unit = runBlocking {
        val low = SavedMemory("Synthetic exam", metadata = RecordMetadata(createdAt = 100), importance = 0f)
        val high = low.copy(metadata = RecordMetadata(createdAt = 90), importance = 1f)
        listOf(low, high, high.copy(metadata = RecordMetadata(), expiresAt = 100)).forEach { repository.saveMemory(it) }
        val result = engine.retrieve(MemorySearchQuery("exam", at = 100))
        assertEquals(listOf(high, low), result.memories)
        assertEquals(result.memories.map { it.metadata.id }, result.scores.map { it.id })
        assertTrue(result.scores.first().total > result.scores.last().total)
    }

    @Test
    fun derivedProvenanceCannotBePromotedThroughEitherApi(): Unit = runBlocking {
        val derived = SavedMemory("Synthetic derived fact", origin = MemoryOrigin.DERIVED)
        repository.saveMemory(derived)
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            engine.saveExplicit("Synthetic changed fact", derived.type, 1f, MemoryExpiryChoice.KEEP, derived)
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.saveMemory(derived.copy(origin = MemoryOrigin.EXPLICIT))
        } }
        assertEquals(listOf(derived), repository.observeMemories().first())
    }

    @Test
    fun expiryReviewIsBoundedMetadataOnlyAndDeletionUsesOnlyReviewedIds(): Unit = runBlocking {
        repeat(51) { repository.saveMemory(SavedMemory("Synthetic expired fact $it", expiresAt = 99)) }
        fixture.cipher.decryptions = 0
        val review = engine.reviewExpired()
        assertEquals(0, fixture.cipher.decryptions)
        assertEquals(50, review.candidates.size)
        assertTrue(review.hasMore)
        val new = SavedMemory("Synthetic newly expired fact", expiresAt = 99)
        repository.saveMemory(new)
        assertEquals(50, engine.deleteExpired(review))
        val remaining = repository.observeMemories().first()
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.metadata.id == new.metadata.id })
    }

    @Test
    fun staleExpiryReviewRollsBackAndMissingKeyNeverDeletesData(): Unit = runBlocking {
        val first = SavedMemory("Synthetic expired first", expiresAt = 99)
        val second = SavedMemory("Synthetic expired second", expiresAt = 99)
        repository.saveMemory(first)
        repository.saveMemory(second)
        val stale = engine.reviewExpired()
        repository.saveMemory(second.copy(expiresAt = null))
        assertThrows(RevisionConflictException::class.java) { runBlocking { engine.deleteExpired(stale) } }
        assertEquals(2, repository.observeMemories().first().size)
        val review = engine.reviewExpired()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(fixture.alias) }
        assertEquals(review, engine.reviewExpired())
        assertThrows(Exception::class.java) { runBlocking { engine.deleteExpired(review) } }
        assertEquals(2, fixture.database.records().recordCount())
    }

    @Test
    fun expiryCleanupHandlesTwoReviewedMemoriesInAReferenceCycle(): Unit = runBlocking {
        val first = SavedMemory("Synthetic first", expiresAt = 99)
        repository.saveMemory(first)
        val second = SavedMemory("Synthetic second", expiresAt = 99, entityType = MemoryEntityType.MEMORY,
            entityId = first.metadata.id)
        repository.saveMemory(second)
        repository.saveMemory(first.copy(entityType = MemoryEntityType.MEMORY, entityId = second.metadata.id))
        assertEquals(2, engine.deleteExpired(engine.reviewExpired()))
        assertTrue(repository.observeMemories().first().isEmpty())
    }
}
