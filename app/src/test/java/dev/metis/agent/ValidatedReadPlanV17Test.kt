package dev.metis.agent

import dev.metis.agent.domain.agent.AgentDestination
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.CalendarReadScope
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.ExtractedEntity
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.NormalizedValue
import dev.metis.agent.domain.agent.ReadDateV15
import dev.metis.agent.domain.agent.ReadResourceV17
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.ScreenContext
import dev.metis.agent.domain.agent.ValidatedReadPlanV17
import dev.metis.agent.domain.storage.MemoryEntityType
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ValidatedReadPlanV17Test {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun allFourResourcesDeriveTheirIntentFromTheScope() {
        val scopes = listOf(
            ReadScopeV15.Tasks() to ReadResourceV17.TASKS,
            ReadScopeV15.Calendar() to ReadResourceV17.CALENDAR,
            ReadScopeV15.Memories("Synthetic topic") to ReadResourceV17.MEMORY,
            ReadScopeV15.Promises("Synthetic person") to ReadResourceV17.PROMISES,
        )
        val plans = scopes.map { (scope, resource) ->
            ValidatedReadPlanV17(request("Synthetic request"), zone, scope).also {
                assertEquals(resource, it.resource)
                assertSame(scope, it.scope)
            }
        }
        assertEquals(setOf(AgentIntent.CHECK_TASKS, AgentIntent.CHECK_CALENDAR,
            AgentIntent.CHECK_MEMORY, AgentIntent.WHAT_DID_I_PROMISE), plans.map { it.intent }.toSet())
    }

    @Test fun originalRequestAndItsClockContextAreRetainedWithoutNormalization() {
        val original = request("  Show  my notes about Café 📝, including the original punctuation?!  ")
        val plan = ValidatedReadPlanV17(original, zone, ReadScopeV15.Memories("Café 📝"))
        assertSame(original, plan.request)
        assertEquals("  Show  my notes about Café 📝, including the original punctuation?!  ", plan.request.text)
        assertEquals(original.timestamp, plan.request.timestamp)
        assertEquals(original.source, plan.request.source)
        assertEquals(original.screenContext, plan.request.screenContext)
        assertEquals(original.conversationId, plan.request.conversationId)
        assertEquals(zone, plan.zoneId)
    }

    @Test fun taskMemoryAndPromiseConstraintsRemainAttached() {
        val scopes = listOf(
            ReadScopeV15.Tasks("  Synthetic task  ", "COMPLETED",
                ReadDateV15(ReadDateV15.Field.COMPLETED, "Today"), "status"),
            ReadScopeV15.Memories("Synthetic literal subject", ReadDateV15(ReadDateV15.Field.CREATED, "2026-10-16"),
                "email address", negative = true),
            ReadScopeV15.Promises("Synthetic person", "Synthetic promise", "OPEN",
                ReadDateV15(ReadDateV15.Field.DUE, "tomorrow"), negative = true),
        )
        scopes.forEach { scope ->
            val plan = ValidatedReadPlanV17(request("Synthetic request"), zone, scope)
            assertSame(scope, plan.scope)
        }
        val task = ValidatedReadPlanV17(request("Synthetic request"), zone, scopes.first()).scope as ReadScopeV15.Tasks
        assertEquals("  Synthetic task  ", task.subject)
        assertEquals("Today", task.date?.expression)
    }

    @Test fun calendarKeepsBothTypedFiltersAndExistingTimePeriodRepresentation() {
        val legacy = CalendarReadScope(CalendarReadScope.Kind.MEETINGS_WITH_PERSON,
            CalendarReadScope.Day.THIS_WEEK, CalendarReadScope.Period.EVENING, "Synthetic person")
        val scope = ReadScopeV15.Calendar("Synthetic subject", "Synthetic person",
            ReadDateV15(ReadDateV15.Field.START, "this week"), meetings = true,
            legacy = legacy, appointments = true)
        val plan = ValidatedReadPlanV17(request("Synthetic request"), zone, scope)
        assertSame(scope, plan.scope)
        assertSame(legacy, (plan.scope as ReadScopeV15.Calendar).legacy)
    }

    @Test fun entitySpansAndReferencesSurviveCallerCollectionMutation() {
        val original = request("Promises to Synthetic person about notes")
        val raw = "Synthetic person"
        val start = original.text.indexOf(raw)
        val reference = EntityReference(MemoryEntityType.PERSON, UUID.randomUUID())
        val entity = ExtractedEntity(EntityKind.PERSON, raw, start, start + raw.length, 1.0,
            NormalizedValue.Reference(reference))
        val supplied = mutableListOf(entity)
        val plan = ValidatedReadPlanV17(original, zone, ReadScopeV15.Promises(raw, "notes"), supplied)
        supplied.clear()
        assertEquals(listOf(entity), plan.entities)
        assertEquals(raw, plan.request.text.substring(plan.entities.single().startOffset,
            plan.entities.single().endOffset))
        assertEquals(reference, (plan.entities.single().normalizedValue as NormalizedValue.Reference).value)
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-16T05:30:00Z"), ScreenContext(AgentDestination.AGENT), UUID.randomUUID())
}
