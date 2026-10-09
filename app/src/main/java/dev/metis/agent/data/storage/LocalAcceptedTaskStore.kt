package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskActionHistoryEntry
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.agent.ValidationResult
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedTask
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Accepted actions are reserved durably; task + verified result + audit then commit in one transaction. */
class LocalAcceptedTaskStore internal constructor(
    private val repository: LocalPersonalRepository,
    private val database: PersonalDatabase,
    private val codec: RecordCodec,
    private val clock: Clock = Clock.systemUTC(),
) : AcceptedTaskStore {
    private val runs = ActionRunCodec(codec)

    override suspend fun context(request: AgentRequest): ResolvedContext = currentContext(request)

    private suspend fun currentContext(request: AgentRequest? = null): ResolvedContext {
        val levels = database.userProfile().autonomyLevels()
        val authority = if (levels.size > 1 || levels.singleOrNull() == 0) {
            AutonomyLevel.ANSWER_ONLY
        } else AutonomyLevel.SUGGEST
        val now = clock.instant()
        return ResolvedContext(now, ZoneId.systemDefault(), authority,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request?.screenContext, conversationId = request?.conversationId)
    }

    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt =
        withContext(Dispatchers.IO) {
            val action = proposal.action as? TaskAction ?: reject("Only plain task creation is available.")
            if (action.mutation !is TaskMutation.Create) reject("Only plain task creation is available.")
            database.withTransaction {
                val old = database.actionRun().findByKey(action.identity.idempotencyKey.toString())
                if (old != null) {
                    if (!TaskActionEncoding.matches(runs.decode(old), proposal)) reject("The accepted action changed.")
                } else {
                    val current = currentContext()
                    when (val review = ProposalPolicy().reviewProposal(proposal, current)) {
                        is ValidationResult.Invalid -> throw ActionRejectedException(review.code, review.userMessage)
                        ValidationResult.Valid -> Unit
                    }
                    val now = current.now.toEpochMilli()
                    val run = SavedActionRun(proposal.requestId.toString(), proposal.id.toString(),
                        action.identity.idempotencyKey.toString(), "TASK",
                        TaskActionEncoding.payload(proposal, confirmationId), "MEDIUM", "PENDING", now, "UNVERIFIED",
                        metadata = RecordMetadata(id = action.identity.id.toString(), createdAt = now))
                    repository.foundation.actionRun.save(run)
                    audit(run, "CONFIRM", confirmationId, "The displayed task proposal was explicitly accepted.")
                }
            }
            retry(action.identity.id)
        }

    override suspend fun retry(actionId: UUID): ActionReceipt = withContext(Dispatchers.IO) {
        database.withTransaction {
            val run = load(actionId)
            val data = TaskActionEncoding.data(run)
            if (run.status != "PENDING") return@withTransaction TaskActionEncoding.receipt(run)
            val now = currentContext()
            if (now.autonomy != AutonomyLevel.SUGGEST || now.now.toEpochMilli() < run.startedAt ||
                now.now.toEpochMilli() >= data.getLong("expiresAt")) {
                val failed = run.copy(status = "FAILED", verification = "UNVERIFIED",
                    finishedAt = maxOf(run.startedAt, now.now.toEpochMilli()),
                    safeErrorCode = if (now.autonomy != AutonomyLevel.SUGGEST) "DENIED" else "STALE")
                repository.foundation.actionRun.save(failed)
                audit(run, "DENY", UUID.fromString(data.getString("confirmationId")),
                    "Acceptance expired or current policy prevents this action.")
                return@withTransaction TaskActionEncoding.receipt(failed)
            }
            val taskId = data.getString("taskId")
            check(database.records().task(taskId) == null)
            repository.saveTask(SavedTask(data.getString("title"), metadata = RecordMetadata(
                id = taskId, createdAt = now.now.toEpochMilli())))
            val saved = requireNotNull(database.records().task(taskId))
            check(codec.decode(saved).title == data.getString("title") && saved.metadata.revision == 0L)
            val completed = run.copy(status = "SUCCEEDED", verification = "VERIFIED_LOCAL",
                finishedAt = now.now.toEpochMilli(), entityType = "TASK", entityId = taskId,
                receipt = JSONObject().put("id", UUID.randomUUID()).put("taskId", taskId)
                    .put("revision", saved.metadata.revision).toString())
            repository.foundation.actionRun.save(completed)
            audit(completed, "ALLOW", UUID.fromString(data.getString("confirmationId")),
                "Task creation was verified and committed atomically with this receipt.")
            TaskActionEncoding.receipt(completed)
        }
    }

    override suspend fun cancelPending(actionId: UUID): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
            val run = load(actionId)
            val data = TaskActionEncoding.data(run)
            if (run.status == "PENDING") {
                repository.foundation.actionRun.save(run.copy(status = "CANCELLED",
                    finishedAt = maxOf(clock.millis(), run.startedAt)))
                audit(run, "DENY", UUID.fromString(data.getString("confirmationId")),
                    "The user cancelled the pending accepted task before mutation.")
            }
        }
    }

    override suspend fun undo(receipt: ActionReceipt): UndoResult = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (!TaskReceiptBinding.undoable(receipt)) {
                return@withTransaction UndoResult.NOT_SUPPORTED
            }
            val run = load(receipt.identity.id)
            val undoId = TaskActionEncoding.undoId(receipt.identity.id)
            if (database.actionRun().find(undoId.toString()) != null) return@withTransaction UndoResult.UNDONE
            if (currentContext().autonomy != AutonomyLevel.SUGGEST) return@withTransaction UndoResult.FAILED
            if (run.safeErrorCode == "PERSONAL_DATA_REMOVED" || run.status != "SUCCEEDED") {
                return@withTransaction UndoResult.NOT_SUPPORTED
            }
            val stored = TaskActionEncoding.receipt(run)
            if (!TaskReceiptBinding.matches(stored, receipt)) {
                return@withTransaction UndoResult.CONFLICT
            }
            val taskId = requireNotNull(run.entityId)
            val task = database.records().task(taskId) ?: return@withTransaction UndoResult.CONFLICT
            val revision = JSONObject(requireNotNull(run.receipt)).getLong("revision")
            if (task.metadata.revision != revision || database.records().taskUndoLinks(taskId, run.metadata.id) != 0) {
                return@withTransaction UndoResult.CONFLICT
            }
            repository.deleteTask(taskId, revision)
            check(database.records().task(taskId) == null)
            val at = clock.millis()
            val undo = SavedActionRun(run.requestId, UUID.randomUUID().toString(), undoId.toString(), "TASK",
                JSONObject().put("operation", "UNDO_TASK_CREATE_V1").put("actionId", run.metadata.id).toString(),
                "CRITICAL", "SUCCEEDED", at, "VERIFIED_LOCAL", finishedAt = at,
                receipt = JSONObject().put("id", undoId).put("verification", "VERIFIED_LOCAL").toString(),
                metadata = RecordMetadata(id = undoId.toString(), createdAt = at))
            repository.foundation.actionRun.save(undo)
            audit(undo, "ALLOW", UUID.randomUUID(), "User-requested undo removed an unchanged, unlinked created task.")
            UndoResult.UNDONE
        }
    }

    override fun observeHistory() = repository.foundation.actionRun.observe().map { records ->
        records.filter { it.payload.contains(TaskActionEncoding.OPERATION) ||
            (it.actionType == "TASK" && it.safeErrorCode == "PERSONAL_DATA_REMOVED") }.map { run ->
            if (run.safeErrorCode == "PERSONAL_DATA_REMOVED") TaskActionHistoryEntry(UUID.fromString(run.metadata.id),
                "Personal task data removed", ActionStatus.UNKNOWN)
            else TaskActionHistoryEntry(UUID.fromString(run.metadata.id),
                TaskActionEncoding.data(run).getString("title"), ActionStatus.valueOf(run.status),
                if (run.status == "SUCCEEDED") TaskActionEncoding.receipt(run) else null)
        }
    }

    private suspend fun load(id: UUID) = runs.decode(requireNotNull(database.actionRun().find(id.toString())))

    private suspend fun audit(run: SavedActionRun, decision: String, confirmation: UUID, reason: String) {
        val at = clock.millis()
        repository.foundation.actionAudit.save(SavedActionAudit(run.metadata.id, at, "TASK_ACTIONS_V1",
            currentContext().autonomy.ordinal,
            "{\"capabilities\":[\"LOCAL_READ\",\"LOCAL_WRITE\"]}", decision, reason,
            "{\"source\":\"EXPLICIT_USER_ACCEPTANCE\"}", confirmation.toString()))
    }

    private fun reject(message: String): Nothing = throw ActionRejectedException(SafeErrorCode.DENIED, message)
}
