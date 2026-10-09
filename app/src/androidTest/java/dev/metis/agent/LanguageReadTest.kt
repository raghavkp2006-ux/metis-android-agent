package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.languageAgentOrchestrator
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.TaskStatus
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageReadTest {
    @Test
    fun tasksReturnBoundedOpenRecordsAndMutationsCannotWrite(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            repeat(7) { fixture.repository.saveTask(SavedTask("Synthetic open task $it")) }
            fixture.repository.saveTask(SavedTask("Synthetic cancelled task", status = TaskStatus.CANCELLED))
            val before = fixture.repository.observeTasks().first()
            val pipeline = languageAgentOrchestrator(LocalAgentReads(fixture.repository)) { ZoneId.of("UTC") }
            val answer = pipeline.process(request("What tasks do I have?"))
            assertEquals(AgentResultStatus.ANSWER, answer.status)
            assertEquals(5, answer.message.count { it == '•' })
            assertFalse(answer.message.contains("cancelled"))
            assertTrue(answer.message.contains("bounded excerpt"))
            listOf("add a task to study", "remind me tomorrow at 8 am to call Rahul").forEach {
                val result = pipeline.process(request(it))
                assertEquals(AgentResultStatus.UNSUPPORTED, result.status)
                assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
            }
            assertEquals(before, fixture.repository.observeTasks().first())
            assertTrue(fixture.repository.foundation.reminder.observe().first().isEmpty())
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    @Test
    fun memoryAnswersKeepExplicitDerivedAndExpiryBoundaries(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val at = System.currentTimeMillis()
            fixture.repository.saveMemory(SavedMemory("Synthetic DSP exam explicit fact"))
            fixture.repository.saveMemory(SavedMemory("Synthetic DSP exam derived fact", origin = MemoryOrigin.DERIVED))
            fixture.repository.saveMemory(SavedMemory("Synthetic DSP exam expired fact", expiresAt = at - 1))
            val reads = LocalAgentReads(fixture.repository)
            val answer = reads.memories("DSP exam", at)
            assertTrue(answer.contains("explicit fact"))
            assertFalse(answer.contains("derived fact") || answer.contains("expired fact"))
            assertTrue(reads.memories("unknown topic", at).contains("none found"))
        }
    }

    @Test
    fun promiseLookupRequiresExactlyOneLocalPerson(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val pipeline = languageAgentOrchestrator(LocalAgentReads(fixture.repository)) { ZoneId.of("UTC") }
            val input = request("What did I promise Rahul?")
            assertEquals(AgentResultStatus.FOLLOW_UP, pipeline.process(input).status)
            val person = SavedPerson("Rahul")
            fixture.repository.foundation.person.save(person)
            fixture.repository.foundation.promise.save(SavedPromise(person.metadata.id, "Synthetic book return", "OPEN"))
            val answer = pipeline.process(input)
            assertEquals(AgentResultStatus.ANSWER, answer.status)
            assertTrue(answer.message.contains("OPEN: Synthetic book return"))
            fixture.repository.foundation.person.save(SavedPerson("Rahul", metadata = RecordMetadata()))
            assertEquals(AgentResultStatus.FOLLOW_UP, pipeline.process(input).status)
        }
    }

    @Test
    fun missingKeyBecomesVisibleFailureInsteadOfEmptyAnswer(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            fixture.repository.saveTask(SavedTask("Synthetic encrypted task"))
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(fixture.alias) }
            val result = languageAgentOrchestrator(LocalAgentReads(fixture.repository)) { ZoneId.of("UTC") }
                .process(request("show my tasks"))
            assertEquals(AgentResultStatus.FAILED, result.status)
            assertFalse(result.message.contains("encrypted task") || result.message.contains("none found"))
        }
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, Instant.now())
}
