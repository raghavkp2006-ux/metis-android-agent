package dev.metis.agent

import dev.metis.agent.data.storage.toStorageRisk
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionRequirements
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.MessageAction
import dev.metis.agent.domain.agent.ResolvedTime
import dev.metis.agent.domain.agent.RiskLevel
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExternalActionRiskTest {
    @Test
    fun riskLevelMapsToAcceptedStorageVocabulary() {
        assertEquals("LOW", RiskLevel.R0.toStorageRisk())
        assertEquals("MEDIUM", RiskLevel.R1.toStorageRisk())
        assertEquals("HIGH", RiskLevel.R2.toStorageRisk())
        assertEquals("CRITICAL", RiskLevel.R3.toStorageRisk())
    }

    @Test
    fun savedActionRunWithR1AndR2ViaToStorageRiskSucceeds() {
        val externalRiskLevels = listOf(RiskLevel.R1, RiskLevel.R2)
        val now = System.currentTimeMillis()

        for (riskLevel in externalRiskLevels) {
            val run = SavedActionRun(
                requestId = UUID.randomUUID().toString(),
                proposalId = UUID.randomUUID().toString(),
                idempotencyKey = UUID.randomUUID().toString(),
                actionType = "TIMER",
                payload = "{\"test\":true}",
                risk = riskLevel.toStorageRisk(),
                status = "PENDING",
                startedAt = now,
                verification = "UNVERIFIED",
                metadata = RecordMetadata(id = UUID.randomUUID().toString(), createdAt = now)
            )
            assertEquals(if (riskLevel == RiskLevel.R1) "MEDIUM" else "HIGH", run.risk)
        }
    }

    @Test
    fun rawRiskLevelNamesAreRejectedBySavedActionRun() {
        val now = System.currentTimeMillis()
        for (rawRisk in listOf("R0", "R1", "R2", "R3")) {
            assertThrows(IllegalArgumentException::class.java) {
                SavedActionRun(
                    requestId = UUID.randomUUID().toString(),
                    proposalId = UUID.randomUUID().toString(),
                    idempotencyKey = UUID.randomUUID().toString(),
                    actionType = "TIMER",
                    payload = "{\"test\":true}",
                    risk = rawRisk,
                    status = "PENDING",
                    startedAt = now,
                    verification = "UNVERIFIED",
                    metadata = RecordMetadata(id = UUID.randomUUID().toString(), createdAt = now)
                )
            }
        }
    }

    @Test
    fun allExternalActionsDeriveValidStorageRisk() {
        val identity = ActionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val timer = CreateTimerAction(identity, 300L, "Tea")
        val zone = ZoneId.of("UTC")
        val local = LocalDateTime.of(2026, 11, 1, 7, 0)
        val instant = local.atZone(zone).toInstant()
        val alarm = CreateAlarmAction(
            identity,
            ResolvedTime(instant, local, zone),
            "Wakeup"
        )
        val call = CallAction(identity, "Test Contact", "+15551234567")
        val message = MessageAction(identity, "Test Contact", URI.create("smsto:+15551234567"), "Hello")

        val actions = listOf(timer, alarm, call, message)
        val now = System.currentTimeMillis()

        for (action in actions) {
            val domainRisk = ActionRequirements.risk(action)
            val storageRisk = domainRisk.toStorageRisk()
            when (action) {
                is CreateTimerAction, is CreateAlarmAction -> {
                    assertEquals(RiskLevel.R1, domainRisk)
                    assertEquals("MEDIUM", storageRisk)
                }
                is CallAction, is MessageAction -> {
                    assertEquals(RiskLevel.R2, domainRisk)
                    assertEquals("HIGH", storageRisk)
                }
                else -> Unit
            }

            val run = SavedActionRun(
                requestId = action.identity.requestId.toString(),
                proposalId = UUID.randomUUID().toString(),
                idempotencyKey = action.identity.idempotencyKey.toString(),
                actionType = action.type.name,
                payload = "{\"action\":\"${action.type.name}\"}",
                risk = storageRisk,
                status = "PENDING",
                startedAt = now,
                verification = "UNVERIFIED",
                metadata = RecordMetadata(id = action.identity.id.toString(), createdAt = now)
            )
            assertEquals(storageRisk, run.risk)
        }
    }
}
