package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.FieldChange
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedTask
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

/** Closed, revision-bound edits. Prior values support guarded undo; deletion scrubs this payload. */
internal object TaskEditEncoding {
    const val OPERATION = "CONFIRMED_TASK_EDIT_V1"

    fun payload(proposal: ActionProposal, confirmation: UUID, task: SavedTask): String {
        val mutation = (proposal.action as TaskAction).mutation
        val json = JSONObject().put("operation", OPERATION).put("taskId", task.metadata.id)
            .put("revision", task.metadata.revision).put("title", task.title).put("reason", proposal.reason)
            .put("risk", proposal.risk.name).put("createdAt", proposal.createdAt.toEpochMilli())
            .put("expiresAt", proposal.expiresAt.toEpochMilli()).put("confirmationId", confirmation)
        change(json, mutation)
        return json.put("beforePriority", task.priority).put("beforeDue", task.dueAt ?: JSONObject.NULL)
            .put("beforeZone", task.dueZoneId ?: JSONObject.NULL).toString()
    }

    private fun change(json: JSONObject, mutation: TaskMutation) {
        when (mutation) {
            is TaskMutation.Delete -> json.put("kind", "DELETE")
            is TaskMutation.Postpone -> json.put("kind", "POSTPONE").put("afterDue",
                mutation.due.instant.toEpochMilli())
                .put("afterZone", mutation.due.zone.id).put("afterLocal", mutation.due.local.toString())
            is TaskMutation.Update -> {
                val patch = mutation.patch
                require(patch.due == FieldChange.Unchanged)
                if (patch.title is FieldChange.Set && patch.priority == FieldChange.Unchanged) {
                    require(patch.title.value.length <= MAX_TITLE && patch.title.value.none { it.isISOControl() })
                    json.put("kind", "RENAME").put("afterTitle", patch.title.value)
                } else {
                    require(patch.title == FieldChange.Unchanged && patch.priority is FieldChange.Set)
                    json.put("kind", "PRIORITY").put("afterPriority", patch.priority.value)
                }
            }
            else -> error("Unsupported task edit.")
        }
    }

    fun data(run: SavedActionRun): JSONObject {
        require(run.actionType == "TASK" && run.safeErrorCode != "PERSONAL_DATA_REMOVED")
        val json = JSONObject(run.payload)
        require(json.getString("operation") == OPERATION)
        val deletion = json.getString("kind") == "DELETE"
        require(json.getString("risk") == if (deletion) "R3" else "R1")
        require(run.risk == if (deletion) "CRITICAL" else "MEDIUM")
        require(run.entityType == "TASK" && run.entityId == json.getString("taskId"))
        UUID.fromString(json.getString("taskId"))
        UUID.fromString(json.getString("confirmationId"))
        require(json.getLong("revision") >= 0 && json.getString("title").isNotBlank())
        require(json.getLong("createdAt") <= run.startedAt && run.startedAt < json.getLong("expiresAt"))
        require(Math.subtractExact(json.getLong("expiresAt"), json.getLong("createdAt")) in 1..MAX_ACCEPTANCE_MILLIS)
        validateChange(json)
        return json
    }

    private fun validateChange(json: JSONObject) {
        when (json.getString("kind")) {
            "DELETE" -> Unit
            "RENAME" -> require(json.getString("afterTitle").let {
                it.isNotBlank() && it.length <= MAX_TITLE && it.none(Char::isISOControl)
            })
            "PRIORITY" -> require(json.getInt("afterPriority") in 0..MAX_PRIORITY)
            "POSTPONE" -> {
                val local = LocalDateTime.parse(json.getString("afterLocal"))
                val zone = ZoneId.of(json.getString("afterZone"))
                require(zone.rules.getValidOffsets(local).size == 1)
                require(local.atZone(zone).toInstant() == Instant.ofEpochMilli(json.getLong("afterDue")))
            }
            else -> error("Unsupported task edit.")
        }
    }

    fun matches(run: SavedActionRun, proposal: ActionProposal): Boolean {
        val action = proposal.action as TaskAction
        val json = data(run)
        val expected = JSONObject()
        change(expected, action.mutation)
        val target = dev.metis.agent.domain.agent.ActionRequirements.targets(action).single()
        return run.metadata.id == action.identity.id.toString() && run.requestId == proposal.requestId.toString() &&
            run.proposalId == proposal.id.toString() &&
                run.idempotencyKey == action.identity.idempotencyKey.toString() &&
            json.getString("reason") == proposal.reason && json.getString("risk") == proposal.risk.name &&
            json.getLong("revision") == target.expectedRevision &&
                json.getString("taskId") == target.reference.id.toString() &&
            json.getLong("createdAt") == proposal.createdAt.toEpochMilli() &&
            json.getLong("expiresAt") == proposal.expiresAt.toEpochMilli() &&
            expected.keys().asSequence().all { json.get(it) == expected.get(it) }
    }

    private const val MAX_PRIORITY = 3
    private const val MAX_TITLE = 500
    private const val MAX_ACCEPTANCE_MILLIS = 300_000L
}
