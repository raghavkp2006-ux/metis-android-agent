package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import dev.metis.agent.data.storage.LocalAcceptedReminderStore
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.data.storage.RecordCodec
import dev.metis.agent.data.storage.ReminderActionEncoding
import dev.metis.agent.data.storage.ReminderDelivery
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReminderPlatform
import dev.metis.agent.domain.agent.ReminderRegistration
import dev.metis.agent.domain.agent.ReminderWorkState
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.SavedUserProfile
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderStoreTest {
    @After fun cleanup() = runBlocking {
        PersonalStorage.repository(ApplicationProvider.getApplicationContext()).database.clearAllTables()
    }
    @Test fun canonicalAcceptanceSchedulesOnceWithSeparateVerifiedRegistration(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val agent = rig.agent()
            val proposal = agent.process(rig.request()).proposals.single()
            assertTrue(f.repository.foundation.reminder.observe().first().isEmpty())
            val copy = ActionProposal(proposal.id, proposal.action, proposal.risk, proposal.reason,
                proposal.evidence, true, proposal.createdAt, proposal.expiresAt)
            assertEquals(AgentResultStatus.DENIED, agent.accept(copy).status)
            assertEquals(0, rig.platform.schedules)
            val result = agent.accept(proposal)
            assertEquals(AgentResultStatus.ANSWER, result.status)
            assertEquals(AgentResultStatus.DENIED, agent.accept(proposal).status)
            val receipt = result.completedActions.single()
            assertTrue(receipt.reason.contains("Delivery may be late"))
            assertEquals(1, rig.platform.schedules)
            val reminder = f.repository.foundation.reminder.observe().first().single()
            assertEquals("SCHEDULED", reminder.schedulingState)
            assertNull(reminder.deliveredAt)
            assertEquals(0, rig.platform.posts)
            val decisions = f.repository.foundation.actionAudit.observe().first().map { it.decision }
            assertTrue(decisions.containsAll(listOf("CONFIRM", "ALLOW")))
        }
    }

    @Test fun concurrentReplayReturnsOneReceiptAndOneRegistration(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val p = rig.proposal()
            val results = listOf(async { rig.store.accept(p, UUID.randomUUID()) },
                async { rig.store.accept(p, UUID.randomUUID()) }).map { it.await() }
            assertEquals(results[0].id, results[1].id)
            assertEquals(1, rig.platform.schedules)
            assertEquals(1, f.repository.foundation.actionRun.observe().first().size)
        }
    }

    @Test fun deniedPermissionProfileExpiryAndAmbiguityHaveNoEffects(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            for (text in listOf("remind me tomorrow at 8 to synthetic check", "remind me at 08:00 to check")) {
                assertEquals(AgentResultStatus.FOLLOW_UP, rig.agent().process(rig.request(text)).status)
            }
            assertEquals(AgentResultStatus.DENIED,
                rig.agent().process(rig.request("don't remind me tomorrow at 08:00 to check")).status)
            val p = rig.proposal()
            rig.platform.available = false
            assertThrows(ActionRejectedException::class.java) { runBlocking { rig.store.accept(p, UUID.randomUUID()) } }
            rig.platform.available = true
            rig.clock.now = rig.clock.now.plusSeconds(301)
            assertThrows(ActionRejectedException::class.java) { runBlocking { rig.store.accept(p, UUID.randomUUID()) } }
            f.repository.foundation.userProfile.save(SavedUserProfile("Synthetic profile", "UTC", "en", 0, false, true))
            assertEquals(AgentResultStatus.DENIED, rig.agent().process(rig.request()).status)
            assertTrue(f.repository.foundation.reminder.observe().first().isEmpty())
            assertEquals(0, rig.platform.schedules)
        }
    }

    @Test fun interruptedEnqueueRecoversExistingRegistrationEvenAfterProposalExpiry(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val p = rig.proposal()
            rig.platform.failAfterEnqueue = true
            assertThrows(IllegalStateException::class.java) { runBlocking { rig.store.accept(p, UUID.randomUUID()) } }
            assertEquals("PENDING", f.repository.foundation.actionRun.observe().first().single().status)
            rig.platform.failAfterEnqueue = false
            rig.clock.now = rig.clock.now.plusSeconds(301)
            val reopened = rig.newStore()
            reopened.reconcile()
            assertEquals(ActionStatus.SUCCEEDED, reopened.retry(p.action.identity.id).outcome.status)
            assertEquals(1, rig.platform.schedules)
            assertEquals(0, rig.platform.posts)
        }
    }

    @Test fun expiredPendingWithoutRegistrationNeverExecutes(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val p = rig.proposal()
            rig.platform.failBeforeEnqueue = true
            assertThrows(IllegalStateException::class.java) { runBlocking { rig.store.accept(p, UUID.randomUUID()) } }
            rig.clock.now = rig.clock.now.plusSeconds(301)
            rig.platform.failBeforeEnqueue = false
            assertEquals(ActionStatus.FAILED, rig.store.retry(p.action.identity.id).outcome.status)
            assertEquals(0, rig.platform.schedules)
            assertEquals("FAILED", f.repository.foundation.reminder.observe().first().single().schedulingState)
        }
    }

    @Test fun cancelAndUndoAreIdempotentAndStopFuturePosting(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val p = rig.proposal()
            val receipt = rig.store.accept(p, UUID.randomUUID())
            val id = ReminderActionEncoding.reminderId(p.action.identity.id)
            assertEquals(UndoResult.UNDONE, rig.store.undo(receipt))
            assertEquals(UndoResult.UNDONE, rig.store.undo(receipt))
            rig.clock.now = rig.clock.now.plusSeconds(86_400)
            assertTrue(ReminderDelivery(rig.store.records, rig.platform).deliver(id))
            assertEquals(0, rig.platform.posts)
            assertEquals("CANCELLED", f.repository.foundation.reminder.observe().first().single().schedulingState)
            assertNull(rig.store.history().first().single().receipt)
        }
    }

    @Test fun changedReminderCannotBeCancelledThroughStaleReceipt(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val receipt = rig.store.accept(rig.proposal(), UUID.randomUUID())
            val reminder = f.repository.foundation.reminder.observe().first().single()
            f.repository.foundation.reminder.save(reminder.copy(title = "Synthetic changed title"))
            assertEquals(UndoResult.CONFLICT, rig.store.undo(receipt))
            assertEquals(0, rig.platform.cancels)
        }
    }

    @Test fun dueDeliveryHasIndependentReceiptAndCannotBeUndoneAsScheduling(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val p = rig.proposal()
            val receipt = rig.store.accept(p, UUID.randomUUID())
            val id = ReminderActionEncoding.reminderId(p.action.identity.id)
            val delivery = ReminderDelivery(rig.store.records, rig.platform)
            assertFalse(delivery.deliver(id))
            assertEquals(0, rig.platform.posts)
            rig.clock.now = Instant.ofEpochMilli(f.repository.foundation.reminder.observe().first().single().triggerAt)
            assertTrue(delivery.deliver(id))
            assertTrue(delivery.deliver(id))
            assertEquals(1, rig.platform.posts)
            val runs = f.repository.foundation.actionRun.observe().first()
            assertEquals(2, runs.size)
            assertTrue(runs.all { it.status == "SUCCEEDED" })
            assertEquals("FIRED", f.repository.foundation.reminder.observe().first().single().schedulingState)
            assertEquals(UndoResult.CONFLICT, rig.store.undo(receipt))
        }
    }

    @Test fun revokedPermissionAtDispatchNeverClaimsPosting(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val p = rig.proposal()
            rig.store.accept(p, UUID.randomUUID())
            rig.platform.available = false
            rig.clock.now = rig.clock.now.plusSeconds(86_400)
            ReminderDelivery(rig.store.records, rig.platform).deliver(ReminderActionEncoding.reminderId(p.action.identity.id))
            assertEquals(0, rig.platform.posts)
            assertEquals("DENIED", f.repository.foundation.reminder.observe().first().single().schedulingState)
            assertEquals("UNKNOWN", f.repository.foundation.actionRun.observe().first()
                .single { it.metadata.id == ReminderActionEncoding.deliveryId(p.action.identity.id).toString() }.status)
        }
    }

    @Test fun interruptedUnverifiablePostIsNotRepeatedOrReportedDelivered(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val p = rig.proposal()
            rig.store.accept(p, UUID.randomUUID())
            rig.clock.now = rig.clock.now.plusSeconds(86_400)
            rig.platform.failOnPost = true
            val delivery = ReminderDelivery(rig.store.records, rig.platform)
            val id = ReminderActionEncoding.reminderId(p.action.identity.id)
            assertThrows(IllegalStateException::class.java) { runBlocking { delivery.deliver(id) } }
            rig.platform.failOnPost = false
            assertTrue(delivery.deliver(id))
            assertEquals(0, rig.platform.posts)
            assertEquals("FAILED", f.repository.foundation.reminder.observe().first().single().schedulingState)
        }
    }

    @Test fun missingRegistrationIsVisibleWithoutAutomaticReplacement(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val receipt = rig.store.accept(rig.proposal(), UUID.randomUUID())
            rig.platform.registrations.clear()
            rig.store.reconcile()
            assertEquals("FAILED", rig.store.history().first().single().status)
            assertEquals(1, rig.platform.schedules)
            assertEquals(receipt.id, rig.store.retry(receipt.identity.id).id)
        }
    }

    @Test fun privacyDeletionPreventsWorkerAndScrubsPersonalPayload(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val rig = ReminderRig(f)
            val p = rig.proposal()
            rig.store.accept(p, UUID.randomUUID())
            val reminder = f.repository.foundation.reminder.observe().first().single()
            f.repository.foundation.reminder.delete(reminder.metadata.id, reminder.metadata.revision)
            rig.clock.now = rig.clock.now.plusSeconds(86_400)
            assertTrue(ReminderDelivery(rig.store.records, rig.platform).deliver(UUID.fromString(reminder.metadata.id)))
            assertEquals(0, rig.platform.posts)
            assertEquals("{}", f.repository.foundation.actionRun.observe().first().single().payload)
            assertTrue(rig.store.history().first().isEmpty())
        }
    }
}

private class ReminderRig(private val f: StorageTestFixture) {
    val clock = ReminderTestClock()
    val platform = TestReminderPlatform()
    val store = newStore()
    fun newStore() = LocalAcceptedReminderStore(f.repository, f.database, RecordCodec(f.cipher), platform, clock)
    fun agent() = ConfirmedReminderAgent(ConfirmedTaskAgent(LocalAgentReads(f.repository), f.repository.taskActions),
        store, { ZoneId.of("UTC") })
    fun request(text: String = "remind me tomorrow at 08:00 to synthetic check") =
        AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, clock.instant())
    suspend fun proposal() = agent().process(request()).proposals.single()
}

private class ReminderTestClock : Clock() {
    var now: Instant = Instant.parse("2026-10-09T10:00:00Z")
    override fun instant() = now
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
}

private class TestReminderPlatform : ReminderPlatform {
    var available = true
    var schedules = 0
    var cancels = 0
    var posts = 0
    var failBeforeEnqueue = false
    var failAfterEnqueue = false
    var failOnPost = false
    val registrations = mutableMapOf<UUID, ReminderRegistration>()
    private val posted = mutableSetOf<UUID>()
    override fun notificationsAvailable() = available
    override suspend fun registration(reminderId: UUID) = registrations[reminderId]
    override suspend fun schedule(reminderId: UUID, triggerAt: Long): ReminderRegistration {
        check(!failBeforeEnqueue)
        schedules++
        val registration = ReminderRegistration(UUID.randomUUID().toString(), ReminderWorkState.QUEUED)
        registrations[reminderId] = registration
        check(!failAfterEnqueue)
        return registration
    }
    override suspend fun cancel(reminderId: UUID) { cancels++; registrations.remove(reminderId); posted.remove(reminderId) }
    override fun posted(reminderId: UUID) = reminderId in posted
    override fun post(reminderId: UUID, title: String) { check(!failOnPost); posts++; posted.add(reminderId) }
}
