package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.agent.ReadDateV15
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.domain.storage.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only isolated encrypted fixtures; never clears installed personal data. */
@RunWith(AndroidJUnit4::class)
class CandidateV15DeviceTest {
    private val at = Instant.parse("2026-10-12T05:30:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun authoredScopesReadMatchingRecordsAndBlockAllPairedVariants(): Unit = runBlocking {
        val text = InstrumentationRegistry.getInstrumentation().context.assets.open("phase15-v15.json")
            .bufferedReader().use { it.readText() }
        val array = JSONObject(text).getJSONArray("rows")
        val rows = (0 until array.length()).map(array::getJSONObject)
        assertEquals(87, rows.count { it.getBoolean("read") })
        val grouped = rows.groupBy { it.getString("id").split('-').let { parts ->
            val slot = parts.indexOfFirst { part -> part.toIntOrNull() != null }
            if (slot < 0) it.getString("id") else parts.take(slot + 1).joinToString("-")
        } }
        grouped.values.forEach { batch ->
            StorageTestFixture().use { fixture ->
                batch.firstOrNull { it.getBoolean("read") }?.let { populate(fixture, it.getJSONObject("scope")) }
                val beforeTasks = fixture.repository.observeTasks().first()
                val beforeMemories = fixture.repository.observeMemories().first()
                val beforePromises = fixture.repository.foundation.promise.observe().first()
                val beforeCalendar = fixture.repository.observeSchedules().first()
                batch.forEach { row ->
                    fixture.cipher.decryptions = 0
                    val answer = agent(fixture).process(request(row.getString("utterance")))
                    if (row.getBoolean("read")) {
                        assertEquals(row.getString("id"), AgentResultStatus.ANSWER, answer.status)
                        assertTrue(row.getString("id") + answer.message, answer.message.contains("SCOPE_MATCH"))
                        assertFalse(row.getString("id"), answer.message.contains("SCOPE_DECOY"))
                    } else {
                        assertTrue(row.getString("id"), answer.status in setOf(
                            AgentResultStatus.FOLLOW_UP, AgentResultStatus.DENIED, AgentResultStatus.UNSUPPORTED))
                        assertEquals(row.getString("id"), 0, fixture.cipher.decryptions)
                    }
                }
                assertEquals(beforeTasks, fixture.repository.observeTasks().first())
                assertEquals(beforeMemories, fixture.repository.observeMemories().first())
                assertEquals(beforePromises, fixture.repository.foundation.promise.observe().first())
                assertEquals(beforeCalendar, fixture.repository.observeSchedules().first())
            }
        }
    }

    @Test fun metadataPredicatesRunBeforeDecryptionAndAnyStatusMeansAny(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val due = at.toEpochMilli()
            repeat(25) { fixture.repository.saveTask(SavedTask("review the indigo resonator SCOPE_DECOY",
                status = TaskStatus.COMPLETED, completedAt = due, dueAt = due, dueZoneId = zone.id)) }
            fixture.repository.saveTask(SavedTask("review the indigo resonator SCOPE_MATCH", dueAt = due, dueZoneId = zone.id))
            fixture.cipher.decryptions = 0
            val direct = LocalAgentReads(fixture.repository).tasks(ReadScopeV15.Tasks("the indigo resonator", "OPEN",
                ReadDateV15(ReadDateV15.Field.DUE, "today")), at.toEpochMilli(), zone)
            assertTrue(direct is SpecialistResult.Answer)
            fixture.cipher.decryptions = 0
            val open = agent(fixture).process(request("Find open tasks about the indigo resonator due today"))
            assertTrue(open.message, open.message.contains("SCOPE_MATCH"))
            assertFalse(open.message.contains("SCOPE_DECOY"))
            assertTrue("Only key probe plus filtered task titles", fixture.cipher.decryptions <= 2)
        }
        StorageTestFixture().use { fixture ->
            fixture.repository.saveTask(SavedTask("review the indigo resonator SCOPE_MATCH",
                status = TaskStatus.COMPLETED, completedAt = at.toEpochMilli()))
            val any = agent(fixture).process(request("Have I already created a task to review the indigo resonator?"))
            assertTrue(any.message, any.message.contains("COMPLETED") && any.message.contains("SCOPE_MATCH"))
            val open = agent(fixture).process(request("What tasks do I have related to the indigo resonator?"))
            assertTrue(open.message, open.message.contains("none found"))
            val date = agent(fixture).process(request("What is the due date of my task to review the indigo resonator?"))
            assertTrue(date.message, date.message.contains("due date: unavailable"))
        }
    }

    @Test fun boundedNoMatchAndMissingFieldsAreHonest(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            fixture.repository.saveTask(SavedTask("review the indigo resonator hidden match",
                metadata = RecordMetadata(createdAt = 1, updatedAt = 1)))
            repeat(21) { fixture.repository.saveTask(SavedTask("unrelated bounded decoy")) }
            fixture.cipher.decryptions = 0
            val answer = agent(fixture).process(request("Do I have a task to review the indigo resonator?"))
            assertTrue(answer.message, answer.message.contains("within the search bound"))
            assertFalse(answer.message.contains("hidden match"))
            assertTrue(fixture.cipher.decryptions <= 21)
            fixture.repository.saveMemory(SavedMemory("the indigo resonator contact details not provided"))
            val email = agent(fixture).process(request("What email address did I save for the indigo resonator?"))
            assertTrue(email.message, email.message.contains("email address: unavailable"))
            fixture.repository.saveMemory(SavedMemory("borrowing a book from Kabir SCOPE_MATCH"))
            fixture.repository.saveMemory(SavedMemory("borrowing a book from Mira SCOPE_DECOY"))
            val attributed = agent(fixture).process(request("Did I save anything about borrowing a book from Kabir?"))
            assertTrue(attributed.message, attributed.message.contains("SCOPE_MATCH"))
            assertFalse(attributed.message.contains("SCOPE_DECOY"))
        }
    }

    @Test fun exactPeopleAndPolarityDoNotReadOtherPromises(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val person = SavedPerson("Zoë O’Neil")
            fixture.repository.foundation.person.save(person)
            fixture.repository.foundation.promise.save(SavedPromise(person.metadata.id,
                "not review the indigo resonator today SCOPE_MATCH", "OPEN"))
            fixture.repository.foundation.promise.save(SavedPromise(person.metadata.id,
                "review the indigo resonator today SCOPE_DECOY", "OPEN"))
            val negative = agent(fixture).process(request("Did I tell Zoë O’Neil I would not review the indigo resonator today?"))
            assertTrue(negative.message, negative.message.contains("SCOPE_MATCH"))
            assertFalse(negative.message.contains("SCOPE_DECOY"))
            fixture.repository.foundation.person.save(SavedPerson("Zoë O’Neil"))
            val ambiguous = agent(fixture).process(request("Did I promise Zoë O’Neil to review the indigo resonator?"))
            assertEquals(AgentResultStatus.FOLLOW_UP, ambiguous.status)
            assertFalse(ambiguous.message.contains("SCOPE_MATCH"))
            val missing = agent(fixture).process(request("Did I promise Zora O’Neil to review the indigo resonator?"))
            assertEquals(AgentResultStatus.FOLLOW_UP, missing.status)
        }
        StorageTestFixture().use { fixture ->
            fixture.repository.foundation.person.save(SavedPerson("Zoë O’Neil"))
            repeat(100) { fixture.repository.foundation.person.save(SavedPerson("Synthetic person $it")) }
            fixture.cipher.decryptions = 0
            val incomplete = agent(fixture).process(request("What did I promise Zoë O’Neil?"))
            assertEquals(AgentResultStatus.FOLLOW_UP, incomplete.status)
            assertTrue(incomplete.message.contains("bound"))
            assertTrue(fixture.cipher.decryptions <= 1)
        }
    }

    private suspend fun populate(fixture: StorageTestFixture, scope: JSONObject) {
        val subject = scope.getString("subject")
        val day = scope.getString("date")
        val timestamp = if (day.isEmpty()) at.toEpochMilli() + HOUR else
            ReadDateV15(ReadDateV15.Field.valueOf(scope.getString("date_field")), day).window(at, zone).first + HOUR
        when (scope.getString("kind")) {
            "TASK" -> {
                val status = scope.getString("status").let { if (it == "ANY") TaskStatus.COMPLETED else TaskStatus.valueOf(it) }
                fixture.repository.saveTask(SavedTask("$subject SCOPE_MATCH", status = status, dueAt = timestamp,
                    dueZoneId = zone.id, completedAt = timestamp.takeIf { status == TaskStatus.COMPLETED }))
                fixture.repository.saveTask(SavedTask("unrelated SCOPE_DECOY"))
            }
            "PROMISE" -> {
                val person = SavedPerson(scope.getString("person"))
                fixture.repository.foundation.person.save(person)
                fixture.repository.foundation.promise.save(SavedPromise(person.metadata.id, "$subject SCOPE_MATCH",
                    scope.getString("status").let { if (it == "ANY") "OPEN" else it }, dueAt = timestamp))
                fixture.repository.foundation.promise.save(SavedPromise(person.metadata.id, "unrelated SCOPE_DECOY", "OPEN"))
            }
            "MEMORY" -> {
                fixture.repository.saveMemory(SavedMemory("$subject SCOPE_MATCH contact synthetic@example.test number 123",
                    metadata = RecordMetadata(createdAt = timestamp, updatedAt = timestamp)))
                fixture.repository.saveMemory(SavedMemory("unrelated SCOPE_DECOY"))
            }
            "CALENDAR" -> {
                val eventAt = if (day.isEmpty()) at.toEpochMilli() - HOUR else timestamp
                val person = scope.getString("person").let { if (it.isEmpty()) "" else "with $it " }
                val title = "meeting ${person}about $subject SCOPE_MATCH"
                fixture.repository.saveSchedule(SavedSchedule(title, eventAt, eventAt + HOUR, zone.id,
                    "Synthetic accepted event", status = ScheduleStatus.ACCEPTED))
                fixture.repository.saveSchedule(SavedSchedule("unrelated SCOPE_DECOY", eventAt, eventAt + HOUR,
                    zone.id, "Synthetic unrelated event", status = ScheduleStatus.ACCEPTED))
            }
        }
    }

    private fun agent(fixture: StorageTestFixture) = ConfirmedTaskAgent(LocalAgentReads(fixture.repository),
        fixture.repository.taskActions, zone = { zone }, localCalendar = true, readCandidate = ReadCandidate.V15)
    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, at)
    companion object { private const val HOUR = 3_600_000L }
}
