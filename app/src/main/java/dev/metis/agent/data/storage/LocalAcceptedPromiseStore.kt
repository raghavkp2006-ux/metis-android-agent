package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.AcceptedPromiseStore
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.CreatePromiseAction
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.PromisePersonMatch
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.ValidationResult
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedPromise
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The only CREATE_PROMISE executor. Person, Promise, verified run and audit commit atomically. */
class LocalAcceptedPromiseStore internal constructor(
    private val repository: LocalPersonalRepository,
    private val database: PersonalDatabase,
    codec: RecordCodec,
    private val clock: Clock = Clock.systemUTC(),
) : AcceptedPromiseStore {
    private val runs = ActionRunCodec(codec)

    override suspend fun context(request: AgentRequest): ResolvedContext {
        val levels = database.userProfile().autonomyLevels()
        val autonomy = if (levels.size > 1 || levels.singleOrNull() == 0) AutonomyLevel.ANSWER_ONLY
            else AutonomyLevel.SUGGEST
        val now = clock.instant()
        return ResolvedContext(now, ZoneId.systemDefault(), autonomy,
            CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE)),
            screenContext = request.screenContext, conversationId = request.conversationId)
    }

    override suspend fun matchingPeople(name: String): List<PromisePersonMatch> =
        repository.foundation.person.observe().first().filter { it.displayName.equals(name, ignoreCase = true) }
            .map { PromisePersonMatch(UUID.fromString(it.metadata.id), it.displayName) }.sortedBy { it.id.toString() }

    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "InjectDispatcher") // Acceptance checks stay atomic.
    override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt =
        withContext(Dispatchers.IO) {
            val action = proposal.action as? CreatePromiseAction ?: reject()
            database.withTransaction {
                val old = database.actionRun().findByKey(action.identity.idempotencyKey.toString())
                if (old != null) {
                    val existing = runs.decode(old)
                    if (existing.proposalId != proposal.id.toString() ||
                        existing.requestId != proposal.requestId.toString() ||
                        existing.metadata.id != action.identity.id.toString() ||
                        existing.payload != payload(proposal, JSONObject(existing.payload).getString("confirmation")))
                        reject()
                    return@withTransaction receipt(existing)
                }
                val current = context(AgentRequest(UUID.randomUUID(), "accepted promise",
                    dev.metis.agent.domain.agent.InputSource.TEXT, clock.instant()))
                when (val review = ProposalPolicy().reviewProposal(proposal, current)) {
                    is ValidationResult.Invalid -> throw ActionRejectedException(review.code, review.userMessage)
                    ValidationResult.Valid -> Unit
                }
                val matches = matchingPeople(action.personName)
                val personId = when {
                    action.matchedPersonId != null -> {
                        if (matches.none { it.id == action.matchedPersonId }) reject()
                        action.matchedPersonId.toString()
                    }
                    matches.size == 1 -> matches.single().id.toString()
                    matches.size > 1 -> reject()
                    else -> UUID.randomUUID().toString()
                }
                val now = current.now.toEpochMilli()
                val promiseId = UUID.nameUUIDFromBytes("promise:${action.identity.id}".toByteArray()).toString()
                val pending = SavedActionRun(proposal.requestId.toString(), proposal.id.toString(),
                    action.identity.idempotencyKey.toString(), "CREATE_PROMISE", payload(proposal,
                        confirmationId.toString()), "MEDIUM", "PENDING", now, "UNVERIFIED",
                    metadata = RecordMetadata(id = action.identity.id.toString(), createdAt = now))
                repository.foundation.actionRun.save(pending)
                audit(pending, "CONFIRM", confirmationId, "The displayed promise was explicitly accepted.")
                if (matches.isEmpty()) repository.foundation.person.save(SavedPerson(action.personName,
                    metadata = RecordMetadata(id = personId, createdAt = now)))
                repository.foundation.promise.save(SavedPromise(personId, action.description, "OPEN",
                    dueAt = action.dueAt?.toEpochMilli(),
                    metadata = RecordMetadata(id = promiseId, createdAt = now)))
                val completed = pending.copy(status = "SUCCEEDED", verification = "VERIFIED_LOCAL", finishedAt = now,
                    entityType = "PROMISE", entityId = promiseId,
                    receipt = JSONObject().put("id", UUID.randomUUID()).put("promiseId", promiseId).toString())
                repository.foundation.actionRun.save(completed)
                audit(completed, "ALLOW", confirmationId,
                    "Promise, receipt and audit were verified and committed atomically.")
                receipt(completed)
            }
        }

    private fun payload(proposal: ActionProposal, confirmation: String): String {
        val action = proposal.action as CreatePromiseAction
        return JSONObject().put("operation", "CREATE_PROMISE_V1").put("person", action.personName)
            .put("description", action.description).put("dueDate", action.dueDate?.toString())
            .put("dueAt", action.dueAt?.toEpochMilli()).put("matchedPersonId", action.matchedPersonId?.toString())
            .put("createdAt", proposal.createdAt.toEpochMilli()).put("expiresAt", proposal.expiresAt.toEpochMilli())
            .put("confirmation", confirmation).toString()
    }

    private fun receipt(run: SavedActionRun): ActionReceipt {
        val success = run.status == "SUCCEEDED"
        return ActionReceipt(run.receipt?.let { UUID.fromString(JSONObject(it).getString("id")) }
            ?: UUID.fromString(run.metadata.id),
            ActionIdentity(UUID.fromString(run.metadata.id), UUID.fromString(run.requestId),
                UUID.fromString(run.idempotencyKey)), ActionType.CREATE_PROMISE,
            ReceiptOutcome(ActionStatus.valueOf(run.status), Instant.ofEpochMilli(run.startedAt),
                run.finishedAt?.let(Instant::ofEpochMilli),
                if (success) OutcomeVerification.VERIFIED_LOCAL else OutcomeVerification.UNVERIFIED),
            if (success) "Promise saved and verified locally." else "Promise save is pending.",
            affectedEntities = if (success) listOf(EntityReference(MemoryEntityType.PROMISE,
                UUID.fromString(requireNotNull(run.entityId)))) else emptyList())
    }

    private suspend fun audit(run: SavedActionRun, decision: String, confirmation: UUID, reason: String) {
        val at = clock.millis()
        repository.foundation.actionAudit.save(SavedActionAudit(run.metadata.id, at, "PROMISE_ACTIONS_V1",
            context(AgentRequest(UUID.randomUUID(), "accepted promise",
                dev.metis.agent.domain.agent.InputSource.TEXT, clock.instant())).autonomy.ordinal,
            "{\"capabilities\":[\"LOCAL_READ\",\"LOCAL_WRITE\"]}", decision, reason,
            "{\"source\":\"EXPLICIT_USER_ACCEPTANCE\"}", confirmation.toString()))
    }

    private fun reject(): Nothing = throw ActionRejectedException(SafeErrorCode.DENIED,
        "Only an unchanged, uniquely resolved promise proposal can be accepted.")
}
