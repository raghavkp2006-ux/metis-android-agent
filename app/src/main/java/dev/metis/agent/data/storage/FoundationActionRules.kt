package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.SavedActionRun

internal suspend fun validateActionUpdate(database: PersonalDatabase, codec: RecordCodec, action: SavedActionRun) {
    val old = database.actionRun().find(action.metadata.id) ?: return
    check(old.safeErrorCode != "PERSONAL_DATA_REMOVED") { "Personal data removed." }
    require(old.requestId == action.requestId && old.proposalId == action.proposalId &&
        old.idempotencyKey == action.idempotencyKey && old.actionType == action.actionType && old.risk == action.risk)
    require(codec.decrypt(old.payload, "action_runs", old.metadata.id, "payload") == action.payload)
    require(action.status in requireNotNull(ACTION_TRANSITIONS[old.status]))
}

private val ACTION_TRANSITIONS = mapOf(
    "PENDING" to setOf("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "HANDED_OFF", "UNKNOWN"),
    "RUNNING" to setOf("RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "HANDED_OFF", "UNKNOWN"),
    "UNKNOWN" to setOf("UNKNOWN", "SUCCEEDED", "FAILED", "CANCELLED", "HANDED_OFF"),
    "SUCCEEDED" to emptySet(), "FAILED" to emptySet(), "CANCELLED" to emptySet(), "HANDED_OFF" to emptySet(),
)
