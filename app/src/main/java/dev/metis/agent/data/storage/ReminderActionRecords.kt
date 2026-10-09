package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.CreateReminderAction
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.ValidationResult
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedReminder
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

internal class ReminderActionRecords(
    val repository: LocalPersonalRepository,
    val database: PersonalDatabase,
    codec: RecordCodec,
    val clock: Clock,
) {
    private val runs = ActionRunCodec(codec)
    private val reminders = ReminderCodec(codec)

    suspend fun load(id: UUID) = runs.decode(requireNotNull(database.actionRun().find(id.toString())))
    suspend fun reminder(id: String) = database.reminder().find(id)?.let(reminders::decode)
    suspend fun autonomy(): AutonomyLevel {
        val levels = database.userProfile().autonomyLevels()
        return if (levels.size > 1 || levels.singleOrNull() == 0) AutonomyLevel.ANSWER_ONLY else AutonomyLevel.SUGGEST
    }

    suspend fun context(request: AgentRequest, notifications: Boolean): ResolvedContext {
        val now = clock.instant()
        return ResolvedContext(now, ZoneId.systemDefault(), autonomy(), CapabilitySnapshot(now,
            setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE, Capability.REMINDER_SCHEDULE) +
                if (notifications) setOf(Capability.NOTIFICATIONS) else emptySet()),
            screenContext = request.screenContext, conversationId = request.conversationId)
    }

    suspend fun reserve(proposal: ActionProposal, confirmation: UUID, available: Boolean) {
        require(proposal.action is CreateReminderAction)
        database.withTransaction {
            val old = database.actionRun().findByKey(proposal.action.identity.idempotencyKey.toString())
            if (old != null) {
                require(ReminderActionEncoding.matches(runs.decode(old), proposal))
            } else {
                val request = AgentRequest(proposal.requestId, "Accepted reminder",
                    dev.metis.agent.domain.agent.InputSource.TEXT,
                    clock.instant())
                when (val result = ProposalPolicy().reviewProposal(proposal, context(request, available))) {
                    is ValidationResult.Invalid -> throw ActionRejectedException(result.code, result.userMessage)
                    ValidationResult.Valid -> Unit
                }
                val data = JSONObject(ReminderActionEncoding.payload(proposal, confirmation))
                if (data.getLong("triggerAt") <= clock.millis()) {
                    throw ActionRejectedException(SafeErrorCode.STALE,
                        "The reminder time has passed. Request a new time.")
                }
                val id = ReminderActionEncoding.reminderId(proposal.action.identity.id).toString()
                val at = clock.millis()
                repository.foundation.reminder.save(SavedReminder(data.getString("title"), data.getLong("triggerAt"),
                    data.getString("local"), data.getString("zone"), "APPROXIMATE", "PENDING",
                    proposal.action.identity.idempotencyKey.toString(), metadata = RecordMetadata(id = id,
                        createdAt = at)))
                val run = SavedActionRun(proposal.requestId.toString(), proposal.id.toString(),
                    proposal.action.identity.idempotencyKey.toString(), "CREATE_REMINDER", data.toString(), "MEDIUM",
                    "PENDING", at, "UNVERIFIED", entityType = "REMINDER", entityId = id,
                    metadata = RecordMetadata(id = proposal.action.identity.id.toString(), createdAt = at))
                repository.foundation.actionRun.save(run)
                audit(run, "CONFIRM", "The user explicitly accepted approximate reminder scheduling.")
            }
        }
    }

    suspend fun audit(run: SavedActionRun, decision: String, reason: String) {
        val confirmation = JSONObject(run.payload).optString("confirmation").takeIf { it.isNotBlank() }
        repository.foundation.actionAudit.save(SavedActionAudit(run.metadata.id, clock.millis(), "REMINDER_ACTIONS_V1",
            autonomy().ordinal, "{\"precision\":\"APPROXIMATE\"}", decision, reason,
            "{\"source\":\"LOCAL_PLATFORM_CHECK\"}", confirmation))
    }

    suspend fun fail(run: SavedActionRun, reminder: SavedReminder, code: String): SavedActionRun {
        val state = if (code == "DENIED") "DENIED" else "FAILED"
        repository.foundation.reminder.save(reminder.copy(schedulingState = state))
        val failed = run.copy(status = "FAILED", safeErrorCode = code, finishedAt = maxOf(run.startedAt,
            clock.millis()))
        repository.foundation.actionRun.save(failed)
        audit(failed, "DENY", "No verified reminder registration: expired, unavailable or denied by current policy.")
        return failed
    }

    fun sameReminder(run: SavedActionRun, reminder: SavedReminder): Boolean {
        val data = ReminderActionEncoding.data(run)
        return reminder.metadata.id == run.entityId && reminder.idempotencyKey == run.idempotencyKey &&
            reminder.title == data.getString("title") && reminder.triggerAt == data.getLong("triggerAt") &&
            reminder.localDateTime == data.getString("local") && reminder.zoneId == data.getString("zone") &&
            reminder.precision == "APPROXIMATE" && reminder.taskId == null && reminder.personId == null
    }
}
