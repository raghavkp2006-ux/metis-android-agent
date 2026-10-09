package dev.metis.agent

import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentEventType
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.CalendarAction
import dev.metis.agent.domain.agent.CalendarDetails
import dev.metis.agent.domain.agent.CalendarMode
import dev.metis.agent.domain.agent.CalendarOperation
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.EntityKind
import dev.metis.agent.domain.agent.ExtractedEntity
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.IntentPrediction
import dev.metis.agent.domain.agent.MessageAction
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ParsedRequest
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedTime
import dev.metis.agent.domain.agent.RiskLevel
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.TaskPatch
import dev.metis.agent.domain.agent.UndoCapability
import dev.metis.agent.domain.storage.FoundationCatalog
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentProtocolTest {
    @Test
    fun catalogsMatchThePersistedIdentifiersAndKeepUnknownNonExecutable() {
        assertEquals(FoundationCatalog.ACTION_TYPES, ActionType.entries.map { it.name }.toSet())
        assertEquals(FoundationCatalog.EVENT_TYPES, AgentEventType.entries.map { it.name }.toSet())
        assertEquals(35, AgentIntent.entries.size)
        assertThrows(IllegalArgumentException::class.java) { ActionIdentity(id(), id(), id(), schemaVersion = 2) }
    }

    @Test
    fun extractionUsesOriginalUtf16TextAndRejectsMismatchOrSplitSurrogate() {
        val request = AgentRequest(id(), "😀 Rahul", InputSource.TEXT, Instant.EPOCH)
        val entity = ExtractedEntity(EntityKind.PERSON, "Rahul", 3, 8, 1.0)
        val prediction = IntentPrediction(AgentIntent.CHECK_PERSON, 1.0)
        assertEquals(entity, ParsedRequest(request, prediction, listOf(entity), testContext()).entities.single())
        assertThrows(IllegalArgumentException::class.java) {
            ParsedRequest(request, prediction, listOf(entity.copy(startOffset = 2, endOffset = 7)), testContext())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParsedRequest(request, prediction,
                listOf(ExtractedEntity(EntityKind.TITLE, request.text.substring(1, 2), 1, 2, 1.0)), testContext())
        }
    }

    @Test
    fun boundedTextFiniteConfidencePositiveDurationAndNonemptyPatchAreRequired() {
        assertThrows(IllegalArgumentException::class.java) { AgentRequest(id(), " ", InputSource.TEXT, Instant.EPOCH) }
        assertThrows(IllegalArgumentException::class.java) {
            AgentRequest(id(), "x".repeat(4_001), InputSource.TEXT, Instant.EPOCH)
        }
        assertThrows(IllegalArgumentException::class.java) { IntentPrediction(AgentIntent.CREATE_TASK, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { CreateTimerAction(identity(), 0, "timer") }
        assertThrows(IllegalArgumentException::class.java) { TaskPatch() }
    }

    @Test
    fun resolvedTimeRejectsDstGapAndWrongOffsetButAcceptsExplicitOverlapChoice() {
        val zone = ZoneId.of("Europe/Paris")
        assertThrows(IllegalArgumentException::class.java) {
            ResolvedTime(Instant.parse("2026-03-29T01:30:00Z"), LocalDateTime.parse("2026-03-29T02:30"), zone)
        }
        val local = LocalDateTime.parse("2026-10-25T02:30")
        zone.rules.getValidOffsets(local).forEach { offset -> ResolvedTime(local.toInstant(offset), local, zone) }
        assertThrows(IllegalArgumentException::class.java) { ResolvedTime(Instant.EPOCH, local, zone) }
    }

    @Test
    fun unsupportedExternalModesAndInjectionDestinationsCannotBeConstructed() {
        assertThrows(IllegalArgumentException::class.java) { CallAction(identity(), "person", "*21*123#") }
        assertThrows(IllegalArgumentException::class.java) {
            MessageAction(identity(), "person", URI("javascript:alert(1)"), "hello")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MessageAction(identity(), "person", URI("smsto:123456?body=unreviewed"), "hello")
        }
        val start = ResolvedTime(Instant.EPOCH, LocalDateTime.ofInstant(Instant.EPOCH, UTC), UTC)
        assertThrows(IllegalArgumentException::class.java) {
            CalendarAction(identity(), CalendarOperation.DELETE, CalendarMode.INTENT,
                CalendarDetails("event", start, start.copy(instant = Instant.ofEpochSecond(60),
                    local = start.local.plusMinutes(1))))
        }
    }

    @Test
    fun proposalCannotLowerRiskDisableConfirmationOrExceedFiveMinutes() {
        val action = TaskAction(identity(), TaskMutation.Delete(taskTarget()))
        assertThrows(IllegalArgumentException::class.java) {
            ActionProposal(id(), action, RiskLevel.R1, "reason", emptyList(), true, Instant.EPOCH,
                Instant.ofEpochSecond(300))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActionProposal(id(), action, RiskLevel.R3, "reason", emptyList(), false, Instant.EPOCH,
                Instant.ofEpochSecond(300))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActionProposal(id(), action, RiskLevel.R3, "reason", emptyList(), true, Instant.EPOCH,
                Instant.ofEpochSecond(301))
        }
    }

    @Test
    fun receiptCannotConfuseHandoffPendingOrFailureWithVerifiedSuccess() {
        assertThrows(IllegalArgumentException::class.java) {
            ReceiptOutcome(ActionStatus.SUCCEEDED, Instant.EPOCH, Instant.EPOCH, OutcomeVerification.HANDOFF_ONLY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReceiptOutcome(ActionStatus.HANDED_OFF, Instant.EPOCH, Instant.EPOCH, OutcomeVerification.VERIFIED_PLATFORM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReceiptOutcome(ActionStatus.PENDING, Instant.EPOCH, null, OutcomeVerification.VERIFIED_LOCAL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReceiptOutcome(ActionStatus.FAILED, Instant.EPOCH, Instant.EPOCH, OutcomeVerification.UNVERIFIED,
                UndoCapability.LOCAL_REVISION_CHECKED)
        }
        ReceiptOutcome(ActionStatus.HANDED_OFF, Instant.EPOCH, Instant.EPOCH, OutcomeVerification.HANDOFF_ONLY)
        assertThrows(IllegalArgumentException::class.java) {
            ActionReceipt(id(), identity(), ActionType.CALL,
                ReceiptOutcome(ActionStatus.SUCCEEDED, Instant.EPOCH, Instant.EPOCH,
                    OutcomeVerification.VERIFIED_PLATFORM), "Dialer opened")
        }
    }

    @Test
    fun resultCannotMixRequestCorrelationOrClaimProposalWithNoProposal() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentResult(id(), AgentResultStatus.PROPOSAL, "proposal")
        }
        val action = TaskAction(identity(), TaskMutation.Create("task"))
        val proposal = ActionProposal(id(), action, RiskLevel.R1, "reason", emptyList(), true,
            Instant.EPOCH, Instant.ofEpochSecond(300))
        assertThrows(IllegalArgumentException::class.java) {
            AgentResult(id(), AgentResultStatus.PROPOSAL, "proposal", listOf(proposal))
        }
    }
}
internal fun id(): UUID = UUID.randomUUID()
internal fun identity(requestId: UUID = id()) = ActionIdentity(id(), requestId, id())
private val UTC = ZoneId.of("UTC")
