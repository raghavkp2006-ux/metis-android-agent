package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.AcceptedTaskStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.RevisionTarget
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.UndoResult
import dev.metis.agent.domain.agent.ValidationResult
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedActionRun
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
    private val mutation = TaskActionMutation(repository, database, codec)
    private val deletion = TaskDeletion(repository, database, codec)

    override suspend fun context(request: AgentRequest): ResolvedContext = currentContext(request)

    override suspend fun resolveCompletion(title: String) = withContext(Dispatchers.IO) {
        database.withTransaction { mutation.resolve(title) }
    }

    private suspend fun currentContext(
        request: AgentRequest? = null, targets: List<RevisionTarget> = emptyList(),
    ): ResolvedContext {
        val levels = database.userProfile().autonomyLevels()
        val authority = if (levels.size > 1 || levels.singleOrNull() == 0) {
            AutonomyLevel.ANSWER_ONLY
        } else AutonomyLevel.SUGGEST
        val now = clock.instant()
        return ResolvedContext(now, ZoneId.systemDefault(), authority,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request?.screenContext, conversationId = request?.conversationId,
            referencedEntities = targets)
    }

    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt =
        withContext(Dispatchers.IO) {
            val action = proposal.action as? TaskAction ?: rejectTaskAction()
            database.withTransaction {
                val old = database.actionRun().findByKey(action.identity.idempotencyKey.toString())
                if (old != null) {
                    val previous = runs.decode(old)
                    if (previous.safeErrorCode != "PERSONAL_DATA_REMOVED" &&
                        !TaskMutationEncoding.matches(previous, proposal)) rejectTaskAction()
                } else {
                    val task = mutation.completionTask(proposal)
                    val current = currentContext(targets = listOfNotNull(task?.let(TaskCompletionEncoding::target)))
                    when (val review = ProposalPolicy().reviewProposal(proposal, current)) {
                        is ValidationResult.Invalid -> throw ActionRejectedException(review.code, review.userMessage)
                        ValidationResult.Valid -> Unit
                    }
                    val now = current.now.toEpochMilli()
                    val run = SavedActionRun(proposal.requestId.toString(), proposal.id.toString(),
                        action.identity.idempotencyKey.toString(), "TASK",
                        if (task == null) TaskActionEncoding.payload(proposal, confirmationId)
                        else if (action.mutation is TaskMutation.Complete)
                            TaskCompletionEncoding.payload(proposal, confirmationId, task)
                        else TaskEditEncoding.payload(proposal, confirmationId, task),
                        if (action.mutation is TaskMutation.Delete) "CRITICAL" else "MEDIUM", "PENDING", now,
                            "UNVERIFIED",
                        entityType = task?.let { "TASK" }, entityId = task?.metadata?.id,
                        metadata = RecordMetadata(id = action.identity.id.toString(), createdAt = now))
                    if (action.mutation !is TaskMutation.Create &&
                        !mutation.validPending(TaskMutationEncoding.data(run), now)) {
                        throw ActionRejectedException(SafeErrorCode.STALE,
                            "The task is linked, changed, or the new deadline does not postpone it. Request a fresh " +
                                "proposal.")
                    }
                    repository.foundation.actionRun.save(run)
                    audit(run, "CONFIRM", confirmationId, "The displayed task proposal was explicitly accepted.")
                }
            }
            retry(action.identity.id)
        }

    override suspend fun retry(actionId: UUID): ActionReceipt = withContext(Dispatchers.IO) {
        database.withTransaction {
            val run = load(actionId)
            deletion.completed(run)?.let { return@withTransaction it }
            val data = TaskMutationEncoding.data(run)
            if (run.status != "PENDING") return@withTransaction TaskActionEncoding.receipt(run)
            val now = currentContext()
            val eligible = now.now.toEpochMilli() >= run.startedAt &&
                now.now.toEpochMilli() < data.getLong("expiresAt") && mutation.validPending(data,
                    now.now.toEpochMilli())
            if (now.autonomy != AutonomyLevel.SUGGEST || !eligible) {
                val failed = run.copy(status = "FAILED", verification = "UNVERIFIED",
                    finishedAt = maxOf(run.startedAt, now.now.toEpochMilli()),
                    safeErrorCode = if (now.autonomy != AutonomyLevel.SUGGEST) "DENIED" else "STALE")
                repository.foundation.actionRun.save(failed)
                audit(run, "DENY", UUID.fromString(data.getString("confirmationId")),
                    "Acceptance expired, task changed or current policy prevents this action.")
                return@withTransaction TaskActionEncoding.receipt(failed)
            }
            if (data.optString("kind") == "DELETE") return@withTransaction deletion.execute(run, data,
                now.now.toEpochMilli())
            val taskId = data.getString("taskId")
            val saved = mutation.execute(data, now.now.toEpochMilli())
            val completed = run.copy(status = "SUCCEEDED", verification = "VERIFIED_LOCAL",
                finishedAt = now.now.toEpochMilli(), entityType = "TASK", entityId = taskId,
                receipt = JSONObject().put("id", UUID.randomUUID()).put("taskId", taskId)
                    .put("revision", saved.metadata.revision).toString())
            repository.foundation.actionRun.save(completed)
            audit(completed, "ALLOW", UUID.fromString(data.getString("confirmationId")),
                "Task mutation was verified and committed atomically with this receipt.")
            TaskActionEncoding.receipt(completed)
        }
    }

    override suspend fun cancelPending(actionId: UUID): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
            val run = load(actionId)
            deletion.completed(run)?.let { return@withTransaction }
            val data = TaskMutationEncoding.data(run)
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
            if (task.metadata.revision != revision || !mutation.undo(run, codec.decode(task))) {
                return@withTransaction UndoResult.CONFLICT
            }
            val at = clock.millis()
            val operation = JSONObject(run.payload).getString("operation")
            val preservesTask = operation != TaskActionEncoding.OPERATION
            val undo = SavedActionRun(run.requestId, UUID.randomUUID().toString(), undoId.toString(), "TASK",
                JSONObject().put("operation", "UNDO_$operation")
                    .put("actionId", run.metadata.id).toString(),
                "CRITICAL", "SUCCEEDED", at, "VERIFIED_LOCAL", finishedAt = at,
                entityType = if (preservesTask) "TASK" else null,
                entityId = if (preservesTask) taskId else null,
                receipt = JSONObject().put("id", undoId).put("verification", "VERIFIED_LOCAL").toString(),
                metadata = RecordMetadata(id = undoId.toString(), createdAt = at))
            repository.foundation.actionRun.save(undo)
            audit(undo, "ALLOW", UUID.randomUUID(), "User-requested task undo was verified locally.")
            UndoResult.UNDONE
        }
    }

    override fun observeHistory() = repository.foundation.actionRun.observe().map(TaskActionHistoryMapping::entries)

    private suspend fun load(id: UUID) = runs.decode(requireNotNull(database.actionRun().find(id.toString())))

    private suspend fun audit(run: SavedActionRun, decision: String, confirmation: UUID, reason: String) {
        val at = clock.millis()
        repository.foundation.actionAudit.save(SavedActionAudit(run.metadata.id, at, "TASK_ACTIONS_V1",
            currentContext().autonomy.ordinal,
            "{\"capabilities\":[\"LOCAL_READ\",\"LOCAL_WRITE\"]}", decision, reason,
            "{\"source\":\"EXPLICIT_USER_ACCEPTANCE\"}", confirmation.toString()))
    }

}

private fun rejectTaskAction(): Nothing = throw ActionRejectedException(SafeErrorCode.DENIED,
    "Only supported, unchanged task proposals can be accepted.")
