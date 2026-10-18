package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReadClarificationReasonV17
import dev.metis.agent.domain.agent.ReadRejectionReasonV17
import dev.metis.agent.domain.agent.ReadRequestValidatorV17
import dev.metis.agent.domain.agent.ReadResourceV17
import dev.metis.agent.domain.agent.ReadSafetyV16
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.ReadValidationResultV17
import dev.metis.agent.domain.agent.ValidatedReadPlanV17
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadRequestValidatorV17Test {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun validCarriesPlanAndCompleteOriginalRequest() {
        val original = request("  Synthetic task?  ")
        val plan = ValidatedReadPlanV17(original, zone, ReadScopeV15.Tasks("Synthetic task"))
        val result: ReadValidationResultV17 = ReadValidationResultV17.Valid(plan)

        assertSame(plan, (result as ReadValidationResultV17.Valid).plan)
        assertSame(original, result.request)
        assertEquals(original.text, result.request.text)
        assertEquals(ReadResourceV17.TASKS, result.proposedResource)
    }

    @Test fun clarifyAndRejectAreDistinctAndKeepMachineReadableReasons() {
        val original = request("Synthetic incomplete request")
        val clarify: ReadValidationResultV17 = ReadValidationResultV17.Clarify(
            original, ReadResourceV17.CALENDAR, ReadClarificationReasonV17.AMBIGUOUS_DATE)
        val reject: ReadValidationResultV17 = ReadValidationResultV17.Reject(
            original, ReadResourceV17.CALENDAR, ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT)

        assertTrue(clarify is ReadValidationResultV17.Clarify)
        assertTrue(reject is ReadValidationResultV17.Reject)
        assertSame(original, clarify.request)
        assertSame(original, reject.request)
        assertEquals(ReadClarificationReasonV17.AMBIGUOUS_DATE,
            (clarify as ReadValidationResultV17.Clarify).reason)
        assertEquals(ReadRejectionReasonV17.UNSUPPORTED_CONSTRAINT,
            (reject as ReadValidationResultV17.Reject).reason)
    }

    @Test fun contractAcceptsOnlyFourKnownReadResourcesAndNoConfidence() {
        assertEquals(setOf(AgentIntent.CHECK_TASKS, AgentIntent.CHECK_CALENDAR,
            AgentIntent.CHECK_MEMORY, AgentIntent.WHAT_DID_I_PROMISE),
            ReadResourceV17.entries.map { it.intent }.toSet())
        assertTrue(ReadResourceV17.entries.none { it.intent == AgentIntent.UNKNOWN })

        val original = request("Synthetic request")
        val validator = object : ReadRequestValidatorV17 {
            override fun validate(request: AgentRequest, proposedResource: ReadResourceV17,
                                  zoneId: ZoneId): ReadValidationResultV17 =
                ReadValidationResultV17.Clarify(request, proposedResource,
                    ReadClarificationReasonV17.INCOMPLETE_REQUEST)
        }
        val result = validator.validate(original, ReadResourceV17.MEMORY, zone)
        assertSame(original, result.request)
        assertEquals(ReadResourceV17.MEMORY, result.proposedResource)
    }

    @Test fun unchangedV16StillRecognizesKnownReadAndRejectsUnrelatedAction() {
        val known = ReadSafetyV16.plan(request("Did I create a task for synthetic notes?"))
        assertEquals(AgentIntent.CHECK_TASKS, known?.parsed?.intent)
        assertNull(ReadSafetyV16.plan(request("Send an email now")))
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT,
        Instant.parse("2026-10-16T05:30:00Z"))
}
