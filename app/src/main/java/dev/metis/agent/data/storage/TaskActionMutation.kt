package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionRejectedException
import dev.metis.agent.domain.agent.CompletionSelection
import dev.metis.agent.domain.agent.SafeErrorCode
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.TaskStatus
import org.json.JSONObject

/** Invoked inside the caller's Room transaction for acceptance, completion and undo. */
internal class TaskActionMutation(
    private val repository: LocalPersonalRepository,
    private val database: PersonalDatabase,
    private val codec: RecordCodec,
) {
    private val edits = TaskEdits(database)

    suspend fun resolve(title: String): CompletionSelection {
        if (title.length > MAX_ACTION_TITLE || title.any { it.isISOControl() }) return CompletionSelection.Unavailable
        val matches = database.records().openTasks().map { codec.decode(it) }
            .filter { it.title.equals(title, ignoreCase = true) }
        val task = matches.singleOrNull()
        return if (task == null || task.recurrenceRule != null) CompletionSelection.Unavailable
        else CompletionSelection.Selected(task.title, TaskCompletionEncoding.target(task))
    }

    suspend fun completionTask(proposal: ActionProposal): SavedTask? {
        val action = proposal.action as TaskAction
        if (action.mutation is TaskMutation.Create) return null
        val target = dev.metis.agent.domain.agent.ActionRequirements.targets(action).single()
        val task = database.records().task(target.reference.id.toString())?.let { codec.decode(it) }
        val unchanged = task != null && TaskCompletionEncoding.target(task) == target
        val supported = task != null && task.status == TaskStatus.OPEN && task.recurrenceRule == null
        val permitted = task != null && (action.mutation !is TaskMutation.Complete || canComplete(task))
        if (!unchanged || !supported || !permitted) {
            throw ActionRejectedException(SafeErrorCode.STALE,
                "The task changed, is recurring, or has unfinished prerequisites. Request a fresh proposal.")
        }
        return requireNotNull(task)
    }

    suspend fun validPending(data: JSONObject, at: Long): Boolean = if (data.getString("operation") ==
        TaskCompletionEncoding.OPERATION) {
        val task = database.records().task(data.getString("taskId"))?.let { codec.decode(it) }
        task != null && task.metadata.revision == data.getLong("revision") &&
            task.title == data.getString("title") && canComplete(task)
    } else if (data.getString("operation") == TaskEditEncoding.OPERATION) {
        val task = database.records().task(data.getString("taskId"))?.let { codec.decode(it) }
        task != null && edits.eligible(task, data, at)
    } else true

    suspend fun execute(data: JSONObject, at: Long): SavedTask {
        val taskId = data.getString("taskId")
        if (data.getString("operation") == TaskCompletionEncoding.OPERATION) {
            val task = codec.decode(requireNotNull(database.records().task(taskId)))
            check(validPending(data, at))
            repository.saveTask(task.copy(status = TaskStatus.COMPLETED, completedAt = at))
        } else if (data.getString("operation") == TaskEditEncoding.OPERATION) {
            val task = codec.decode(requireNotNull(database.records().task(taskId)))
            check(validPending(data, at))
            repository.saveTask(edits.changed(task, data))
        } else {
            check(database.records().task(taskId) == null)
            repository.saveTask(SavedTask(data.getString("title"),
                metadata = RecordMetadata(id = taskId, createdAt = at)))
        }
        val saved = codec.decode(requireNotNull(database.records().task(taskId)))
        val edit = data.getString("operation") == TaskEditEncoding.OPERATION
        check(saved.title == if (edit && data.getString("kind") == "RENAME")
            data.getString("afterTitle") else data.getString("title"))
        val completion = data.getString("operation") == TaskCompletionEncoding.OPERATION
        check(saved.metadata.revision == if (completion || edit) data.getLong("revision") + 1 else 0L)
        check(if (completion) saved.status == TaskStatus.COMPLETED && saved.completedAt == at
            else saved.status == TaskStatus.OPEN)
        return saved
    }

    suspend fun undo(run: SavedActionRun, task: SavedTask): Boolean {
        val completion = JSONObject(run.payload).getString("operation") == TaskCompletionEncoding.OPERATION
        return if (completion) undoCompletion(task)
        else if (JSONObject(run.payload).getString("operation") == TaskEditEncoding.OPERATION) {
            val restored = edits.restored(task, TaskEditEncoding.data(run))
            repository.saveTask(restored)
            val saved = codec.decode(requireNotNull(database.records().task(task.metadata.id)))
            check(saved == restored.copy(metadata = saved.metadata) &&
                saved.metadata.revision == task.metadata.revision + 1)
            true
        } else undoCreation(run, task)
    }

    private suspend fun undoCompletion(task: SavedTask): Boolean {
        val taskId = task.metadata.id
        if (task.status != TaskStatus.COMPLETED || database.records().completedDependents(taskId) != 0) return false
        repository.saveTask(task.copy(status = TaskStatus.OPEN, completedAt = null))
        val saved = codec.decode(requireNotNull(database.records().task(taskId)))
        check(saved.status == TaskStatus.OPEN && saved.completedAt == null &&
            saved.metadata.revision == task.metadata.revision + 1)
        return true
    }

    private suspend fun undoCreation(run: SavedActionRun, task: SavedTask): Boolean {
        val taskId = task.metadata.id
        if (database.records().taskUndoLinks(taskId, run.metadata.id) != 0) return false
        repository.deleteTask(taskId, task.metadata.revision)
        check(database.records().task(taskId) == null)
        return true
    }

    private suspend fun canComplete(task: SavedTask) = task.status == TaskStatus.OPEN &&
        task.recurrenceRule == null && database.records().incompletePrerequisites(task.metadata.id) == 0

    private companion object { const val MAX_ACTION_TITLE = 500 }
}

internal object TaskMutationEncoding {
    fun data(run: SavedActionRun): JSONObject = when (JSONObject(run.payload).optString("operation")) {
        TaskCompletionEncoding.OPERATION -> TaskCompletionEncoding.data(run)
        TaskEditEncoding.OPERATION -> TaskEditEncoding.data(run)
        else -> TaskActionEncoding.data(run)
    }

    fun isCompletion(run: SavedActionRun) = JSONObject(run.payload).optString("operation") ==
        TaskCompletionEncoding.OPERATION

    fun matches(run: SavedActionRun, proposal: ActionProposal) =
        if ((proposal.action as TaskAction).mutation is TaskMutation.Complete) {
            TaskCompletionEncoding.matches(run, proposal)
        } else if ((proposal.action as TaskAction).mutation is TaskMutation.Create) {
            TaskActionEncoding.matches(run, proposal)
        } else TaskEditEncoding.matches(run, proposal)
}
