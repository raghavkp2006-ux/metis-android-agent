package dev.metis.agent

import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.MemoryEntity
import dev.metis.agent.data.storage.StoredMetadata
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.MemoryType
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemorySearchTest {
    private val fixture = StorageTestFixture()
    private val repository = fixture.repository

    @After fun cleanup() = fixture.close()

    @Test
    fun metadataAndExpiryFilterBeforeDecryptionAndQueriesAreLiteral(): Unit = runBlocking {
        val fact = SavedMemory("Synthetic café DSP exam", expiresAt = 101)
        repository.saveMemory(fact)
        repository.saveMemory(SavedMemory("Synthetic café DSP expired", expiresAt = 100))
        repository.saveMemory(SavedMemory("Synthetic café DSP derived", origin = MemoryOrigin.DERIVED))
        val id = UUID.randomUUID().toString()
        // Corrupt content outside the selected type must never be decrypted by this search.
        fixture.database.records().insert(MemoryEntity(
            StoredMetadata(id, 1, 1, 0), MemoryType.PROCEDURAL.name, MemoryOrigin.EXPLICIT.name,
            byteArrayOf(0), 0.5f, 1f, null, null, null,
        ))
        fixture.cipher.decryptions = 0
        val result = repository.searchMemories(MemorySearchQuery(
            "CAFE DSP", type = MemoryType.SEMANTIC, origin = MemoryOrigin.EXPLICIT, at = 100,
        ))
        assertEquals(listOf(fact), result.memories)
        assertEquals(1, result.candidateCount)
        assertEquals(1, fixture.cipher.decryptions)
        assertFalse(result.candidatesTruncated)
        assertTrue(repository.searchMemories(MemorySearchQuery("*\";--")).memories.isEmpty())
        assertTrue(repository.searchMemories(MemorySearchQuery(
            "café OR DSP", type = MemoryType.SEMANTIC, at = 100,
        )).memories.isEmpty())
    }

    @Test
    fun candidateAndResultLimitsAreVisibleAndStable(): Unit = runBlocking {
        repeat(MemorySearchQuery.MAX_CANDIDATES + 1) { number ->
            val id = UUID.randomUUID().toString()
            fixture.database.records().insert(MemoryEntity(
                StoredMetadata(id, 0, number.toLong(), 0), MemoryType.SEMANTIC.name, MemoryOrigin.EXPLICIT.name,
                fixture.cipher.encrypt("Synthetic bounded search $number", "memories/$id/content"),
                0.5f, 1f, null, null, null,
            ))
        }
        fixture.cipher.decryptions = 0
        val defaultStarted = SystemClock.elapsedRealtime()
        val preview = repository.searchMemories(MemorySearchQuery("bounded", limit = 1))
        val defaultElapsed = SystemClock.elapsedRealtime() - defaultStarted
        assertEquals(MemorySearchQuery.DEFAULT_CANDIDATES, preview.candidateCount)
        assertEquals(MemorySearchQuery.DEFAULT_CANDIDATES, fixture.cipher.decryptions)
        assertTrue(preview.candidatesTruncated)
        fixture.cipher.decryptions = 0
        val started = SystemClock.elapsedRealtime()
        val result = repository.searchMemories(MemorySearchQuery(
            "bounded", limit = 1, candidateLimit = MemorySearchQuery.MAX_CANDIDATES,
        ))
        val elapsed = SystemClock.elapsedRealtime() - started
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("stream", "\nSynthetic memory search: 20 candidates $defaultElapsed ms; " +
                "200 candidates $elapsed ms (debug API ${Build.VERSION.SDK_INT}).\n")
        })
        assertEquals(200, result.candidateCount)
        assertEquals(200, fixture.cipher.decryptions)
        assertTrue(result.candidatesTruncated)
        assertTrue(result.matchesTruncated)
        assertEquals("Synthetic bounded search 200", result.memories.single().content)
        assertTrue(repository.searchMemories(MemorySearchQuery("search 0")).memories.isEmpty())
    }

    @Test
    fun indexRebuildsAfterUpdatesAndDeletesWithoutPersistentPlaintext(): Unit = runBlocking {
        val old = SavedMemory("Synthetic original secretword")
        repository.saveMemory(old)
        val databaseFiles = fixture.context.databaseList().toSet()
        assertEquals(old, repository.searchMemories(MemorySearchQuery("secretword")).memories.single())
        repository.saveMemory(old.copy(content = "Synthetic changed freshword"))
        assertTrue(repository.searchMemories(MemorySearchQuery("secretword")).memories.isEmpty())
        val saved = repository.searchMemories(MemorySearchQuery("freshword")).memories.single()
        assertEquals(1L, saved.metadata.revision)
        assertEquals(databaseFiles, fixture.context.databaseList().toSet())
        fixture.database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE name LIKE 'memory_search%'",
        ).use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        fixture.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val text = String(fixture.context.getDatabasePath(fixture.name).readBytes(), Charsets.ISO_8859_1)
        assertFalse(text.contains("secretword"))
        assertFalse(text.contains("freshword"))
        repository.deleteMemory(saved.metadata.id, saved.metadata.revision)
        assertTrue(repository.searchMemories(MemorySearchQuery("freshword")).memories.isEmpty())
    }

    @Test
    fun linkedMemoryDeletionIsAtomicAndUnrelatedMemoryIsRetained(): Unit = runBlocking {
        val task = SavedTask("Synthetic linked task")
        repository.saveTask(task)
        val schedule = SavedSchedule("Synthetic block", 1, 2, "UTC", "Reason", taskId = task.metadata.id)
        repository.saveSchedule(schedule)
        val taskMemory = SavedMemory("Synthetic linked fact", entityType = MemoryEntityType.TASK, entityId = task.metadata.id)
        val blockMemory = SavedMemory(
            "Synthetic block fact", entityType = MemoryEntityType.SCHEDULE, entityId = schedule.metadata.id,
        )
        val unrelated = SavedMemory("Synthetic unrelated fact")
        listOf(taskMemory, blockMemory, unrelated).forEach { repository.saveMemory(it) }
        assertEquals(taskMemory, repository.searchMemories(MemorySearchQuery(
            "fact", entityType = MemoryEntityType.TASK, entityId = task.metadata.id,
        )).memories.single())
        assertThrows(RevisionConflictException::class.java) { runBlocking { repository.deleteTask(task.metadata.id, 1) } }
        assertEquals(3, repository.observeMemories().first().size)
        repository.deleteTask(task.metadata.id, 0)
        assertEquals(2, repository.observeMemories().first().size)
        repository.deleteSchedule(schedule.metadata.id, 0)
        assertEquals(listOf(unrelated), repository.observeMemories().first())
    }

    @Test
    fun invalidLinksAndSelectedCorruptContentFailWithoutPartialResults(): Unit = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.saveMemory(SavedMemory(
                "Synthetic orphan fact", entityType = MemoryEntityType.TASK, entityId = UUID.randomUUID().toString(),
            ))
        } }
        assertEquals(0, fixture.database.records().recordCount())
        repository.saveMemory(SavedMemory("Synthetic valid fact", metadata = RecordMetadata(createdAt = 0)))
        fixture.database.openHelper.writableDatabase.execSQL("UPDATE memories SET content = X'00'")
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.searchMemories(MemorySearchQuery("fact"))
        } }
        assertEquals(1, fixture.database.records().recordCount())
    }
}
