package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedReminder

internal class ReminderCodec(private val codec: RecordCodec) : FoundationCodec<SavedReminder, ReminderEntity> {
    override fun encode(record: SavedReminder, metadata: StoredMetadata) = ReminderEntity(
        metadata,
        codec.encrypt(record.title, "reminders", metadata.id, "title"),
        record.triggerAt,
        record.localDateTime,
        record.zoneId,
        record.precision,
        record.schedulingState,
        record.idempotencyKey,
        record.taskId,
        record.personId,
        record.platformToken,
        record.deliveredAt,
    )

    override fun decode(row: ReminderEntity): SavedReminder {
        val metadata = row.recordMetadata()
        return SavedReminder(
            title = codec.decrypt(row.title, "reminders", metadata.id, "title"),
            triggerAt = row.triggerAt,
            localDateTime = row.localDateTime,
            zoneId = row.zoneId,
            precision = row.precision,
            schedulingState = row.schedulingState,
            idempotencyKey = row.idempotencyKey,
            taskId = row.taskId,
            personId = row.personId,
            platformToken = row.platformToken,
            deliveredAt = row.deliveredAt,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
