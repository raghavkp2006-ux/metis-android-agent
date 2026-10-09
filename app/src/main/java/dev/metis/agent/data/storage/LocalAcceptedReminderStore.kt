package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.AcceptedReminderStore
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.ReminderPlatform
import dev.metis.agent.domain.agent.ReminderWorkState
import dev.metis.agent.domain.agent.UndoResult
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalAcceptedReminderStore internal constructor(
    repository: LocalPersonalRepository,
    database: PersonalDatabase,
    codec: RecordCodec,
    private val platform: ReminderPlatform,
    clock: Clock = Clock.systemUTC(),
) : AcceptedReminderStore {
    internal val records = ReminderActionRecords(repository, database, codec, clock)
    private val cancellation = ReminderActionCancellation(records, platform)

    override suspend fun context(request: AgentRequest) = records.context(request, platform.notificationsAvailable())
    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt =
        withContext(Dispatchers.IO) {
            records.reserve(proposal, confirmationId, platform.notificationsAvailable())
            retry(proposal.action.identity.id)
        }

    override suspend fun retry(actionId: UUID): ActionReceipt = withContext(Dispatchers.IO) {
        records.database.withTransaction {
            val run = records.load(actionId)
            val data = ReminderActionEncoding.data(run)
            if (run.status != "PENDING") return@withTransaction ReminderActionEncoding.receipt(run)
            val reminder = requireNotNull(records.reminder(requireNotNull(run.entityId)))
            check(records.sameReminder(run, reminder))
            val id = UUID.fromString(reminder.metadata.id)
            val existing = platform.registration(id)
            val registered = existing?.state in ACTIVE_WORK
            val allowed = records.autonomy() == AutonomyLevel.SUGGEST && platform.notificationsAvailable()
            val current = records.clock.millis()
            val fresh = current >= run.startedAt && current < reminder.triggerAt &&
                (registered || current < data.getLong("expiresAt"))
            if (!allowed || !fresh || reminder.schedulingState !in setOf("PENDING", "SCHEDULED")) {
                platform.cancel(id)
                return@withTransaction ReminderActionEncoding.receipt(records.fail(run, reminder,
                    if (!allowed) "DENIED" else "STALE"))
            }
            val registration = if (registered) requireNotNull(existing) else platform.schedule(id, reminder.triggerAt)
            check(registration.state in ACTIVE_WORK)
            records.repository.foundation.reminder.save(reminder.copy(schedulingState = "SCHEDULED",
                platformToken = registration.token))
            val saved = requireNotNull(records.reminder(reminder.metadata.id))
            check(saved.schedulingState == "SCHEDULED" && saved.platformToken == registration.token)
            val completed = run.copy(status = "SUCCEEDED", verification = "VERIFIED_PLATFORM", finishedAt = current,
                receipt = JSONObject().put("id", UUID.randomUUID()).put("revision", saved.metadata.revision)
                    .put("token", registration.token).toString())
            records.repository.foundation.actionRun.save(completed)
            records.audit(completed, "ALLOW",
                "Approximate background registration was read back and verified; not delivery.")
            ReminderActionEncoding.receipt(completed)
        }
    }

    override suspend fun undo(receipt: ActionReceipt) = cancellation.undo(receipt)
    override suspend fun cancel(actionId: UUID) = cancellation.cancel(actionId)
    override fun history() = ReminderHistoryMapping.observe(records.repository)

    override suspend fun reconcile(): Unit = withContext(Dispatchers.IO) {
        val runs = records.repository.foundation.actionRun.observe().first().filter {
            it.actionType == "CREATE_REMINDER" && it.safeErrorCode != "PERSONAL_DATA_REMOVED" &&
                JSONObject(it.payload).optString("operation") == ReminderActionEncoding.OPERATION
        }
        runs.forEach { run ->
            if (run.status == "PENDING") retry(UUID.fromString(run.metadata.id))
            else if (run.status == "SUCCEEDED") reconcileRegistered(UUID.fromString(run.metadata.id))
        }
    }

    private suspend fun reconcileRegistered(actionId: UUID) {
        records.database.withTransaction {
            val run = records.load(actionId)
            val reminder = run.entityId?.let { records.reminder(it) }
            if (reminder?.schedulingState == "SCHEDULED") {
                val allowed = records.autonomy() == AutonomyLevel.SUGGEST && platform.notificationsAvailable()
                val registration = platform.registration(UUID.fromString(reminder.metadata.id))
                if (!allowed || registration?.state !in ACTIVE_WORK) {
                    platform.cancel(UUID.fromString(reminder.metadata.id))
                    records.repository.foundation.reminder.save(reminder.copy(
                        schedulingState = if (allowed) "FAILED" else "DENIED"))
                    records.audit(run, "DENY",
                        "Registered reminder is no longer available; no automatic replacement was made.")
                }
            }
        }
    }
    private companion object { val ACTIVE_WORK = setOf(ReminderWorkState.QUEUED, ReminderWorkState.RUNNING) }
}
