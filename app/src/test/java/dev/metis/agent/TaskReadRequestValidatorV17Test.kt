package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReadClarificationReasonV17
import dev.metis.agent.domain.agent.ReadDateV15
import dev.metis.agent.domain.agent.ReadRejectionReasonV17
import dev.metis.agent.domain.agent.ReadResourceV17
import dev.metis.agent.domain.agent.ReadSafetyV16
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.ReadValidationResultV17
import dev.metis.agent.domain.agent.TaskReadRequestValidatorV17
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReadRequestValidatorV17Test {
    private val validator = TaskReadRequestValidatorV17()
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun simpleListingPreservesOriginalRequestAndMapsToTasks() {
        val original = request("  Please show my tasks?  ")
        val result = valid(original)
        assertSame(original, result.plan.request)
        assertEquals(original.text, result.plan.request.text)
        assertEquals(zone, result.plan.zoneId)
        assertEquals(ReadResourceV17.TASKS, result.plan.resource)
        assertEquals(AgentIntent.CHECK_TASKS, result.plan.intent)
        assertEquals(ReadScopeV15.Tasks(), result.plan.scope)
    }

    @Test fun subjectAndEntitySpanArePreserved() {
        val original = request("List my tasks about Project Phoenix")
        val result = valid(original)
        val scope = result.plan.scope as ReadScopeV15.Tasks
        assertEquals("Project Phoenix", scope.subject)
        assertEquals("Project Phoenix", result.plan.entities.single().rawValue)
        assertEquals("Project Phoenix", original.text.substring(result.plan.entities.single().startOffset,
            result.plan.entities.single().endOffset))
    }

    @Test fun supportedStatusDateAndCombinedFiltersRemainTyped() {
        assertEquals("COMPLETED", (valid(request("show completed tasks")).plan.scope as ReadScopeV15.Tasks).status)
        assertEquals("CANCELLED", (valid(request("list cancelled tasks")).plan.scope as ReadScopeV15.Tasks).status)
        assertEquals("ANY", (valid(request("list all tasks")).plan.scope as ReadScopeV15.Tasks).status)

        val due = valid(request("show my tasks due tomorrow")).plan.scope as ReadScopeV15.Tasks
        assertEquals("OPEN", due.status)
        assertEquals(ReadDateV15(ReadDateV15.Field.DUE, "tomorrow"), due.date)

        val combined = valid(request("list completed tasks about Project Phoenix due 2026-10-20"))
        val scope = combined.plan.scope as ReadScopeV15.Tasks
        assertEquals("Project Phoenix", scope.subject)
        assertEquals("COMPLETED", scope.status)
        assertEquals(ReadDateV15(ReadDateV15.Field.DUE, "2026-10-20"), scope.date)
        assertEquals(2, combined.plan.entities.size)

        val completed = valid(request("show my tasks completed on today")).plan.scope as ReadScopeV15.Tasks
        assertEquals("COMPLETED", completed.status)
        assertEquals(ReadDateV15(ReadDateV15.Field.COMPLETED, "today"), completed.date)
    }

    @Test fun missingOrAmbiguousInformationClarifies() {
        assertClarify("show my tasks about", ReadClarificationReasonV17.MISSING_SUBJECT)
        assertClarify("show my tasks due", ReadClarificationReasonV17.AMBIGUOUS_DATE)
        assertClarify("show my tasks for today", ReadClarificationReasonV17.AMBIGUOUS_SCOPE)
        assertClarify("show my tasks about it", ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
    }

    @Test fun writesAndMixedReadWriteRequestsReject() {
        listOf("create a task", "update my task", "delete my task", "complete my task",
            "postpone my task", "remind me about my tasks", "set an alarm", "start a timer",
            "show my tasks and delete them").forEach {
            assertReject(it, ReadRejectionReasonV17.UNSUPPORTED_OPERATION)
        }
    }

    @Test fun unsupportedOrConflictingConstraintsRejectWithoutDroppingThem() {
        listOf("show my tasks due yesterday", "show my tasks due 2026-02-30",
            "show my tasks created today", "show my tasks priority high",
            "show open tasks completed on today", "show all tasks completed on today",
            "show my tasks about Project Phoenix before tomorrow").forEach {
            assertReject(it, ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        }
    }

    @Test fun otherResourcesAndUnsupportedSourcesReject() {
        val original = request("show my tasks")
        val other = validator.validate(original, ReadResourceV17.CALENDAR, zone)
        assertEquals(ReadRejectionReasonV17.UNSUPPORTED_RESOURCE,
            (other as ReadValidationResultV17.Reject).reason)
        assertSame(original, other.request)
        val widget = validator.validate(original.copy(source = InputSource.WIDGET), ReadResourceV17.TASKS, zone)
        assertEquals(ReadRejectionReasonV17.UNSUPPORTED_INPUT_SOURCE,
            (widget as ReadValidationResultV17.Reject).reason)
    }

    @Test fun independentV17GrammarLeavesKnownV16TaskReadIntact() {
        val v16Known = request("Did I create a task for Project Phoenix?")
        assertEquals(AgentIntent.CHECK_TASKS, ReadSafetyV16.plan(v16Known)?.parsed?.intent)
        val v17Only = request("Check my tasks")
        assertNull(ReadSafetyV16.plan(v17Only))
        assertEquals(ReadResourceV17.TASKS, valid(v17Only).plan.resource)
    }

    private fun valid(request: AgentRequest): ReadValidationResultV17.Valid {
        val result = validator.validate(request, ReadResourceV17.TASKS, zone)
        assertTrue("${request.text}: $result", result is ReadValidationResultV17.Valid)
        return result as ReadValidationResultV17.Valid
    }

    private fun assertClarify(text: String, expected: ReadClarificationReasonV17) {
        val original = request(text)
        val result = validator.validate(original, ReadResourceV17.TASKS, zone)
        assertTrue("$text: $result", result is ReadValidationResultV17.Clarify)
        assertEquals(expected, (result as ReadValidationResultV17.Clarify).reason)
        assertSame(original, result.request)
    }

    private fun assertReject(text: String, expected: ReadRejectionReasonV17) {
        val original = request(text)
        val result = validator.validate(original, ReadResourceV17.TASKS, zone)
        assertTrue("$text: $result", result is ReadValidationResultV17.Reject)
        assertEquals(expected, (result as ReadValidationResultV17.Reject).reason)
        assertSame(original, result.request)
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-16T05:30:00Z"))
}
