package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.ReminderPlatform
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedReminder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Delivery is a separate durable outcome; a schedule receipt never means a notification was posted. */
internal class ReminderDelivery(private val records: ReminderActionRecords, private val platform: ReminderPlatform) {
    suspend fun deliver(reminderId: UUID): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        val claim = records.database.withTransaction {
            val reminder = records.reminder(reminderId.toString()) ?: return@withTransaction null
            if (reminder.schedulingState != "SCHEDULED") return@withTransaction null
            val originalRow = records.database.actionRun().findByKey(reminder.idempotencyKey)
                ?: return@withTransaction null
            val original = records.load(UUID.fromString(originalRow.metadata.id))
            if (original.status != "SUCCEEDED" || records.clock.millis() < reminder.triggerAt) {
                return@withTransaction original to false
            }
            check(records.sameReminder(original, reminder))
            val id = ReminderActionEncoding.deliveryId(UUID.fromString(original.metadata.id))
            if (records.database.actionRun().find(id.toString()) == null) {
                val at = records.clock.millis()
                records.repository.foundation.actionRun.save(SavedActionRun(original.requestId,
                    UUID.randomUUID().toString(), id.toString(), "CREATE_REMINDER",
                    JSONObject().put("operation", "REMINDER_DELIVERY_V1").toString(), "MEDIUM", "RUNNING", at,
                    "UNVERIFIED", entityType = "REMINDER", entityId = reminder.metadata.id,
                    metadata = RecordMetadata(id = id.toString(), createdAt = at)))
                original to true
            } else original to false
        } ?: return@withContext true
        if (claim.first.status != "SUCCEEDED" || records.clock.millis() <
            ReminderActionEncoding.data(claim.first).getLong("triggerAt")) return@withContext false
        finish(reminderId, claim.first, claim.second)
        true
    }

    private suspend fun finish(id: UUID, original: SavedActionRun, newClaim: Boolean) {
        records.database.withTransaction {
            val reminder = records.reminder(id.toString()) ?: return@withTransaction
            if (reminder.schedulingState != "SCHEDULED") return@withTransaction
            val delivery = records.load(ReminderActionEncoding.deliveryId(UUID.fromString(original.metadata.id)))
            if (delivery.status !in setOf("RUNNING", "UNKNOWN")) return@withTransaction
            val allowed = records.autonomy() == AutonomyLevel.SUGGEST && platform.notificationsAvailable()
            if (allowed && newClaim && !platform.posted(id)) platform.post(id, reminder.title)
            val posted = allowed && verifyPosted(id)
            val state = when { posted -> "FIRED"; !allowed -> "DENIED"; else -> "FAILED" }
            saveDelivery(reminder, delivery, state, posted)
        }
    }

    private suspend fun verifyPosted(id: UUID): Boolean {
        repeat(POST_CHECKS) {
            if (platform.posted(id)) return true
            delay(POST_CHECK_DELAY)
        }
        return platform.posted(id)
    }

    private suspend fun saveDelivery(reminder: SavedReminder, delivery: SavedActionRun, state: String,
        posted: Boolean) {
        val at = records.clock.millis()
        records.repository.foundation.reminder.save(reminder.copy(schedulingState = state,
            deliveredAt = if (posted) at else null))
        val result = delivery.copy(status = if (posted) "SUCCEEDED" else "UNKNOWN",
            verification = if (posted) "VERIFIED_PLATFORM" else "UNVERIFIED", finishedAt = at,
            receipt = if (posted) JSONObject().put("id", UUID.randomUUID()).put("notification",
                "POSTED").toString() else null,
            safeErrorCode = if (posted) null else "OUTCOME_UNKNOWN")
        records.repository.foundation.actionRun.save(result)
        records.audit(result, if (posted) "ALLOW" else "DENY",
            if (posted) "An active local notification was verified. This does not prove it was read."
            else "Delivery denied or uncertain; an uncertain interrupted post is not automatically repeated.")
    }
    private companion object {
        const val POST_CHECKS = 10
        const val POST_CHECK_DELAY = 50L
    }
}
