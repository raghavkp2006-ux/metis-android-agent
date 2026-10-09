package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.agent.AcceptPlanAction
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResult
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.FollowUpQuestion
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.ValidationResult
import dev.metis.agent.domain.planner.PlanDraft
import dev.metis.agent.domain.planner.PlanPreparation
import dev.metis.agent.domain.planner.PlannerEngine
import dev.metis.agent.domain.planner.PlannerPort
import dev.metis.agent.domain.planner.PlannerWindow
import dev.metis.agent.domain.storage.SavedActionAudit
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** No durable drafts. Recheck the entire input snapshot and current authority before an atomic local commit. */
class LocalPlannerStore internal constructor(private val repository: LocalPersonalRepository,
    private val codec: RecordCodec, private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : PlannerPort {
    private val database = repository.database

    override suspend fun prepare(request: AgentRequest): PlanPreparation = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), codec)
            if (database.actionRun().find(PlanEncoding.actionId(request.id).toString()) != null) {
                return@withTransaction answer(request, "This plan request was already accepted. Review saved blocks " +
                    "in Plan; request a fresh plan for current inputs.")
            }
            val snapshot = snapshot()
            if (snapshot.routinesEnabled) return@withTransaction answer(request,
                "Enabled routines cannot be expanded by this planner baseline. No partial plan was generated.")
            val window = PlannerWindow.resolve(request.text, clock.instant(), zone(), snapshot.dayStart)
                ?: return@withTransaction PlanPreparation(AgentResult(request.id, AgentResultStatus.FOLLOW_UP,
                    WINDOW_HELP, followUp = FollowUpQuestion(UUID.randomUUID(), request.id, WINDOW_HELP,
                        setOf("window"))))
            val output = PlannerEngine.generate(snapshot.inputs, snapshot.schedules, window.span,
                snapshot.bookedTasksUntil.filterValues { it > window.span.start }.keys)
            val blocks = PlanEncoding.blocks(request.id, output, window, clock.millis())
            if (blocks.isEmpty()) return@withTransaction answer(request,
                "No eligible whole task fits before its deadline in this window. Known estimates and completed " +
                    "prerequisites are required. Saved commitments are preserved. No plan was saved.")
            val proposal = PlanEncoding.proposal(request.id, blocks, output, window, clock.instant())
            checkPolicy(proposal, context((proposal.action as AcceptPlanAction).acceptedBlocks))
            PlanPreparation(AgentResult(request.id, AgentResultStatus.PROPOSAL,
                "Review the proposed plan and assumed availability. Nothing is saved until you accept.",
                proposals = listOf(proposal)), PlanDraft(proposal, window, blocks.toList(), snapshot.fingerprint))
        }
    }

    override suspend fun accept(draft: PlanDraft, confirmationId: UUID): ActionReceipt = withContext(Dispatchers.IO) {
        database.withTransaction { commit(draft, confirmationId) }
    }

    private suspend fun commit(draft: PlanDraft, confirmation: UUID): ActionReceipt {
        requireReadableKey(database.records(), codec)
        val proposal = draft.proposal
        val action = proposal.action as? AcceptPlanAction ?: stale()
        val existing = database.actionRun().findByKey(action.identity.idempotencyKey.toString())
        if (existing != null) {
            val run = ActionRunCodec(codec).decode(existing)
            if (!replayMatches(run, proposal)) stale()
            return PlanEncoding.receipt(run)
        }
        checkPolicy(proposal, context(action.acceptedBlocks))
        val snapshot = snapshot()
        if (snapshot.fingerprint != draft.fingerprint || snapshot.routinesEnabled ||
            clock.millis() >= draft.window.span.start) stale()
        val output = PlannerEngine.generate(snapshot.inputs, snapshot.schedules, draft.window.span,
            snapshot.bookedTasksUntil.filterValues { it > draft.window.span.start }.keys)
        val expected = PlanEncoding.blocks(proposal.requestId, output, draft.window,
            draft.blocks.first().metadata.createdAt)
        if (expected != draft.blocks || !identityMatches(action) || !PlanEncoding.matches(draft, output)) stale()
        draft.blocks.forEach { repository.saveSchedule(it) }
        draft.blocks.forEach { check(codec.decode(requireNotNull(database.records().schedule(it.metadata.id))) == it) }
        val now = clock.millis()
        val run = PlanEncoding.run(draft, confirmation, now)
        repository.foundation.actionRun.save(run)
        repository.foundation.actionAudit.save(SavedActionAudit(run.metadata.id, now, PlanEncoding.OPERATION, 1,
            "{\"capabilities\":[\"LOCAL_READ\",\"LOCAL_WRITE\",\"PLAN_ACCEPTANCE\"]}", "ALLOW",
            "Displayed blocks explicitly accepted; inputs and local persistence rechecked. No reminder authorized.",
            "{\"source\":\"EXPLICIT_USER_ACCEPTANCE\"}", confirmation.toString()))
        return PlanEncoding.receipt(run)
    }

    private fun replayMatches(run: dev.metis.agent.domain.storage.SavedActionRun,
        proposal: dev.metis.agent.domain.agent.ActionProposal) =
        run.metadata.id == proposal.action.identity.id.toString() && run.proposalId == proposal.id.toString() &&
            run.requestId == proposal.requestId.toString() && run.safeErrorCode == null

    private fun identityMatches(action: AcceptPlanAction) =
        action.planId == PlanEncoding.planId(action.identity.requestId) &&
            action.identity.id == PlanEncoding.actionId(action.identity.requestId) &&
            action.expectedRevision == 0L && action.reminderProposalIds.isEmpty()

    private suspend fun snapshot(): PlannerSnapshot {
        if (database.records().decisionTaskCount() > MAX_ROWS || database.records().plannerScheduleCount() > MAX_ROWS) {
            throw ActionRejectedException(SafeErrorCode.UNSUPPORTED,
                "Too many saved records for this planner baseline.")
        }
        return plannerSnapshot(repository)
    }

    private suspend fun context(targets: List<dev.metis.agent.domain.agent.RevisionTarget>): ResolvedContext {
        val levels = database.userProfile().autonomyLevels()
        val authority = if (levels.size > 1 || levels.singleOrNull() == 0) AutonomyLevel.ANSWER_ONLY
            else AutonomyLevel.SUGGEST
        val now = clock.instant()
        return ResolvedContext(now, zone(), authority, CapabilitySnapshot(now,
            setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE, Capability.PLAN_ACCEPTANCE)),
            referencedEntities = targets)
    }

    private fun checkPolicy(proposal: dev.metis.agent.domain.agent.ActionProposal, context: ResolvedContext) {
        when (val review = ProposalPolicy().reviewProposal(proposal, context)) {
            is ValidationResult.Invalid -> throw ActionRejectedException(review.code, review.userMessage)
            ValidationResult.Valid -> Unit
        }
    }
    private fun answer(request: AgentRequest, message: String) =
        PlanPreparation(AgentResult(request.id, AgentResultStatus.ANSWER, message))
    private fun stale(): Nothing = throw ActionRejectedException(SafeErrorCode.STALE,
        "Plan inputs changed, its window started, or acceptance is stale. Request a fresh plan.")
    private companion object {
        const val MAX_ROWS = 1_000
        const val WINDOW_HELP = "Specify a future window on one local date, for example: " +
            "plan my day tomorrow from 09:00 to 17:00. Use HH:mm; overnight and ambiguous DST times need clarification."
    }
}
