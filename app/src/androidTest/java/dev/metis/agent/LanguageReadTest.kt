package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.languageAgentOrchestrator
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.MemorySearchQuery
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
    fun v8KeepsTitleQueryAndRecallSeparateFromUnsupportedActions(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            fixture.repository.saveMemory(SavedMemory("The Who Synthetic title match"))
            fixture.repository.saveMemory(SavedMemory("Who Synthetic decoy match"))
            val before = fixture.repository.searchMemories(
                dev.metis.agent.domain.storage.MemorySearchQuery(text = "", at = System.currentTimeMillis()))
            val pipeline = languageAgentOrchestrator(LocalAgentReads(fixture.repository)) { ZoneId.of("UTC") }
            listOf("Can you show me notes about The Who?", "Remind me of The Who").forEach { text ->
                val result = pipeline.process(request(text))
                assertEquals(AgentResultStatus.ANSWER, result.status)
                assertTrue(result.message.contains("Synthetic title match"))
                assertFalse(result.message.contains("Synthetic decoy match"))
                assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
            }
            listOf("Remind me of The Who from last year", "Show me notes about The Who then delete them").forEach {
                val result = pipeline.process(request(it))
                assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
                assertTrue(result.message.contains(it))
            }
            assertEquals(AgentResultStatus.UNSUPPORTED,
                pipeline.process(request("remind me tomorrow at 8 am to study")).status)
            assertTrue(fixture.repository.foundation.reminder.observe().first().isEmpty())
            val after = fixture.repository.searchMemories(
                dev.metis.agent.domain.storage.MemorySearchQuery(text = "", at = System.currentTimeMillis()))
            assertEquals(before.memories, after.memories)
        }
    }

    @Test
    fun v10LiteralSubjectsMatchSavedRecordsAndExcludeDescriptiveDecoys(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val examples = listOf(
                Triple("Did I write down what the doctor said about the scan results",
                    "what the doctor said about the scan results", "what the neighbour said about the scan results"),
                Triple("The name of the hotel in Manali where we stayed",
                    "hotel in Manali where we stayed", "hotel in Manali where they stayed"),
                Triple("What did I note about the weather in Shillong in October",
                    "the weather in Shillong in October", "the weather in Shillong in March"),
                Triple("Did I write down what my friend said about her divorce",
                    "what my friend said about her divorce", "what my friend said about his divorce"),
                Triple("The number for the mechanic. do you have it",
                    "number for the mechanic", "address for the mechanic"),
                Triple("I wrote something about not eating after 9pm. why did i write that",
                    "not eating after 9pm", "eating before 9pm"),
            )
            examples.forEachIndexed { index, (_, matching, decoy) ->
                fixture.repository.saveMemory(SavedMemory("$matching Synthetic match $index"))
                fixture.repository.saveMemory(SavedMemory("$decoy Synthetic decoy $index"))
            }
            val before = fixture.repository.searchMemories(MemorySearchQuery(at = System.currentTimeMillis()))
            val pipeline = languageAgentOrchestrator(LocalAgentReads(fixture.repository)) { ZoneId.of("UTC") }
            examples.forEachIndexed { index, (text, _, _) ->
                val result = pipeline.process(request(text))
                assertEquals(AgentResultStatus.ANSWER, result.status)
                assertTrue(result.message.contains("Synthetic match $index"))
                assertFalse(result.message.contains("Synthetic decoy"))
                assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
            }
            val missing = pipeline.process(request("The password for the synthetic cloud drive"))
            assertEquals(AgentResultStatus.ANSWER, missing.status)
            assertTrue(missing.message.contains("none found on this device"))
            val after = fixture.repository.searchMemories(MemorySearchQuery(at = System.currentTimeMillis()))
            assertEquals(before.memories, after.memories)
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    @Test
    fun v10OpenTaskScopeAndExactUniqueUnicodePersonStayBounded(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            fixture.repository.saveTask(SavedTask("Synthetic v10 open"))
            fixture.repository.saveTask(SavedTask("Synthetic v10 done", status = TaskStatus.COMPLETED,
                completedAt = System.currentTimeMillis()))
            val person = SavedPerson("Zoë D’Souza")
            fixture.repository.foundation.person.save(person)
            fixture.repository.foundation.person.save(SavedPerson("Zoë"))
            fixture.repository.foundation.promise.save(SavedPromise(person.metadata.id, "Synthetic exact promise", "OPEN"))
            val before = fixture.repository.observeTasks().first()
            val pipeline = languageAgentOrchestrator(LocalAgentReads(fixture.repository)) { ZoneId.of("UTC") }
            val task = pipeline.process(request("Could you please retrieve the tasks I have not completed"))
            assertTrue(task.message.contains("Synthetic v10 open"))
            assertFalse(task.message.contains("Synthetic v10 done"))
            val text = "What did I tell Zoë D’Souza I would do"
            assertTrue(pipeline.process(request(text)).message.contains("Synthetic exact promise"))
            assertEquals(AgentResultStatus.FOLLOW_UP,
                pipeline.process(request("What did I tell Zoë D’Souza Kumar I would do")).status)
            fixture.repository.foundation.person.save(SavedPerson("Zoë D’Souza"))
            assertEquals(AgentResultStatus.FOLLOW_UP, pipeline.process(request(text)).status)
            assertEquals(before, fixture.repository.observeTasks().first())
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
