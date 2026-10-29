package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.AcceptedExternalActionStore
import dev.metis.agent.domain.agent.Action
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.ActionRequirements
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.CallAction
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.CreateAlarmAction
import dev.metis.agent.domain.agent.CreateTimerAction
import dev.metis.agent.domain.agent.ExternalActionLauncher
import dev.metis.agent.domain.agent.ExternalLaunchResult
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.MessageAction
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.ValidationResult
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedActionRun
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Suppress("TooManyFunctions")
class LocalAcceptedExternalActionStore internal constructor(
    private val repository: LocalPersonalRepository,
    private val database: PersonalDatabase,
    codec: RecordCodec,
    private val launcher: ExternalActionLauncher,
    private val clock: Clock = Clock.systemUTC(),
) : AcceptedExternalActionStore {
    private val runs = ActionRunCodec(codec)

    override suspend fun context(request: AgentRequest): ResolvedContext {
        val levels = database.userProfile().autonomyLevels()
        val authority = if (levels.size > 1 || levels.singleOrNull() == 0) AutonomyLevel.ANSWER_ONLY
        else AutonomyLevel.SUGGEST
        val now = clock.instant()
        return ResolvedContext(
            now,
            ZoneId.systemDefault(),
            authority,
            CapabilitySnapshot(
                now,
                setOf(
                    Capability.LOCAL_READ,
                    Capability.LOCAL_WRITE,
                    Capability.TIMER,
                    Capability.CLOCK_ALARM,
                    Capability.DIALER,
                    Capability.MESSAGE_COMPOSER
                )
            ),
            screenContext = request.screenContext,
            conversationId = request.conversationId
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val action = proposal.action
                val old = database.actionRun().findByKey(action.identity.idempotencyKey.toString())
                if (old != null) {
                    val existing = runs.decode(old)
                    return@withTransaction receipt(existing, action)
                }

                val current = context(AgentRequest(UUID.randomUUID(), "accepted external action",
                    InputSource.TEXT, clock.instant()))
                when (val review = ProposalPolicy().reviewProposal(proposal, current)) {
                    is ValidationResult.Invalid -> throw ActionRejectedException(review.code, review.userMessage)
                    ValidationResult.Valid -> Unit
                }

                val now = clock.millis()
                val pending = SavedActionRun(
                    requestId = proposal.requestId.toString(),
                    proposalId = proposal.id.toString(),
                    idempotencyKey = action.identity.idempotencyKey.toString(),
                    actionType = action.type.name,
                    payload = payload(proposal, confirmationId.toString()),
                    risk = ActionRequirements.risk(action).name,
                    status = "PENDING",
                    startedAt = now,
                    verification = "UNVERIFIED",
                    metadata = RecordMetadata(id = action.identity.id.toString(), createdAt = now)
                )
                repository.foundation.actionRun.save(pending)
                audit(pending, "CONFIRM", confirmationId, "The displayed action was explicitly accepted.")

                val launchResult = when (action) {
                    is CreateTimerAction -> launcher.launchTimer(action.durationSeconds.toInt(), action.label)
                    is CreateAlarmAction -> launcher.launchAlarm(action.trigger.local.hour,
                        action.trigger.local.minute, action.label)
                    is CallAction -> launcher.launchDial(action.phoneNumber)
                    is MessageAction -> launcher.launchSendTo(action.destination.schemeSpecificPart, action.body)
                    else -> ExternalLaunchResult.Failed("Unsupported action type ${action.type}")
                }

                val outcomeTuple = outcomeFor(action, launchResult)
                val status = outcomeTuple.status
                val verification = outcomeTuple.verification
                val reason = outcomeTuple.reason
                val safeErrorCode = outcomeTuple.safeErrorCode

                val receiptId = UUID.randomUUID()
                val completed = pending.copy(
                    status = status.name,
                    verification = verification.name,
                    finishedAt = clock.millis(),
                    safeErrorCode = safeErrorCode?.name,
                    receipt = JSONObject().put("id", receiptId.toString()).put("reason", reason).toString()
                )
                repository.foundation.actionRun.save(completed)
                val auditDecision = if (status == ActionStatus.FAILED) "DENY" else "ALLOW"
                audit(completed, auditDecision, confirmationId, reason)
                receipt(completed, action, receiptId, reason, status, verification, safeErrorCode)
            }
        }

    private data class OutcomeData(
        val status: ActionStatus,
        val verification: OutcomeVerification,
        val reason: String,
        val safeErrorCode: SafeErrorCode?,
    )

    private fun outcomeFor(action: Action, result: ExternalLaunchResult): OutcomeData = when (result) {
        is ExternalLaunchResult.Success -> when (action) {
            is CallAction -> OutcomeData(
                ActionStatus.HANDED_OFF,
                OutcomeVerification.HANDOFF_ONLY,
                "Opened dialer for ${action.targetLabel} (${action.phoneNumber}).",
                null
            )
            is MessageAction -> OutcomeData(
                ActionStatus.HANDED_OFF,
                OutcomeVerification.HANDOFF_ONLY,
                "Opened message composer for ${action.recipient}.",
                null
            )
            is CreateTimerAction -> OutcomeData(
                ActionStatus.SUCCEEDED,
                OutcomeVerification.VERIFIED_PLATFORM,
                "Started timer for ${action.durationSeconds} seconds.",
                null
            )
            is CreateAlarmAction -> OutcomeData(
                ActionStatus.SUCCEEDED,
                OutcomeVerification.VERIFIED_PLATFORM,
                "Set alarm for ${action.trigger.local.toLocalTime()}.",
                null
            )
            else -> OutcomeData(ActionStatus.FAILED, OutcomeVerification.UNVERIFIED,
                "Unsupported action.", SafeErrorCode.UNSUPPORTED)
        }
        is ExternalLaunchResult.NoHandler -> OutcomeData(
            ActionStatus.FAILED,
            OutcomeVerification.UNVERIFIED,
            result.message,
            SafeErrorCode.UNSUPPORTED
        )
        is ExternalLaunchResult.Failed -> OutcomeData(
            ActionStatus.FAILED,
            OutcomeVerification.UNVERIFIED,
            result.message,
            SafeErrorCode.OUTCOME_UNKNOWN
        )
    }

    private fun payload(proposal: ActionProposal, confirmation: String): String {
        val json = JSONObject()
            .put("actionType", proposal.action.type.name)
            .put("actionId", proposal.action.identity.id.toString())
            .put("requestId", proposal.requestId.toString())
            .put("confirmation", confirmation)
            .put("createdAt", proposal.createdAt.toEpochMilli())
            .put("expiresAt", proposal.expiresAt.toEpochMilli())
        when (val action = proposal.action) {
            is CreateTimerAction -> json.put("durationSeconds", action.durationSeconds).put("label", action.label)
            is CreateAlarmAction -> json.put("trigger", action.trigger.instant.toEpochMilli())
                .put("label", action.label)
            is CallAction -> json.put("targetLabel", action.targetLabel).put("phoneNumber", action.phoneNumber)
            is MessageAction -> json.put("recipient", action.recipient)
                .put("destination", action.destination.toString()).put("body", action.body)
            else -> Unit
        }
        return json.toString()
    }

    private suspend fun audit(run: SavedActionRun, decision: String, confirmation: UUID, reason: String) {
        val at = clock.millis()
        repository.foundation.actionAudit.save(SavedActionAudit(
            actionRunId = run.metadata.id,
            timestamp = at,
            policyVersion = "EXTERNAL_ACTIONS_V1",
            autonomyLevel = AutonomyLevel.SUGGEST.ordinal,
            permissionSnapshotJson = "{\"capabilities\":[\"${run.actionType}\"]}",
            decision = decision,
            reason = reason,
            evidence = "{\"source\":\"EXPLICIT_USER_ACCEPTANCE\"}",
            confirmationId = confirmation.toString(),
            metadata = RecordMetadata(UUID.randomUUID().toString(), at, at, 0)
        ))
    }

    @Suppress("LongParameterList")
    private fun receipt(
        run: SavedActionRun,
        action: Action,
        receiptId: UUID = run.receipt?.let { UUID.fromString(JSONObject(it).getString("id")) }
            ?: UUID.fromString(run.metadata.id),
        reason: String = run.receipt?.let { JSONObject(it).optString("reason", "Action executed.") }
            ?: "Action executed.",
        status: ActionStatus = ActionStatus.valueOf(run.status),
        verification: OutcomeVerification = OutcomeVerification.valueOf(run.verification),
        safeErrorCode: SafeErrorCode? = run.safeErrorCode?.let { SafeErrorCode.valueOf(it) },
    ): ActionReceipt {
        val identity = ActionIdentity(
            UUID.fromString(run.metadata.id),
            UUID.fromString(run.requestId),
            UUID.fromString(run.idempotencyKey)
        )
        return ActionReceipt(
            id = receiptId,
            identity = identity,
            actionType = action.type,
            outcome = ReceiptOutcome(
                status = status,
                startedAt = Instant.ofEpochMilli(run.startedAt),
                finishedAt = run.finishedAt?.let(Instant::ofEpochMilli) ?: Instant.ofEpochMilli(run.startedAt),
                verification = verification,
                safeErrorCode = safeErrorCode,
            ),
            reason = reason
        )
    }
}

