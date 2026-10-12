package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedPromise
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Temporary encrypted databases and test keys only; no installed personal data is cleared. */
@RunWith(AndroidJUnit4::class)
class CandidateV14DeviceTest {
    @Test fun newRecallFormsUseMatchingRecordsAndPreserveStorage(): Unit = runBlocking {
        val rows = InstrumentationRegistry.getInstrumentation().context.assets.open("phase15-v14.tsv")
            .bufferedReader().use { it.readLines().drop(1).map { line -> line.split('\t') } }
        assertEquals(75, rows.count { it[4] == "1" })
        // At most 8 matching memories plus 8 decoys, below the unchanged 20-candidate bound.
        rows.chunked(8).forEach { batch ->
            StorageTestFixture().use { fixture ->
                batch.filter { it[4] == "1" }.distinctBy { it[5] }.forEach { row ->
                    val query = decode(row[5])
                    fixture.repository.saveMemory(SavedMemory("$query Synthetic exact scope"))
                    fixture.repository.saveMemory(SavedMemory("Unrelated synthetic decoy"))
                }
                val before = fixture.repository.searchMemories(MemorySearchQuery(at = System.currentTimeMillis())).memories
                val agent = ConfirmedTaskAgent(LocalAgentReads(fixture.repository), fixture.repository.taskActions,
                    localCalendar = true, readCandidate = ReadCandidate.V14)
                batch.forEach { row ->
                    fixture.cipher.decryptions = 0
                    val answer = agent.process(request(decode(row[1])))
                    if (row[4] == "1") {
                        assertEquals(row[0], AgentResultStatus.ANSWER, answer.status)
                        assertTrue(row[0], answer.message.contains("Synthetic exact scope"))
                        assertFalse(row[0], answer.message.contains("Unrelated synthetic decoy"))
                    } else {
                        assertTrue(row[0], answer.status in setOf(AgentResultStatus.FOLLOW_UP, AgentResultStatus.DENIED))
                        assertEquals(row[0], 0, fixture.cipher.decryptions)
                    }
                }
                assertEquals(before,
                    fixture.repository.searchMemories(MemorySearchQuery(at = System.currentTimeMillis())).memories)
                assertTrue(fixture.repository.observeTasks().first().isEmpty())
            }
        }
    }

    @Test fun completeTitlesAttributionAndExactUniquePeopleStayDistinct(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            fixture.repository.saveMemory(SavedMemory("The Who Synthetic title match"))
            fixture.repository.saveMemory(SavedMemory("Who Synthetic title decoy"))
            fixture.repository.saveMemory(SavedMemory("my talk with Dev Synthetic attributed match"))
            fixture.repository.saveMemory(SavedMemory("my talk with Devi Synthetic attributed decoy"))
            val person = SavedPerson("Zoë O’Neil")
            fixture.repository.foundation.person.save(person)
            fixture.repository.foundation.promise.save(SavedPromise(person.metadata.id, "Synthetic exact promise", "OPEN"))
            val agent = ConfirmedTaskAgent(LocalAgentReads(fixture.repository), fixture.repository.taskActions,
                localCalendar = true, readCandidate = ReadCandidate.V14)
            val title = agent.process(request("What do you remember about The Who?"))
            assertTrue(title.message.contains("Synthetic title match"))
            assertFalse(title.message.contains("Synthetic title decoy"))
            val speech = agent.process(request("What was your recollection of my talk with Dev?"))
            assertTrue(speech.message.contains("Synthetic attributed match"))
            assertFalse(speech.message.contains("Synthetic attributed decoy"))
            val promise = agent.process(request("What did I promise Zoë O’Neil?"))
            assertTrue(promise.message.contains("Synthetic exact promise"))
            fixture.repository.foundation.person.save(SavedPerson("Zoë O’Neil"))
            val ambiguous = agent.process(request("What did I promise Zoë O’Neil?"))
            assertEquals(AgentResultStatus.FOLLOW_UP, ambiguous.status)
            assertFalse(ambiguous.message.contains("Synthetic exact promise"))
            val missing = agent.process(request("What did I promise Zora O’Neil?"))
            assertEquals(AgentResultStatus.FOLLOW_UP, missing.status)
            val noMatch = agent.process(request("What do you remember about the unmatched topic?"))
            assertTrue(noMatch.message.contains("none found on this device"))
        }
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, Instant.now())
    private fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
}
