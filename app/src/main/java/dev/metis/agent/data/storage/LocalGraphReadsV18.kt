package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.GraphReadChoiceV18
import dev.metis.agent.domain.agent.GraphReadKindV18
import dev.metis.agent.domain.agent.GraphReadPlanV18
import dev.metis.agent.domain.agent.GraphReadPortV18
import dev.metis.agent.domain.agent.GraphReadReplyV18
import dev.metis.agent.domain.agent.GraphReadValidatorV18
import dev.metis.agent.domain.storage.TaskStatus

/** Full-request validation precedes even name enumeration. Joins use stored IDs, never text guesses. */
internal class LocalGraphReadsV18(
    reads: AgentReadPort,
    private val data: GraphSavedDataV18,
) : GraphReadPortV18, AgentReadPort by reads {
    override suspend fun graph(plan: GraphReadPlanV18, selectedId: String?): GraphReadReplyV18 {
        require(GraphReadValidatorV18.matches(plan))
        val project = plan.kind == GraphReadKindV18.PROJECT_PENDING
        val names = if (project) data.projects().map { GraphReadChoiceV18(it.metadata.id, it.title) }
            else data.people().map { GraphReadChoiceV18(it.metadata.id, it.displayName) }
        val exact = names.filter { it.label.equals(plan.name, true) }
        val candidates = exact.ifEmpty { names.filter { it.label.startsWith(plan.name, true) } }
            .sortedWith(compareBy<GraphReadChoiceV18> { it.label.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
        val selected = if (selectedId == null) exact.singleOrNull()
            else candidates.singleOrNull { it.id == selectedId }
        if (selected == null) return choices(project, exact.isEmpty(), candidates)
        return if (project) project(plan, selected) else person(plan, selected)
    }

    private fun choices(project: Boolean, unknown: Boolean, candidates: List<GraphReadChoiceV18>): GraphReadReplyV18 {
        val kind = if (project) "project" else "person"
        val message = if (unknown) "No saved $kind exactly matches that name. " else "Several saved ${kind}s match. "
        if (candidates.isEmpty()) return GraphReadReplyV18.Answer(message + "No saved names match that prefix.")
        val more = if (candidates.size > MAX_ROWS) " And ${candidates.size - MAX_ROWS} more; use a longer name." else ""
        // Duplicate names stay separate, with stable displayed choices rather than a guessed record.
        val shown = candidates.take(MAX_ROWS).mapIndexed { index, candidate ->
            GraphReadChoiceV18(candidate.id, "${display(candidate.label)} (saved match ${index + 1})")
        }
        return GraphReadReplyV18.Choose(message + "Which one do you mean?$more", shown)
    }

    private suspend fun project(plan: GraphReadPlanV18, selected: GraphReadChoiceV18): GraphReadReplyV18 {
        val goals = data.goals().filter { it.projectId == selected.id }.map { it.metadata.id }.toSet()
        val tasks = data.tasks().filter { it.status == TaskStatus.OPEN &&
            (it.projectId == selected.id || it.goalId in goals) }
        val rows = tasks.map { GraphReadRowV18("Task", it.title, it.dueAt, it.metadata.id) }
        return GraphReadReplyV18.Answer(GraphReadAnswersV18.render(plan, selected.label, rows,
            GraphReadAnswersV18.count(tasks.size, "task"), nextDeadline = true))
    }

    private suspend fun person(plan: GraphReadPlanV18, selected: GraphReadChoiceV18): GraphReadReplyV18 {
        val promises = data.promises().filter { it.personId == selected.id }
        val open = promises.filter { it.status == "OPEN" }
        val rows = open.map { GraphReadRowV18("Promise", it.content, it.dueAt, it.metadata.id) }
        if (plan.kind == GraphReadKindV18.OPEN_PROMISES) return GraphReadReplyV18.Answer(
            GraphReadAnswersV18.render(plan, selected.label, rows, GraphReadAnswersV18.count(open.size, "promise")))
        val reminders = data.reminders().filter { it.personId == selected.id }
        val linkedTasks = (promises.mapNotNull { it.taskId } + reminders.mapNotNull { it.taskId }).toSet()
        val tasks = data.tasks().filter { it.status == TaskStatus.OPEN && it.metadata.id in linkedTasks }
        val pending = reminders.filter { it.schedulingState in setOf("PENDING", "SCHEDULED") }
        val all = rows + tasks.map { GraphReadRowV18("Task", it.title, it.dueAt, it.metadata.id) } +
            pending.map { GraphReadRowV18("Reminder", it.title, it.triggerAt, it.metadata.id) }
        val counts = listOf(GraphReadAnswersV18.count(tasks.size, "task"),
            GraphReadAnswersV18.count(open.size, "promise"), GraphReadAnswersV18.count(pending.size, "reminder"))
        return GraphReadReplyV18.Answer(GraphReadAnswersV18.render(plan, selected.label, all,
            counts.joinToString(", ")))
    }

    private fun display(value: String) = GraphReadAnswersV18.display(value)

    private companion object { const val MAX_ROWS = 5 }
}
