package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionAudit

internal class ActionAuditCodec(private val codec: RecordCodec) : FoundationCodec<SavedActionAudit, ActionAuditEntity> {
    override fun encode(record: SavedActionAudit, metadata: StoredMetadata) = ActionAuditEntity(
        metadata.id,
        record.actionRunId,
        record.timestamp,
        record.policyVersion,
        record.autonomyLevel,
        codec.encryptJson(record.permissionSnapshotJson, "action_audit", metadata.id, "permission_snapshot_json"),
        record.decision,
        codec.encrypt(record.reason, "action_audit", metadata.id, "reason"),
        codec.encryptJson(record.evidence, "action_audit", metadata.id, "evidence"),
        record.confirmationId,
    )

    override fun decode(row: ActionAuditEntity): SavedActionAudit {
        val metadata = row.recordMetadata()
        return SavedActionAudit(
            actionRunId = row.actionRunId,
            timestamp = row.timestamp,
            policyVersion = row.policyVersion,
            autonomyLevel = row.autonomyLevel,
            permissionSnapshotJson = codec.decryptJson(row.permissionSnapshotJson, "action_audit",
                metadata.id, "permission_snapshot_json"),
            decision = row.decision,
            reason = codec.decrypt(row.reason, "action_audit", metadata.id, "reason"),
            evidence = codec.decryptJson(row.evidence, "action_audit", metadata.id, "evidence"),
            confirmationId = row.confirmationId,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
