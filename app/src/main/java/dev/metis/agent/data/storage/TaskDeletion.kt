package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.UndoCapability
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

/** Deletion and its generic outcome commit together. Personal reservation/history is privacy-scrubbed. */
internal class TaskDeletion(
    private val repository: LocalPersonalRepository,
    private val database: PersonalDatabase,
    codec: RecordCodec,
) {
    private val runs = ActionRunCodec(codec)

    suspend fun completed(original: SavedActionRun): ActionReceipt? {
        val outcome = database.actionRun().find(outcomeId(original.metadata.id).toString())?.let(runs::decode)
        return outcome?.let { receipt(original, it) }
    }

    suspend fun execute(original: SavedActionRun, data: JSONObject, at: Long): ActionReceipt {
        val taskId = data.getString("taskId")
        check(database.records().taskDeletionLinks(taskId) == 0)
        repository.deleteTask(taskId, data.getLong("revision"))
        check(database.records().task(taskId) == null)
        val id = outcomeId(original.metadata.id).toString()
        val outcome = SavedActionRun(original.requestId, original.proposalId, id, "TASK",
            JSONObject().put("operation", OPERATION).put("originalActionId", original.metadata.id).toString(),
            "CRITICAL", "SUCCEEDED", original.startedAt, "VERIFIED_LOCAL", finishedAt = at,
            receipt = JSONObject().put("id", id).toString(), metadata = RecordMetadata(id = id, createdAt = at))
        repository.foundation.actionRun.save(outcome)
        repository.foundation.actionAudit.save(dev.metis.agent.domain.storage.SavedActionAudit(id, at,
            "TASK_ACTIONS_V1", 1, "{\"capabilities\":[\"LOCAL_WRITE\"]}", "ALLOW",
            "An explicitly accepted task deletion was verified. Personal task content was removed.",
            "{\"verification\":\"VERIFIED_ABSENCE\"}", data.getString("confirmationId")))
        return receipt(original, outcome)
    }

    private fun receipt(original: SavedActionRun, outcome: SavedActionRun) = ActionReceipt(
        UUID.fromString(outcome.metadata.id), ActionIdentity(UUID.fromString(original.metadata.id),
            UUID.fromString(original.requestId), UUID.fromString(original.idempotencyKey)), ActionType.TASK,
        ReceiptOutcome(ActionStatus.SUCCEEDED, Instant.ofEpochMilli(outcome.startedAt),
            Instant.ofEpochMilli(requireNotNull(outcome.finishedAt)), OutcomeVerification.VERIFIED_LOCAL,
            UndoCapability.NOT_SUPPORTED), "Task deletion verified on this device. There is no undo.")

    companion object {
        const val OPERATION = "TASK_DELETE_OUTCOME_V1"
        fun outcomeId(id: String): UUID = UUID.nameUUIDFromBytes("delete-outcome:$id".toByteArray(Charsets.UTF_8))
    }
}
