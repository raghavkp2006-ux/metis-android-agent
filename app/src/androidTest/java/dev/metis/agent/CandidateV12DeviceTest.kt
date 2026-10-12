package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ContextBuilder
import dev.metis.agent.domain.agent.EnglishRules
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.LanguageSpecialist
import dev.metis.agent.domain.agent.LocalAgentOrchestrator
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedPromise
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated synthetic database: never reads or changes the user's actual records. */
@RunWith(AndroidJUnit4::class)
class CandidateV12DeviceTest {
    @Test
    fun userBatchAndScopeCorrectionsUseCorrectSavedRecordsWithoutMutations(): Unit = runBlocking {
        val rows = InstrumentationRegistry.getInstrumentation().context.assets
            .open("phase15-v12.tsv").bufferedReader().use { it.readLines().drop(1).map { line -> line.split('\t') } }
        assertEquals(50, rows.count { it[0].startsWith("user-") })
        assertEquals(44, rows.count { it[3] == "read" })
        // Nine requests seed at most 18 memories, below the existing 20-candidate search bound.
        rows.chunked(9).forEach { batch -> verifyBatch(batch) }
    }

    private suspend fun verifyBatch(rows: List<List<String>>) {
        StorageTestFixture().use { fixture ->
            val positives = rows.filter { it[3] == "read" }
            positives.distinctBy { it[2] + it[4] }.forEach { row ->
                val query = decode(row[4])
                if (row[2] == "WHAT_DID_I_PROMISE") {
                    val person = SavedPerson(query)
                    fixture.repository.foundation.person.save(person)
                    fixture.repository.foundation.promise.save(SavedPromise(person.metadata.id, "Synthetic correct promise", "OPEN"))
                } else {
                    fixture.repository.saveMemory(SavedMemory("$query Synthetic correct memory"))
                    fixture.repository.saveMemory(SavedMemory("${query.substringAfterLast(' ')} Synthetic decoy"))
                }
            }
            val decoyPerson = SavedPerson("Unrelated Sentinel")
            fixture.repository.foundation.person.save(decoyPerson)
            fixture.repository.foundation.promise.save(SavedPromise(decoyPerson.metadata.id, "Synthetic decoy promise", "OPEN"))
            val before = fixture.repository.searchMemories(MemorySearchQuery(at = System.currentTimeMillis()))
            val beforeTasks = fixture.repository.observeTasks().first()
            val beforePeople = fixture.repository.foundation.person.observe().first()
            val beforePromises = fixture.repository.foundation.promise.observe().first()
            val reads = CountingReads(LocalAgentReads(fixture.repository))
            var contexts = 0
            val rules = EnglishRules { ZoneId.of("UTC") }
            val builder = object : ContextBuilder {
                override suspend fun snapshot(request: AgentRequest): ResolvedContext {
                    contexts++
                    return ResolvedContext(request.timestamp, ZoneId.of("UTC"), AutonomyLevel.ANSWER_ONLY,
                        CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)))
                }
            }
            val pipeline = LocalAgentOrchestrator(rules, rules, builder, LanguageSpecialist(reads), ProposalPolicy())
            rows.forEach { row ->
                val text = decode(row[1])
                val startReads = reads.calls
                val startContexts = contexts
                val result = pipeline.process(AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, Instant.now()))
                val expected = if (row[3] == "read") 1 else 0
                assertEquals(row[0], expected, reads.calls - startReads)
                assertEquals(row[0], expected, contexts - startContexts)
                if (expected == 1) {
                    assertEquals(row[0], AgentResultStatus.ANSWER, result.status)
                    assertEquals(row[0], decode(row[4]), reads.query)
                    assertTrue(row[0], result.message.contains("Synthetic correct"))
                    assertFalse(row[0], result.message.contains("Synthetic decoy"))
                }
                if (row[3] == "unsupported") assertEquals(row[0], AgentResultStatus.UNSUPPORTED, result.status)
                if (result.status == AgentResultStatus.FOLLOW_UP) assertTrue(row[0], result.message.contains(text))
                assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
            }
            val after = fixture.repository.searchMemories(MemorySearchQuery(at = System.currentTimeMillis()))
            assertEquals(before.memories, after.memories)
            assertEquals(beforeTasks, fixture.repository.observeTasks().first())
            assertEquals(beforePeople, fixture.repository.foundation.person.observe().first())
            assertEquals(beforePromises, fixture.repository.foundation.promise.observe().first())
            assertTrue(fixture.repository.foundation.reminder.observe().first().isEmpty())
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
            val unique = positives.firstOrNull { it[2] == "WHAT_DID_I_PROMISE" } ?: return
            fixture.repository.foundation.person.save(SavedPerson(decode(unique[4])))
            val startReads = reads.calls
            val ambiguous = pipeline.process(AgentRequest(UUID.randomUUID(), decode(unique[1]), InputSource.TEXT, Instant.now()))
            assertEquals(AgentResultStatus.FOLLOW_UP, ambiguous.status)
            // The adapter is called, but duplicate saved people cause no promise lookup.
            assertEquals(startReads + 1, reads.calls)
            assertFalse(ambiguous.message.contains("Synthetic correct promise"))
        }
    }

    private fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)

    private class CountingReads(private val delegate: AgentReadPort) : AgentReadPort {
        var calls = 0
        var query = ""
        override suspend fun tasks(): String { calls++; return delegate.tasks() }
        override suspend fun memories(query: String, at: Long): String {
            calls++; this.query = query; return delegate.memories(query, at)
        }
        override suspend fun promises(person: String): SpecialistResult {
            calls++; query = person; return delegate.promises(person)
        }
    }
}
