package dev.metis.agent.domain.agent

/** Extends the existing local read port; its only added operation accepts a revalidated V18 plan. */
interface GraphReadPortV18 : AgentReadPort {
    suspend fun graph(plan: GraphReadPlanV18, selectedId: String? = null): GraphReadReplyV18
}

class GraphReadChoiceV18(val id: String, val label: String) {
    override fun toString() = "GraphReadChoiceV18(redacted)"
}

sealed interface GraphReadReplyV18 {
    class Answer(val message: String) : GraphReadReplyV18 {
        override fun toString() = "GraphReadReplyV18.Answer(redacted)"
    }
    class Choose(val question: String, choices: List<GraphReadChoiceV18>) : GraphReadReplyV18 {
        val choices = frozenList(choices)
        override fun toString() = "GraphReadReplyV18.Choose(redacted)"
    }
}
