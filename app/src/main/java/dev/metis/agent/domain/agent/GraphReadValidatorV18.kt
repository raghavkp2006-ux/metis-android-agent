package dev.metis.agent.domain.agent

import java.time.ZoneId

/** The complete original request, typed name span and fixed zone survive until port dispatch. */
class GraphReadPlanV18 internal constructor(
    val request: AgentRequest,
    val zone: ZoneId,
    val kind: GraphReadKindV18,
    val name: String,
    val start: Int,
    val end: Int,
) {
    override fun toString() = "GraphReadPlanV18(redacted)"
}

sealed interface GraphReadValidationV18 {
    class Valid(val plan: GraphReadPlanV18) : GraphReadValidationV18
    data object UnresolvedPerson : GraphReadValidationV18
    data object Rejected : GraphReadValidationV18
}

/** No confidence, substring extraction, model classification or resolved slot authorizes a read. */
object GraphReadValidatorV18 {
    @Suppress("ReturnCount") // Unresolved person slots are distinct from both rejection and authority.
    fun validate(request: AgentRequest, zone: ZoneId): GraphReadValidationV18 {
        val form = GraphReadGrammarV18.form(request) ?: return GraphReadValidationV18.Rejected
        if (form.pronoun) return GraphReadValidationV18.UnresolvedPerson
        return GraphReadValidationV18.Valid(GraphReadPlanV18(request, zone, form.kind,
            form.name, form.start, form.end))
    }

    fun matches(plan: GraphReadPlanV18): Boolean {
        val expected = (validate(plan.request, plan.zone) as? GraphReadValidationV18.Valid)?.plan ?: return false
        return expected.kind == plan.kind && expected.name == plan.name &&
            expected.start == plan.start && expected.end == plan.end
    }
}
