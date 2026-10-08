package dev.metis.agent.domain.agent

import java.time.Instant
import java.util.UUID

enum class ActionStatus { SUCCEEDED, FAILED, CANCELLED, HANDED_OFF, PENDING, UNKNOWN }
enum class OutcomeVerification { VERIFIED_LOCAL, VERIFIED_PLATFORM, HANDOFF_ONLY, UNVERIFIED }
enum class UndoCapability { NOT_SUPPORTED, LOCAL_REVISION_CHECKED }
enum class SafeErrorCode { UNSUPPORTED, DENIED, STALE, STORAGE_UNAVAILABLE, OUTCOME_UNKNOWN }

@Suppress("LongParameterList")
class ActionReceipt(
    val id: UUID,
    val identity: ActionIdentity,
    val actionType: ActionType,
    val outcome: ReceiptOutcome,
    val reason: String,
    evidence: List<Evidence> = emptyList(),
    affectedEntities: List<EntityReference> = emptyList(),
) {
    val requestId: UUID get() = identity.requestId
    val evidence = frozenList(evidence)
    val affectedEntities = frozenList(affectedEntities)
    init {
        requireText(reason)
        require(actionType !in HANDOFF_ACTION_TYPES || outcome.status != ActionStatus.SUCCEEDED)
        require(actionType !in HANDOFF_ACTION_TYPES || outcome.undo == UndoCapability.NOT_SUPPORTED)
    }
}
data class ReceiptOutcome(
    val status: ActionStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val verification: OutcomeVerification,
    val undo: UndoCapability = UndoCapability.NOT_SUPPORTED,
    val safeErrorCode: SafeErrorCode? = null,
) {
    init {
        require(finishedAt == null || finishedAt >= startedAt)
        require(status !in TERMINAL_STATUSES || finishedAt != null)
        require(status != ActionStatus.PENDING || finishedAt == null)
        require(when (status) {
            ActionStatus.SUCCEEDED -> verification == OutcomeVerification.VERIFIED_LOCAL ||
                verification == OutcomeVerification.VERIFIED_PLATFORM
            ActionStatus.HANDED_OFF -> verification == OutcomeVerification.HANDOFF_ONLY
            else -> verification == OutcomeVerification.UNVERIFIED
        })
        require(status == ActionStatus.SUCCEEDED || undo == UndoCapability.NOT_SUPPORTED)
    }
}
private val TERMINAL_STATUSES = setOf(
    ActionStatus.SUCCEEDED, ActionStatus.FAILED, ActionStatus.CANCELLED, ActionStatus.HANDED_OFF,
)
private val HANDOFF_ACTION_TYPES = setOf(ActionType.CALL, ActionType.MESSAGE, ActionType.NAVIGATION)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val code: SafeErrorCode, val userMessage: String) : ValidationResult {
        init { requireText(userMessage) }
    }
}
enum class UndoResult { UNDONE, NOT_SUPPORTED, CONFLICT, FAILED }

/** Interface only: Phase 8 must add current policy, durable idempotency and verified storage/platform adapters. */
interface ActionExecutor<T : Action> {
    suspend fun validate(action: T): ValidationResult
    suspend fun execute(action: T): ActionReceipt
    suspend fun undo(action: T): UndoResult
}
