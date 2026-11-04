package dev.metis.agent.domain.agent

/** Proposal review only, not dispatch authority. No execution/acceptance implementation ships in Phase 6. */
class ProposalPolicy : PolicyEngine {
    override fun review(action: Action, context: ResolvedContext): ValidationResult = when {
        context.autonomy == AutonomyLevel.ANSWER_ONLY -> invalid("Answer-only mode prevents actions.")
        context.autonomy != AutonomyLevel.SUGGEST -> invalid("Higher autonomy is not supported yet.")
        context.unresolvedFields.isNotEmpty() -> invalid("Resolve the missing details first.")
        !context.capabilities.available.containsAll(ActionRequirements.capabilities(action)) -> {
            val missing = ActionRequirements.capabilities(action) - context.capabilities.available
            val message = if (Capability.NOTIFICATIONS in missing) {
                "Notification permission is needed to do this. Allow?"
            } else "This action is not available yet."
            ValidationResult.Invalid(SafeErrorCode.UNSUPPORTED, message)
        }
        !context.referencedEntities.containsAll(ActionRequirements.targets(action)) ->
            ValidationResult.Invalid(SafeErrorCode.STALE, "The referenced record must be resolved again.")
        else -> ValidationResult.Valid
    }

    /** The future executor must refresh this context immediately before dispatch and reserve idempotency. */
    fun reviewProposal(proposal: ActionProposal, current: ResolvedContext): ValidationResult = when {
        current.now < proposal.createdAt || current.now >= proposal.expiresAt ->
            ValidationResult.Invalid(SafeErrorCode.STALE, "The proposal has expired. Request a new proposal.")
        else -> review(proposal.action, current)
    }

    private fun invalid(message: String) = ValidationResult.Invalid(SafeErrorCode.DENIED, message)
}
