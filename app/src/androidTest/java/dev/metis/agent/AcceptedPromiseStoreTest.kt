package dev.metis.agent

import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.storage.SavedPerson
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptedPromiseStoreTest {
    @Test fun requestCannotWriteBeforeExplicitAcceptAndAcceptanceBindsReceiptAudit(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val agent = ConfirmedPromiseAgent(
                ConfirmedTaskAgent(LocalAgentReads(fixture.repository), fixture.repository.taskActions),
                fixture.repository.promiseActions)
            val request = AgentRequest(UUID.randomUUID(), "I promised Rahul the slides", InputSource.TEXT,
                Instant.now(), conversationId = UUID.randomUUID())
            val result = agent.process(request)
            assertEquals(AgentResultStatus.PROPOSAL, result.status)
            assertTrue(fixture.repository.foundation.promise.observe().first().isEmpty())
            assertTrue(fixture.repository.foundation.person.observe().first().isEmpty())
            assertTrue(fixture.repository.foundation.actionRun.observe().first().isEmpty())
            val accepted = agent.accept(result.proposals.single())
            assertEquals(AgentResultStatus.ANSWER, accepted.status)
            assertEquals(ActionStatus.SUCCEEDED, accepted.completedActions.single().outcome.status)
            assertEquals("Rahul", fixture.repository.foundation.person.observe().first().single().displayName)
            assertEquals("the slides", fixture.repository.foundation.promise.observe().first().single().content)
            val run = fixture.repository.foundation.actionRun.observe().first().single()
            assertEquals("CREATE_PROMISE", run.actionType)
            assertEquals("SUCCEEDED", run.status)
            assertEquals("VERIFIED_LOCAL", run.verification)
            assertEquals(2, fixture.repository.foundation.actionAudit.observe().first().size)
        }
    }

    @Test fun exactCaseInsensitivePersonMatchReusesTheExistingRow(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val existing = SavedPerson("RAHUL")
            fixture.repository.foundation.person.save(existing)
            val agent = ConfirmedPromiseAgent(
                ConfirmedTaskAgent(LocalAgentReads(fixture.repository), fixture.repository.taskActions),
                fixture.repository.promiseActions)
            val result = agent.process(AgentRequest(UUID.randomUUID(), "I owe Rahul the slides",
                InputSource.TEXT, Instant.now(), conversationId = UUID.randomUUID()))
            assertEquals(AgentResultStatus.PROPOSAL, result.status)
            assertTrue(fixture.repository.foundation.promise.observe().first().isEmpty())
            assertEquals(AgentResultStatus.ANSWER, agent.accept(result.proposals.single()).status)
            assertEquals(1, fixture.repository.foundation.person.observe().first().size)
            assertEquals(existing.metadata.id, fixture.repository.foundation.promise.observe().first().single().personId)
        }
    }
}
