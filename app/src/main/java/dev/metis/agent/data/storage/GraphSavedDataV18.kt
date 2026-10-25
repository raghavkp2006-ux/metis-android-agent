package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedReminder
import dev.metis.agent.domain.storage.SavedTask
import kotlinx.coroutines.flow.first

/** Read projections of existing repositories only. No contacts, mutation or platform port. */
internal interface GraphSavedDataV18 {
    suspend fun projects(): List<SavedProject>
    suspend fun goals(): List<SavedGoal>
    suspend fun people(): List<SavedPerson>
    suspend fun tasks(): List<SavedTask>
    suspend fun promises(): List<SavedPromise>
    suspend fun reminders(): List<SavedReminder>
}

internal class LocalGraphSavedDataV18(private val repository: LocalPersonalRepository) : GraphSavedDataV18 {
    override suspend fun projects() = repository.planning.observeProjects().first()
    override suspend fun goals() = repository.planning.observeGoals().first()
    override suspend fun people() = repository.foundation.person.observe().first()
    override suspend fun tasks() = repository.observeTasks().first()
    override suspend fun promises() = repository.foundation.promise.observe().first()
    override suspend fun reminders() = repository.foundation.reminder.observe().first()
}
