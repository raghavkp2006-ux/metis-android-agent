package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.MemoryReadRequestValidatorV17
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

class MemoryReadRequestValidatorV17Test {
    private val validator = MemoryReadRequestValidatorV17()
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun simpleRecallPreservesEntireRequestAndMapsToMemory() {
        val original = request("  Please recall Project Phoenix?  ").copy(conversationId = UUID.randomUUID())
        val plan = valid(original).plan
        assertSame(original, plan.request)
        assertEquals(original.text, plan.request.text)
        assertEquals(zone, plan.zoneId)
        assertEquals(ReadResourceV17.MEMORY, plan.resource)
        assertEquals(AgentIntent.CHECK_MEMORY, plan.intent)
        assertEquals(ReadScopeV15.Memories("Project Phoenix"), plan.scope)
    }

    @Test fun subjectRecallFormsAndOriginalSubjectSpansAreSupported() {
        listOf("show what I remember about Project Phoenix", "check what I remember about Project Phoenix",
            "find what I remember about Project Phoenix", "find my saved memories about Project Phoenix",
            "recall a saved fact about Project Phoenix", "recall the saved topic about Project Phoenix").forEach {
            val original = request(it)
            val plan = valid(original).plan
            assertEquals("Project Phoenix", (plan.scope as ReadScopeV15.Memories).subject)
            val entity = plan.entities.single()
            assertEquals(EntityKind.MEMORY, entity.type)
            assertEquals("Project Phoenix", entity.rawValue)
            assertEquals(entity.rawValue, original.text.substring(entity.startOffset, entity.endOffset))
        }
    }

    @Test fun namedTopicIsLiteralRecallButSeparatePersonFilteringIsUnsupported() {
        val plan = valid(request("recall Alice Smith")).plan
        assertEquals(ReadScopeV15.Memories("Alice Smith"), plan.scope)
        // No person IDs or attribution/ownership predicates can be carried by Memories.
        listOf("find saved memories about Project Phoenix with Alice Smith",
            "find saved memories about Project Phoenix from Alice Smith",
            "find saved memories about Project Phoenix for person Alice Smith").forEach {
            assertReject(it, ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        }
    }

    @Test fun memoryKindFiltersAreRejectedBecauseScopeHasNoKindField() {
        listOf("find saved memories about Project Phoenix of type fact",
            "find saved memories about Project Phoenix kind preference",
            "find procedural memories about Project Phoenix").forEach {
            assertReject(it, ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        }
    }

    @Test fun existingRequestedFieldsAndCreationDatesCombineWithoutLosingConstraints() {
        val original = request("Find the saved email address for Alice Smith created on 2026-10-20")
        val plan = valid(original).plan
        assertEquals(ReadScopeV15.Memories("Alice Smith",
            ReadDateV15(ReadDateV15.Field.CREATED, "2026-10-20"), "email address"), plan.scope)
        assertEquals(listOf(EntityKind.MEMORY, EntityKind.TIME), plan.entities.map { it.type })
        plan.entities.forEach {
            assertEquals(it.rawValue, original.text.substring(it.startOffset, it.endOffset))
        }
        assertEquals("number", (valid(request("show saved number for Project Phoenix"))
            .plan.scope as ReadScopeV15.Memories).requestedField)
    }

    @Test fun supportedDateWindowsKeepSymbolicMeaningAndZone() {
        listOf(Triple("today", "2026-10-16", 1L), Triple("tomorrow", "2026-10-17", 1L),
            Triple("this week", "2026-10-12", 7L)).forEach { (expression, start, days) ->
            val original = request("find saved memories about Project Phoenix saved on $expression")
            val scope = valid(original).plan.scope as ReadScopeV15.Memories
            assertEquals(ReadDateV15(ReadDateV15.Field.CREATED, expression), scope.date)
            val window = scope.date!!.window(original.timestamp, zone)
            assertEquals(LocalDate.parse(start).atStartOfDay(zone).toInstant().toEpochMilli(), window.first)
            assertEquals(LocalDate.parse(start).plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli(), window.second)
        }
    }

    @Test fun missingSubjectOrConstraintValueClarifies() {
        listOf("recall", "recall a saved fact", "recall a saved fact about",
            "show what I remember about", "find my saved memories", "find saved number for").forEach {
            assertClarify(it, ReadClarificationReasonV17.MISSING_SUBJECT)
        }
        listOf("recall Project Phoenix saved on", "recall Project Phoenix created").forEach {
            assertClarify(it, ReadClarificationReasonV17.AMBIGUOUS_DATE)
        }
    }

    @Test fun unresolvedReferencesClarifyRatherThanUsingUnresolvedContext() {
        listOf("it", "that", "them", "their address", "the details about her").forEach {
            assertClarify("recall $it", ReadClarificationReasonV17.AMBIGUOUS_ENTITY)
        }
    }

    @Test fun memoryWritesPreferencesAndMixedRequestsReject() {
        listOf("save Project Phoenix", "remember Project Phoenix", "create a memory about Project Phoenix",
            "update memory about Project Phoenix", "change my memory", "delete my memory", "forget Project Phoenix",
            "set my preference to dark mode", "prefer short answers", "recall Project Phoenix and save it",
            "recall Project Phoenix then update it", "recall Project Phoenix; forget that").forEach {
            assertReject(it, ReadRejectionReasonV17.UNSUPPORTED_OPERATION)
        }
    }

    @Test fun unsupportedInvalidAndConflictingConstraintsRejectWithoutDroppingClauses() {
        listOf("recall Project Phoenix tagged urgent", "recall Project Phoenix before tomorrow",
            "recall Project Phoenix created whenever", "recall Project Phoenix saved frequently",
            "recall Project Phoenix saved on 2026-02-30", "recall Project Phoenix saved today and tomorrow",
            "recall Project Phoenix created on 2026-10-20 saved on 2026-10-21",
            "recall Project Phoenix saved from 2026-10-20 to 2026-10-22",
            "find saved memories about Project Phoenix only private", "recall Project Phoenix excluding budget",
            "recall Project Phoenix without travel", "find saved password for Alice Smith").forEach {
            assertReject(it, ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        }
    }

    @Test fun wrongResourcesSourcesAndUnsafeEnvelopesReject() {
        val original = request("recall Project Phoenix")
        val other = validator.validate(original, ReadResourceV17.PROMISES, zone)
        assertEquals(ReadRejectionReasonV17.UNSUPPORTED_RESOURCE, (other as ReadValidationResultV17.Reject).reason)
        assertSame(original, other.request)
        val widget = validator.validate(original.copy(source = InputSource.WIDGET), ReadResourceV17.MEMORY, zone)
        assertEquals(ReadRejectionReasonV17.UNSUPPORTED_INPUT_SOURCE, (widget as ReadValidationResultV17.Reject).reason)
        assertReject("recall Project\nPhoenix", ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        assertReject("recall " + "a".repeat(513), ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)
        assertEquals(ReadResourceV17.MEMORY, valid(original.copy(source = InputSource.VOICE)).plan.resource)
    }

    @Test fun knownV16MemoryReadStillWorksAndV17AcceptsAnIndependentForm() {
        assertEquals(AgentIntent.CHECK_MEMORY, ReadSafetyV16.plan(request("Find some record about Project Phoenix"))
            ?.parsed?.intent)
        val independent = request("Check what I remember about Project Phoenix")
        assertNull(ReadSafetyV16.plan(independent))
        assertEquals(AgentIntent.CHECK_MEMORY, valid(independent).plan.intent)
    }

    private fun valid(request: AgentRequest): ReadValidationResultV17.Valid {
        val result = validator.validate(request, ReadResourceV17.MEMORY, zone)
        assertTrue("${request.text}: $result", result is ReadValidationResultV17.Valid)
        return result as ReadValidationResultV17.Valid
    }

    private fun assertClarify(text: String, reason: ReadClarificationReasonV17) {
        val original = request(text)
        val result = validator.validate(original, ReadResourceV17.MEMORY, zone)
        assertTrue("$text: $result", result is ReadValidationResultV17.Clarify)
        assertEquals(reason, (result as ReadValidationResultV17.Clarify).reason)
        assertSame(original, result.request)
    }

    private fun assertReject(text: String, reason: ReadRejectionReasonV17) {
        val original = request(text)
        val result = validator.validate(original, ReadResourceV17.MEMORY, zone)
        assertTrue("$text: $result", result is ReadValidationResultV17.Reject)
        assertEquals(reason, (result as ReadValidationResultV17.Reject).reason)
        assertSame(original, result.request)
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-16T05:30:00Z"))
}
