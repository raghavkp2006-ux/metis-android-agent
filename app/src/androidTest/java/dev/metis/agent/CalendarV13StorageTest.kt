package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.CalendarReadScope
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.presentation.navigation.CalendarDraft
import dev.metis.agent.presentation.navigation.buildEvent
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

/** Separate encrypted test database and key; never touches the user's records on the device. */
@RunWith(AndroidJUnit4::class)
class CalendarV13StorageTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-10-12T05:30:00Z").toEpochMilli()

    @Test fun acceptedLocalEventsMatchTimePersonTopicAndNoWrite() = runBlocking {
        StorageTestFixture().use { fixture ->
            fun instant(day: String, hour: Int) = java.time.LocalDate.parse(day).atTime(hour, 0)
                .atZone(zone).toInstant().toEpochMilli()
            val rohit = SavedSchedule("Meeting with Rohit", instant("2026-10-13", 14),
                instant("2026-10-13", 15), zone.id, "research project", status = ScheduleStatus.ACCEPTED)
            val other = SavedSchedule("Meeting with Mohit", instant("2026-10-13", 14),
                instant("2026-10-13", 15), zone.id, "unrelated", status = ScheduleStatus.ACCEPTED)
            val proposed = SavedSchedule("Meeting with Rohit proposed", instant("2026-10-13", 14),
                instant("2026-10-13", 15), zone.id, "research project", status = ScheduleStatus.PROPOSED)
            val morning = SavedSchedule("Morning research meeting", instant("2026-10-13", 9),
                instant("2026-10-13", 10), zone.id, "research project", status = ScheduleStatus.ACCEPTED)
            val mention = SavedSchedule("Planning meeting", instant("2026-10-13", 16),
                instant("2026-10-13", 17), zone.id, "Rohit mentioned this", status = ScheduleStatus.ACCEPTED)
            val appointment = SavedSchedule("Dentist appointment", instant("2026-10-13", 11),
                instant("2026-10-13", 12), zone.id, "research project", status = ScheduleStatus.ACCEPTED)
            listOf(rohit, other, proposed, morning, mention, appointment).forEach {
                fixture.repository.saveSchedule(it)
            }
            val before = fixture.repository.observeSchedules().first()
            val reads = LocalAgentReads(fixture.repository)
            suspend fun answer(scope: CalendarReadScope) =
                (reads.calendar(scope, now, zone) as SpecialistResult.Answer).message
            val afternoon = answer(CalendarReadScope(CalendarReadScope.Kind.PLANS,
                CalendarReadScope.Day.TOMORROW, CalendarReadScope.Period.AFTERNOON))
            assertTrue(afternoon.contains("Meeting with Rohit"))
            assertTrue(afternoon.contains("Meeting with Mohit"))
            assertFalse(afternoon.contains("Morning research meeting"))
            assertFalse(afternoon.contains("proposed"))
            val person = answer(CalendarReadScope(CalendarReadScope.Kind.MEETINGS_WITH_PERSON,
                CalendarReadScope.Day.THIS_WEEK, subject = "Rohit"))
            assertTrue(person.contains("Meeting with Rohit"))
            assertFalse(person.contains("Mohit"))
            assertFalse(person.contains("proposed"))
            assertFalse(person.contains("Planning meeting"))
            val topic = answer(CalendarReadScope(CalendarReadScope.Kind.MEETINGS_ABOUT_TOPIC,
                subject = "research project"))
            assertTrue(topic.contains("Meeting with Rohit"))
            assertTrue(topic.contains("Morning research meeting"))
            assertFalse(topic.contains("Meeting with Mohit"))
            assertFalse(topic.contains("proposed"))
            assertFalse(topic.contains("Dentist appointment"))
            assertTrue(topic.contains("Other calendars are not included"))
            val appointments = answer(CalendarReadScope(CalendarReadScope.Kind.APPOINTMENTS))
            assertTrue(appointments.contains("Dentist appointment"))
            assertFalse(appointments.contains("Meeting with Rohit"))
            assertEquals(before, fixture.repository.observeSchedules().first())
        }
    }

    @Test fun noMatchIsHonest() = runBlocking {
        StorageTestFixture().use { fixture ->
            val reads = LocalAgentReads(fixture.repository)
            val result = reads.calendar(CalendarReadScope(CalendarReadScope.Kind.MEETINGS), now, zone)
            val message = (result as SpecialistResult.Answer).message
            assertTrue(message.contains("none found on this device"))
            assertTrue(message.contains("Other calendars are not included"))
            assertEquals(0, fixture.cipher.decryptions)
        }
    }

    @Test fun candidateDecryptionAndDisplayedResultsStayBounded() = runBlocking {
        StorageTestFixture().use { fixture ->
            repeat(102) { index ->
                val start = now + (index + 1) * 60_000L
                fixture.repository.saveSchedule(SavedSchedule("Meeting $index", start, start + 30_000,
                    zone.id, "Synthetic calendar bound", status = ScheduleStatus.ACCEPTED))
            }
            fixture.cipher.decryptions = 0
            val message = (LocalAgentReads(fixture.repository).calendar(
                CalendarReadScope(CalendarReadScope.Kind.MEETINGS), now, zone) as SpecialistResult.Answer).message
            assertTrue(message.contains("Meeting 0"))
            assertFalse(message.contains("Meeting 5"))
            assertTrue(message.contains("bounded excerpt"))
            assertTrue(message.contains("Other calendars are not included"))
            assertEquals(203, fixture.cipher.decryptions) // 101 title/reason pairs plus one key probe.
        }
    }

    @Test fun standaloneEventEditsPreserveRevisionAndPlannerRecord() = runBlocking {
        StorageTestFixture().use { fixture ->
            val planner = SavedSchedule("Planner-owned block", now + 1, now + 2, zone.id,
                "Planner reason", status = ScheduleStatus.ACCEPTED)
            fixture.repository.saveSchedule(planner)
            val draft = CalendarDraft(title = "Synthetic appointment", date = "2026-10-13",
                start = "14:00", endDate = "2026-10-13", end = "15:00", details = "Research project")
            fixture.repository.saveSchedule(buildEvent(draft, zone))
            val original = fixture.repository.observeSchedules().first().single { it.title == draft.title }
            fixture.repository.saveSchedule(buildEvent(CalendarDraft(original).copy(title = "Updated appointment"), zone))
            val updated = fixture.repository.observeSchedules().first().single { it.metadata.id == original.metadata.id }
            assertEquals(original.metadata.revision + 1, updated.metadata.revision)
            assertEquals(original.planId, updated.planId)
            assertEquals(original.reason, updated.reason)
            fixture.repository.deleteSchedule(updated.metadata.id, updated.metadata.revision)
            assertEquals(listOf(planner), fixture.repository.observeSchedules().first())
        }
    }

    @Test fun enabledProductionAgentReadsLocalCalendarAndBlocksAppendedAction() = runBlocking {
        StorageTestFixture().use { fixture ->
            val current = Instant.now()
            val event = SavedSchedule("Synthetic meeting with Rohit", current.plusSeconds(86_400).toEpochMilli(),
                current.plusSeconds(90_000).toEpochMilli(), ZoneId.systemDefault().id,
                "Synthetic research project", status = ScheduleStatus.ACCEPTED)
            fixture.repository.saveSchedule(event)
            val agent = ConfirmedTaskAgent(LocalAgentReads(fixture.repository), fixture.repository.taskActions,
                localCalendar = true)
            fun input(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, current)
            val answer = agent.process(input("Which meetings are coming up next?"))
            assertEquals(AgentResultStatus.ANSWER, answer.status)
            assertTrue(answer.message.contains(event.title))
            assertEquals(listOf(event), fixture.repository.observeSchedules().first())
            fixture.cipher.decryptions = 0
            val unsafe = "Which meetings are coming up next and call Rohit?"
            val blocked = agent.process(input(unsafe))
            assertEquals(AgentResultStatus.FOLLOW_UP, blocked.status)
            assertTrue(blocked.message.contains(unsafe))
            assertEquals(0, fixture.cipher.decryptions)
        }
    }
}
