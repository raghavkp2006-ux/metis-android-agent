package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionRequirements
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.ExternalActionLauncher
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.MessageAction
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ResolvedTime
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcceptedExternalActionStoreTest {

    private class FakeLauncher : ExternalActionLauncher {
        val launchedTimers = mutableListOf<String>()
        val launchedAlarms = mutableListOf<String>()
        val launchedDials = mutableListOf<String>()
        val launchedMessages = mutableListOf<String>()

        override fun launchTimer(lengthSeconds: Int, message: String): ExternalLaunchResult {
            launchedTimers.add("$lengthSeconds:$message")
            return ExternalLaunchResult.Success
        }

        override fun launchAlarm(hour: Int, minutes: Int, message: String): ExternalLaunchResult {
            launchedAlarms.add("$hour:$minutes:$message")
            return ExternalLaunchResult.Success
        }

        override fun launchDial(phoneNumber: String): ExternalLaunchResult {
            launchedDials.add(phoneNumber)
            return ExternalLaunchResult.Success
        }

        override fun launchSendTo(phoneNumber: String, body: String): ExternalLaunchResult {
            launchedMessages.add("$phoneNumber:$body")
            return ExternalLaunchResult.Success
        }
    }

    @Test
    fun acceptedActionsPersistValidActionRunRowForAllExternalActions(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val launcher = FakeLauncher()
            val store = fixture.repository.externalActions(launcher)

            val now = Instant.now()
            val zone = ZoneId.of("UTC")
            val localAlarmTime = LocalDateTime.of(2026, 11, 1, 7, 0)
            val alarmInstant = localAlarmTime.atZone(zone).toInstant()

            val timerIdentity = ActionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            val timerAction = CreateTimerAction(timerIdentity, 300L, "Timer")
            val timerProposal = ActionProposal(
                UUID.randomUUID(), timerAction, ActionRequirements.risk(timerAction),
                "Set 300s timer", emptyList(), true, now, now.plusSeconds(300)
            )

            val alarmIdentity = ActionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            val alarmAction = CreateAlarmAction(
                alarmIdentity,
                ResolvedTime(alarmInstant, localAlarmTime, zone),
                "Alarm"
            )
            val alarmProposal = ActionProposal(
                UUID.randomUUID(), alarmAction, ActionRequirements.risk(alarmAction),
                "Set alarm", emptyList(), true, now, now.plusSeconds(300)
            )

            val callIdentity = ActionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            val callAction = CallAction(callIdentity, "Test Contact", "+15551234567")
            val callProposal = ActionProposal(
                UUID.randomUUID(), callAction, ActionRequirements.risk(callAction),
                "Call Test Contact", emptyList(), true, now, now.plusSeconds(300)
            )

            val messageIdentity = ActionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            val messageAction = MessageAction(
                messageIdentity, "Test Contact", URI.create("smsto:+15551234567"), "Running late"
            )
            val messageProposal = ActionProposal(
                UUID.randomUUID(), messageAction, ActionRequirements.risk(messageAction),
                "Message Test Contact", emptyList(), true, now, now.plusSeconds(300)
            )

            val proposals = listOf(timerProposal, alarmProposal, callProposal, messageProposal)

            for (proposal in proposals) {
                val confirmationId = UUID.randomUUID()
                val receipt = store.accept(proposal, confirmationId)
                val action = proposal.action

                val isHandoff = action is CallAction || action is MessageAction
                val expectedStatus = if (isHandoff) ActionStatus.HANDED_OFF else ActionStatus.SUCCEEDED
                val expectedVerification = if (isHandoff) OutcomeVerification.HANDOFF_ONLY
                else OutcomeVerification.VERIFIED_PLATFORM
                val expectedStorageRisk = if (isHandoff) "HIGH" else "MEDIUM"
                val expectedPayloadRisk = if (isHandoff) "R2" else "R1"

                assertEquals(expectedStatus, receipt.outcome.status)
                assertEquals(expectedVerification, receipt.outcome.verification)

                val allRuns = fixture.repository.foundation.actionRun.observe().first()
                val run = allRuns.single { it.idempotencyKey == action.identity.idempotencyKey.toString() }

                assertEquals(proposal.requestId.toString(), run.requestId)
                assertEquals(proposal.id.toString(), run.proposalId)
                assertEquals(action.type.name, run.actionType)
                assertEquals(expectedStorageRisk, run.risk)
                assertEquals(expectedStatus.name, run.status)
                assertEquals(expectedVerification.name, run.verification)
                assertNotNull(run.receipt)
                assertTrue(run.finishedAt != null && run.finishedAt!! >= run.startedAt)

                val payload = JSONObject(run.payload)
                assertEquals(action.type.name, payload.getString("actionType"))
                assertEquals(expectedPayloadRisk, payload.getString("risk"))
                assertEquals(confirmationId.toString(), payload.getString("confirmation"))

                val rawRow = fixture.database.actionRun().findByKey(action.identity.idempotencyKey.toString())
                assertNotNull(rawRow)
                assertEquals(expectedStorageRisk, rawRow!!.risk)
                assertEquals(expectedStatus.name, rawRow.status)
            }

            assertEquals(1, launcher.launchedTimers.size)
            assertEquals(1, launcher.launchedAlarms.size)
            assertEquals(1, launcher.launchedDials.size)
            assertEquals(1, launcher.launchedMessages.size)

            val audits = fixture.repository.foundation.actionAudit.observe().first()
            assertEquals(8, audits.size)
        }
    }
}
