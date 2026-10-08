package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedFocusSession

internal class FocusSessionCodec : FoundationCodec<SavedFocusSession, FocusSessionEntity> {
    override fun encode(record: SavedFocusSession, metadata: StoredMetadata) = FocusSessionEntity(
        metadata,
        record.startedAt,
        record.plannedSeconds,
        record.outcome,
        record.taskId,
        record.endedAt,
    )

    override fun decode(row: FocusSessionEntity): SavedFocusSession {
        val metadata = row.recordMetadata()
        return SavedFocusSession(
            startedAt = row.startedAt,
            plannedSeconds = row.plannedSeconds,
            outcome = row.outcome,
            taskId = row.taskId,
            endedAt = row.endedAt,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
