package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.CalendarReadRequestValidatorV17
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReadClarificationReasonV17
import dev.metis.agent.domain.agent.ReadDateV15
import dev.metis.agent.domain.agent.ReadRejectionReasonV17
import dev.metis.agent.domain.agent.ReadResourceV17
import dev.metis.agent.domain.agent.ReadSafetyV16
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.ReadValidationResultV17
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarReadRequestValidatorV17Test {
    private val validator = CalendarReadRequestValidatorV17()
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun simpleCalendarReadPreservesOriginalRequestAndMapsToCalendar() {
        val original = request("  Please show my calendar?  ")
        val result = valid(original)
        assertSame(original, result.plan.request)
        assertEquals(original.text, result.plan.request.text)
        assertEquals(zone, result.plan.zoneId)
        assertEquals(ReadResourceV17.CALENDAR, result.plan.resource)
        assertEquals(AgentIntent.CHECK_CALENDAR, result.plan.intent)
        assertEquals(ReadScopeV15.Calendar(), result.plan.scope)
    }

    @Test fun fixedAndRelativeDatesRemainSymbolicAndUseStartField() {
        listOf(Triple("on 2026-10-20", "2026-10-20", "2026-10-20"),
            Triple("today", "today", "2026-10-16"),
            Triple("tomorrow", "tomorrow", "2026-10-17"),
            Triple("this week", "this week", "2026-10-12")).forEach { (phrase, expected, start) ->
            val scope = valid(request("show my calendar events $phrase")).plan.scope as ReadScopeV15.Calendar
            assertEquals(ReadDateV15(ReadDateV15.Field.START, expected), scope.date)
            val window = scope.date!!.window(Instant.parse("2026-10-16T05:30:00Z"), zone)
            assertEquals(LocalDate.parse(start).atStartOfDay(zone).toInstant().toEpochMilli(), window.first)
            assertEquals(LocalDate.parse(start).plusDays(if (expected == "this week") 7 else 1)
                .atStartOfDay(zone).toInstant().toEpochMilli(), window.second)
        }
    }

    @Test fun titlePersonKindAndDateConstraintsRemainAttachedWithOriginalSpans() {
        val original = request("List my calendar meetings about Project Phoenix with Alice Smith on 2026-10-20")
        val result = valid(original)
        val scope = result.plan.scope as ReadScopeV15.Calendar
        assertEquals("Project Phoenix", scope.subject)
        assertEquals("Alice Smith", scope.person)
        assertEquals(ReadDateV15(ReadDateV15.Field.START, "2026-10-20"), scope.date)
        assertTrue(scope.meetings)
        assertEquals(false, scope.appointments)
        assertNull(scope.legacy)
        assertEquals(listOf(EntityKind.TITLE, EntityKind.PERSON, EntityKind.TIME),
            result.plan.entities.map { it.type })
        result.plan.entities.forEach {
            assertEquals(it.rawValue, original.text.substring(it.startOffset, it.endOffset))
        }
        val appointments = valid(request("check appointments with Alice Smith tomorrow"))
            .plan.scope as ReadScopeV15.Calendar
        assertTrue(appointments.appointments)
        assertEquals("Alice Smith", appointments.person)
    }

    @Test fun incompleteOrAmbiguousCalendarReadsClarify() {
        assertClarify("show my calendar events about", ReadClarificationReasonV17.MISSING_SUBJECT)
        assertClarify("show my calendar with", ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
        assertClarify("show my calendar on", ReadClarificationReasonV17.AMBIGUOUS_DATE)
        assertClarify("show my calendar on next Friday", ReadClarificationReasonV17.AMBIGUOUS_DATE)
        assertClarify("show my calendar with them", ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
        assertClarify("show my calendar events about it", ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
    }

    @Test fun writesAndMixedReadWriteCommandsReject() {
        listOf("create a calendar event", "update my event", "reschedule my meeting",
            "delete my calendar event", "cancel my appointment", "create a recurring event",
            "remind me about my meeting", "set a calendar notification",
            "show my calendar and create an event", "show my calendar then delete an event")
            .forEach { assertReject(it, ReadRejectionReasonV17.UNSUPPORTED_OPERATION) }
    }

    @Test fun unsupportedOrConflictingConstraintsNeverBecomeValid() {
        listOf("show my calendar on 2026-02-30", "show my calendar today and tomorrow",
            "show my calendar from 2026-10-20 to 2026-10-22", "show my calendar at 9am",
            "show recurring calendar events", "show my calendar events about Project Phoenix before tomorrow",
            "show my calendar meetings with alice", "show my calendar events with Alice Smith on 2026-10-20 after 3pm")
            .forEach { assertReject(it, ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT) }
    }

    @Test fun otherResourcesAndUnsupportedSourcesReject() {
        val original = request("show my calendar")
        val wrongResource = validator.validate(original, ReadResourceV17.TASKS, zone)
        assertEquals(ReadRejectionReasonV17.UNSUPPORTED_RESOURCE,
            (wrongResource as ReadValidationResultV17.Reject).reason)
        assertSame(original, wrongResource.request)
        val widget = validator.validate(original.copy(source = InputSource.WIDGET), ReadResourceV17.CALENDAR, zone)
        assertEquals(ReadRejectionReasonV17.UNSUPPORTED_INPUT_SOURCE,
            (widget as ReadValidationResultV17.Reject).reason)
    }

    @Test fun v16CalendarReadStillWorksAndV17ValidatesAnIndependentForm() {
        val knownV16 = request("Did I set an event for Project Phoenix?")
        assertEquals(AgentIntent.CHECK_CALENDAR, ReadSafetyV16.plan(knownV16)?.parsed?.intent)
        val v17Read = request("Check my calendar events")
        assertNull(ReadSafetyV16.plan(v17Read))
        assertEquals(ReadResourceV17.CALENDAR, valid(v17Read).plan.resource)
    }

    private fun valid(request: AgentRequest): ReadValidationResultV17.Valid {
        val result = validator.validate(request, ReadResourceV17.CALENDAR, zone)
        assertTrue("${request.text}: $result", result is ReadValidationResultV17.Valid)
        return result as ReadValidationResultV17.Valid
    }

    private fun assertClarify(text: String, expected: ReadClarificationReasonV17) {
        val original = request(text)
        val result = validator.validate(original, ReadResourceV17.CALENDAR, zone)
        assertTrue("$text: $result", result is ReadValidationResultV17.Clarify)
        assertEquals(expected, (result as ReadValidationResultV17.Clarify).reason)
        assertSame(original, result.request)
    }

    private fun assertReject(text: String, expected: ReadRejectionReasonV17) {
        val original = request(text)
        val result = validator.validate(original, ReadResourceV17.CALENDAR, zone)
        assertTrue("$text: $result", result is ReadValidationResultV17.Reject)
        assertEquals(expected, (result as ReadValidationResultV17.Reject).reason)
        assertSame(original, result.request)
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-16T05:30:00Z"))
}
