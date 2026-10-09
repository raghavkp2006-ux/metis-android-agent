package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.AcceptPlanAction
import dev.metis.agent.domain.agent.ActionIdentity
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.ActionStatus
import dev.metis.agent.domain.agent.ActionType
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.OutcomeVerification
import dev.metis.agent.domain.agent.ReceiptOutcome
import dev.metis.agent.domain.agent.RevisionTarget
import dev.metis.agent.domain.agent.RiskLevel
import dev.metis.agent.domain.planner.PlanDraft
import dev.metis.agent.domain.planner.PlannerEngine
import dev.metis.agent.domain.planner.PlannerOutput
import dev.metis.agent.domain.planner.PlanningWindow
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.ScheduleStatus
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal object PlanEncoding {
    const val OPERATION = "CONFIRMED_PLAN_V1"
    fun actionId(request: UUID) = stable("plan-action:$request")
    fun planId(request: UUID) = stable("plan:$request")
    private fun stable(value: String) = UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8))

    fun blocks(requestId: UUID, output: PlannerOutput, window: PlanningWindow, at: Long): List<SavedSchedule> =
        output.blocks.map { planned ->
            val task = planned.task
            val evidence = JSONObject().put("version", PlannerEngine.VERSION)
                .put("taskId", task.metadata.id)
                .put("taskRevision", task.metadata.revision)
                .put("deadline", task.dueAt ?: JSONObject.NULL).put("estimatedSeconds", task.estimatedSeconds)
                .put("priority", task.priority).put("availability", "ASSUMED_EXCEPT_SAVED_COMMITMENTS").toString()
            SavedSchedule(task.title, planned.span.start, planned.span.end, window.zone.id,
                "Earliest deadline first; whole estimated duration fits a saved-data gap. " +
                    "External calendar, quiet hours and energy are unknown. Acceptance does not complete this task.",
                RecordMetadata(stable("plan-block:$requestId:${task.metadata.id}").toString(), at),
                planId(requestId).toString(), taskId = null, status = ScheduleStatus.ACCEPTED,
                scoreComponentsJson = evidence)
        }

    fun proposal(request: UUID, blocks: List<SavedSchedule>, output: PlannerOutput, window: PlanningWindow,
        at: Instant): ActionProposal {
        val action = AcceptPlanAction(ActionIdentity(actionId(request), request, actionId(request)), planId(request), 0,
            blocks.map {
                RevisionTarget(EntityReference(MemoryEntityType.SCHEDULE, UUID.fromString(it.metadata.id)), 0)
            })
        return ActionProposal(UUID.randomUUID(), action, RiskLevel.R1, description(blocks, output, window),
            emptyList(), true, at, at.plusSeconds(LIFETIME_SECONDS))
    }

    private fun description(blocks: List<SavedSchedule>, output: PlannerOutput, window: PlanningWindow): String {
        fun local(at: Long) = Instant.ofEpochMilli(at).atZone(window.zone).toString()
        fun time(at: Long) = Instant.ofEpochMilli(at).atZone(window.zone).toLocalTime().toString()
        return "Proposed plan: ${local(window.span.start)} to ${local(window.span.end)}. " +
            (if (window.assumed) "Default window; accepting confirms this assumed availability. " else
                "Requested window; accepting confirms availability outside saved commitments. ") +
            "Change it by submitting: plan my day tomorrow from 09:00 to 17:00.\n" +
            "Free time within saved data: ${output.freeMillis / MINUTE_MILLIS} minutes. " +
            "Existing overlapping commitment pairs: ${output.existingConflicts}. " +
            "Tasks not included: ${output.excludedCount}. Up to ${PlannerEngine.MAX_BLOCKS} whole tasks.\n" +
            blocks.joinToString("\n") { "${time(it.startAt)} – ${time(it.endAt)}: ${it.title.take(PREVIEW_TITLE)}" } +
            "\nEarliest deadline first, then priority. Unknown durations, unfinished prerequisites, " +
            "recurring tasks, inactive goals/projects, already booked tasks and tasks that cannot finish " +
            "before their deadline are excluded. " +
            "No task is split or marked complete. External calendar, quiet hours, energy and routines " +
            "are not inferred. " +
            "Accept saves these local blocks only. Reminders require separate review in Plan. " +
            "Nothing is saved until acceptance; no plan undo is available."
    }

    fun matches(draft: PlanDraft, output: PlannerOutput): Boolean {
        val proposal = draft.proposal
        val action = proposal.action as AcceptPlanAction
        val targets = draft.blocks.map {
            RevisionTarget(EntityReference(MemoryEntityType.SCHEDULE, UUID.fromString(it.metadata.id)), 0)
        }
        return action.acceptedBlocks == targets && proposal.risk == RiskLevel.R1 &&
            proposal.reason == description(draft.blocks, output, draft.window)
    }

    fun run(draft: PlanDraft, confirmation: UUID, now: Long): SavedActionRun {
        val proposal = draft.proposal
        val payload = JSONObject().put("operation", OPERATION).put("fingerprint", draft.fingerprint)
            .put("blocks", JSONArray(draft.blocks.map { it.metadata.id }))
            .put("createdAt", proposal.createdAt.toEpochMilli()).put("expiresAt", proposal.expiresAt.toEpochMilli())
            .put("confirmationId", confirmation).toString()
        return SavedActionRun(proposal.requestId.toString(), proposal.id.toString(),
            proposal.action.identity.idempotencyKey.toString(), "ACCEPT_PLAN", payload, "MEDIUM", "SUCCEEDED", now,
            "VERIFIED_LOCAL", finishedAt = now, receipt = JSONObject().put("id", UUID.randomUUID()).toString(),
            metadata = RecordMetadata(proposal.action.identity.id.toString(), now))
    }

    fun receipt(run: SavedActionRun): ActionReceipt = ActionReceipt(
        UUID.fromString(JSONObject(requireNotNull(run.receipt)).getString("id")),
        ActionIdentity(UUID.fromString(run.metadata.id), UUID.fromString(run.requestId),
            UUID.fromString(run.idempotencyKey)), ActionType.ACCEPT_PLAN,
        ReceiptOutcome(ActionStatus.SUCCEEDED, Instant.ofEpochMilli(run.startedAt),
            Instant.ofEpochMilli(requireNotNull(run.finishedAt)), OutcomeVerification.VERIFIED_LOCAL),
        "Plan saved and verified on this device. Tasks are unchanged. No reminders were scheduled. " +
            "In Plan, review an independent reminder for each future block if wanted. Plan undo is unavailable.")

    private const val LIFETIME_SECONDS = 300L
    private const val MINUTE_MILLIS = 60_000L
    private const val PREVIEW_TITLE = 80
}
