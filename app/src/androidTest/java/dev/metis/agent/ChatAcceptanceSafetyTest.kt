package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.data.storage.LocalPlannerStore
import dev.metis.agent.data.storage.RecordCodec
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.planner.PlannerAgent
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.Workspace
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatAcceptanceSafetyTest {
    private val at = Instant.parse("2026-10-09T07:00:00Z")
    private val zone = ZoneId.of("UTC")

    @Test fun chatTaskProposalWritesOnlyAfterCanonicalAcceptAndRecordsOutcome(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val agent = ConfirmedTaskAgent(LocalAgentReads(fixture.repository), fixture.repository.taskActions,
                { zone })
            val result = agent.process(request("add a task to Synthetic chat task"))
            assertEquals(AgentResultStatus.PROPOSAL, result.status)
            assertEquals(1, result.proposals.size)
            assertTrue(result.completedActions.isEmpty())
            assertNoAcceptedAction(fixture)

            val proposal = result.proposals.single()
            val copy = ActionProposal(proposal.id, proposal.action, proposal.risk, proposal.reason,
                proposal.evidence, proposal.requiresConfirmation, proposal.createdAt, proposal.expiresAt)
            assertEquals(AgentResultStatus.DENIED, agent.accept(copy).status)
            assertNoAcceptedAction(fixture)

            val accepted = agent.accept(proposal)
            assertEquals(AgentResultStatus.ANSWER, accepted.status)
            val receipt = accepted.completedActions.single()
            assertEquals(listOf("Synthetic chat task"), fixture.repository.observeTasks().first().map { it.title })
            assertEquals(1, fixture.repository.foundation.actionRun.observe().first().size)
            val audits = fixture.repository.foundation.actionAudit.observe().first()
            assertEquals(2, audits.size)
            assertEquals(setOf("CONFIRM", "ALLOW"), audits.map { it.decision }.toSet())
            val event = fixture.repository.foundation.event.observe().first().single()
            assertEquals("TASK_CREATED", event.type)
            assertEquals(receipt.identity.id.toString(), event.actionId)
            val denied = agent.accept(proposal)
            assertEquals(AgentResultStatus.DENIED, denied.status)
            assertTrue(denied.completedActions.isEmpty())
            assertEquals(1, fixture.repository.foundation.actionRun.observe().first().size)
        }
    }

    @Test fun chatPlanAcceptRejectsOverlapAddedAfterProposalWithoutReceiptOrAudit(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            fixture.repository.saveTask(SavedTask("Synthetic planned task", estimatedSeconds = 600))
            val planner = LocalPlannerStore(fixture.repository, RecordCodec(fixture.cipher),
                Clock.fixed(at, zone), { zone })
            val agent = PlannerAgent(ConfirmedTaskAgent(LocalAgentReads(fixture.repository),
                fixture.repository.taskActions, { zone }), planner)
            val result = agent.process(request("plan my day tomorrow from 09:00 to 12:00"))
            assertEquals(AgentResultStatus.PROPOSAL, result.status)
            val proposal = result.proposals.single()
            assertTrue(fixture.repository.observeSchedules().first().isEmpty())
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())

            val block = requireNotNull(planner.prepare(request("plan my day tomorrow from 09:00 to 12:00")).draft)
                .blocks.single()
            val competing = SavedSchedule("Synthetic new commitment", block.startAt + 60_000,
                block.endAt, "UTC", "Busy", status = ScheduleStatus.ACCEPTED)
            fixture.repository.saveSchedule(competing)

            val denied = agent.accept(proposal)
            assertEquals(AgentResultStatus.DENIED, denied.status)
            assertTrue(denied.completedActions.isEmpty())
            assertEquals(listOf(competing), fixture.repository.observeSchedules().first())
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
            assertTrue(fixture.repository.foundation.actionAudit.observe().first().isEmpty())
            assertTrue(fixture.repository.foundation.event.observe().first().isEmpty())
        }
    }

    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, at,
        ConversationOrigin(Workspace.CHAT).screenContext(), UUID.randomUUID())

    private suspend fun assertNoAcceptedAction(fixture: StorageTestFixture) {
        assertTrue(fixture.repository.observeTasks().first().isEmpty())
        assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
        assertTrue(fixture.repository.foundation.actionAudit.observe().first().isEmpty())
        assertTrue(fixture.repository.foundation.event.observe().first().isEmpty())
    }
}
