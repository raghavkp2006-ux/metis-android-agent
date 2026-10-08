package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun

internal class ActionRunCodec(private val codec: RecordCodec) : FoundationCodec<SavedActionRun, ActionRunEntity> {
    override fun encode(record: SavedActionRun, metadata: StoredMetadata) = ActionRunEntity(
        metadata,
        record.requestId,
        record.proposalId,
        record.idempotencyKey,
        record.actionType,
        codec.encryptJson(record.payload, "action_runs", metadata.id, "payload"),
        record.risk,
        record.status,
        record.startedAt,
        record.verification,
        record.finishedAt,
        record.receipt?.let { codec.encryptJson(it, "action_runs", metadata.id, "receipt") },
        record.safeErrorCode,
        record.entityType,
        record.entityId,
    )

    override fun decode(row: ActionRunEntity): SavedActionRun {
        val metadata = row.recordMetadata()
        return SavedActionRun(
            requestId = row.requestId,
            proposalId = row.proposalId,
            idempotencyKey = row.idempotencyKey,
            actionType = row.actionType,
            payload = codec.decryptJson(row.payload, "action_runs", metadata.id, "payload"),
            risk = row.risk,
            status = row.status,
            startedAt = row.startedAt,
            verification = row.verification,
            finishedAt = row.finishedAt,
            receipt = row.receipt?.let { codec.decryptJson(it, "action_runs", metadata.id, "receipt") },
            safeErrorCode = row.safeErrorCode,
            entityType = row.entityType,
            entityId = row.entityId,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
