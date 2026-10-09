package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.UndoCapability
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.SavedUserProfile
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskEditStoreTest {
    @After fun cleanup() = runBlocking {
        PersonalStorage.repository(ApplicationProvider.getApplicationContext()).database.clearAllTables()
    }
    @Test fun renameAndPriorityPreserveOtherFieldsAndSupportGuardedUndo(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val task = SavedTask("Synthetic study", notes = "Synthetic notes", priority = 2, estimatedSeconds = 600)
            f.repository.saveTask(task)
            val agent = agent(f)
            val p = agent.process(request("rename task: Synthetic study to: Synthetic exam prep!")).proposals.single()
            assertEquals(task, f.repository.observeTasks().first().single())
            val receipt = agent.accept(p).completedActions.single()
            val renamed = f.repository.observeTasks().first().single()
            assertEquals(task.copy(title = "Synthetic exam prep!", metadata = renamed.metadata), renamed)
            assertEquals(UndoResult.UNDONE, agent.undo(receipt))
            assertEquals(UndoResult.UNDONE, agent.undo(receipt))
            val priority = agent.process(request("prioritize task: Synthetic study to: 3")).proposals.single()
            val priorityReceipt = agent.accept(priority).completedActions.single()
            val prioritized = f.repository.observeTasks().first().single()
            assertEquals(task.copy(priority = 3, metadata = prioritized.metadata), prioritized)
            assertEquals(UndoResult.UNDONE, agent.undo(priorityReceipt))
            assertEquals(2, f.repository.observeTasks().first().single().priority)
        }
    }

    @Test fun postponeBindsUnambiguousFutureTimeAndRestoresPreviousDeadline(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val old = Instant.now().plusSeconds(3_600).toEpochMilli()
            f.repository.saveTask(SavedTask("Synthetic study", dueAt = old, dueZoneId = "UTC"))
            val a = agent(f)
            val p = a.process(request("postpone task: Synthetic study until tomorrow at 23:00")).proposals.single()
            val due = ((p.action as TaskAction).mutation as TaskMutation.Postpone).due
            val receipt = a.accept(p).completedActions.single()
            val saved = f.repository.observeTasks().first().single()
            assertEquals(due.instant.toEpochMilli(), saved.dueAt)
            assertEquals("UTC", saved.dueZoneId)
            assertTrue(f.repository.foundation.reminder.observe().first().isEmpty())
            assertTrue(f.repository.observeSchedules().first().isEmpty())
            assertEquals(UndoResult.UNDONE, a.undo(receipt))
            assertEquals(old, f.repository.observeTasks().first().single().dueAt)
        }
    }

    @Test fun staleProposalAndReceiptCannotOverwriteAnEdit(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val task = SavedTask("Synthetic study")
            f.repository.saveTask(task)
            val a = agent(f)
            val p = a.process(request("rename task: Synthetic study to: Synthetic new")).proposals.single()
            f.repository.saveTask(task.copy(notes = "Synthetic changed"))
            assertThrows(ActionRejectedException::class.java) {
                runBlocking { f.repository.taskActions.accept(p, UUID.randomUUID()) }
            }
            val fresh = a.process(request("prioritize task: Synthetic study to: 3")).proposals.single()
            val receipt = a.accept(fresh).completedActions.single()
            val saved = f.repository.observeTasks().first().single()
            f.repository.saveTask(saved.copy(notes = "Synthetic later edit"))
            assertEquals(UndoResult.CONFLICT, a.undo(receipt))
            assertEquals("Synthetic later edit", f.repository.observeTasks().first().single().notes)
        }
    }

    @Test fun ambiguityNegationInvalidPriorityAndAnswerOnlyCannotMutate(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic study"))
            val a = agent(f)
            assertEquals(AgentResultStatus.FOLLOW_UP,
                a.process(request("prioritize task: Synthetic study to: 4")).status)
            assertEquals(AgentResultStatus.FOLLOW_UP,
                a.process(request("postpone task: Synthetic study until tomorrow at 8")).status)
            assertEquals(AgentResultStatus.DENIED,
                a.process(request("don't delete task: Synthetic study")).status)
            f.repository.foundation.userProfile.save(SavedUserProfile("Synthetic profile", "UTC", "en", 0, false, true))
            assertEquals(AgentResultStatus.DENIED, a.process(request("delete task: Synthetic study")).status)
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    @Test fun deleteLinkedTaskIsDeniedBeforeAnyReservation(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val task = SavedTask("Synthetic study")
            f.repository.saveTask(task)
            f.repository.saveMemory(SavedMemory("Synthetic linked fact", entityType = MemoryEntityType.TASK,
                entityId = task.metadata.id))
            val a = agent(f)
            val p = a.process(request("delete task: Synthetic study")).proposals.single()
            assertEquals(AgentResultStatus.DENIED, a.accept(p).status)
            assertEquals(task, f.repository.observeTasks().first().single())
            assertEquals(1, f.repository.observeMemories().first().size)
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    @Test fun acceptedDeletionScrubsHistoryAndReplayReturnsGenericReceiptWithoutRecreatingTask(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            val a = agent(f)
            val create = a.process(request("add a task to Synthetic private study")).proposals.single()
            a.accept(create)
            val p = a.process(request("delete task: Synthetic private study")).proposals.single()
            assertTrue(p.reason.contains("no undo"))
            val receipts = listOf(async { f.repository.taskActions.accept(p, UUID.randomUUID()) },
                async { f.repository.taskActions.accept(p, UUID.randomUUID()) }).map { it.await() }
            assertEquals(receipts[0].id, receipts[1].id)
            assertEquals(ActionStatus.SUCCEEDED, receipts[0].outcome.status)
            assertEquals(UndoCapability.NOT_SUPPORTED, receipts[0].outcome.undo)
            assertTrue(f.repository.observeTasks().first().isEmpty())
            val runs = f.repository.foundation.actionRun.observe().first()
            assertTrue(runs.none { it.payload.contains("Synthetic private") })
            assertTrue(runs.filter { it.safeErrorCode == "PERSONAL_DATA_REMOVED" }.all { it.payload == "{}" })
            assertFalse(f.repository.foundation.actionAudit.observe().first().any { it.reason.contains("Synthetic private") })
            assertEquals(receipts[0].id, f.repository.taskActions.retry(p.action.identity.id).id)
            assertEquals(UndoResult.NOT_SUPPORTED, a.undo(receipts[0]))
        }
    }

    @Test fun postponeCannotMoveDeadlineEarlier(): Unit = runBlocking {
        StorageTestFixture().use { f ->
            f.repository.saveTask(SavedTask("Synthetic study", dueAt = Instant.now().plusSeconds(259_200).toEpochMilli(),
                dueZoneId = "UTC"))
            val a = agent(f)
            val p = a.process(request("postpone task: Synthetic study until tomorrow at 23:00")).proposals.single()
            assertEquals(AgentResultStatus.DENIED, a.accept(p).status)
            assertTrue(f.repository.foundation.actionRun.observe().first().isEmpty())
        }
    }

    private fun agent(f: StorageTestFixture) = ConfirmedTaskAgent(LocalAgentReads(f.repository), f.repository.taskActions,
        { ZoneId.of("UTC") })
    private fun request(text: String) = AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, Instant.now())
}
