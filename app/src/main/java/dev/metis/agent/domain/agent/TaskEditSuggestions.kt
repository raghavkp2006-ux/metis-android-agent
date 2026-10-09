package dev.metis.agent.domain.agent

import java.util.UUID

internal object TaskEditSuggestions {
    fun respond(request: ParsedRequest, selection: CompletionSelection): SpecialistResult {
        if (selection !is CompletionSelection.Selected) return clarification("task",
            "Use the exact title of one open, nonrecurring task. Duplicate titles need distinct names.")
        val mutation = when (request.prediction.intent) {
            AgentIntent.DELETE_TASK -> TaskMutation.Delete(selection.target)
            AgentIntent.POSTPONE_TASK -> {
                val time = request.entities.first { it.type == EntityKind.TIME }
                    .normalizedValue as? NormalizedValue.Time
                time?.let { TaskMutation.Postpone(selection.target, it.value) }
            }
            else -> update(request, selection.target)
        }
        return if (mutation == null) clarification("change",
            "Use a title of at most 500 characters, priority 0–3, or a future explicit date and unambiguous time.")
        else SpecialistResult.Suggestion(TaskAction(
            ActionIdentity(UUID.randomUUID(), request.request.id, UUID.randomUUID()), mutation),
            "Task: ${selection.title}. " + if (mutation is TaskMutation.Delete)
                "Permanently delete this unlinked task and remove its personal action history. There is no undo."
            else "Change only the displayed task fields. Reminders and schedule blocks stay as they are.")
    }

    private fun update(request: ParsedRequest, target: RevisionTarget): TaskMutation.Update? {
        val value = request.entities.first { it.type == EntityKind.TITLE }.rawValue
        val priority = Regex("^\\s*(?:please )?prioritize\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(request.request.text)
        val patch = if (priority) value.toIntOrNull()?.takeIf { it in 0..MAX_PRIORITY }
            ?.let { TaskPatch(priority = FieldChange.Set(it)) }
        else value.takeIf { it.length <= MAX_TITLE && it.none { char -> char.isISOControl() } }
            ?.let { TaskPatch(title = FieldChange.Set(it)) }
        return patch?.let { TaskMutation.Update(target, it) }
    }

    private fun clarification(field: String, message: String) = SpecialistResult.Clarification(message, setOf(field))
    private const val MAX_PRIORITY = 3
    private const val MAX_TITLE = 500
}
