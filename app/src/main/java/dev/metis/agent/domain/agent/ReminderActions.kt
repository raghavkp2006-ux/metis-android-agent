package dev.metis.agent.domain.agent

import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class ReminderWorkState { ABSENT, QUEUED, RUNNING, FINISHED, CANCELLED, FAILED }
data class ReminderRegistration(val token: String, val state: ReminderWorkState)

/** Implementations expose queue registration and notification posting separately. */
interface ReminderPlatform {
    fun notificationsAvailable(): Boolean
    suspend fun registration(reminderId: UUID): ReminderRegistration?
    suspend fun schedule(reminderId: UUID, triggerAt: Long): ReminderRegistration
    suspend fun cancel(reminderId: UUID)
    fun posted(reminderId: UUID): Boolean
    fun post(reminderId: UUID, title: String)
}

data class ReminderHistoryEntry(
    val actionId: UUID, val reminderId: UUID, val title: String, val trigger: String,
    val status: String, val pending: Boolean, val receipt: ActionReceipt? = null,
)

interface AcceptedReminderStore {
    suspend fun context(request: AgentRequest): ResolvedContext
    suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt
    suspend fun retry(actionId: UUID): ActionReceipt
    suspend fun cancel(actionId: UUID): UndoResult
    suspend fun undo(receipt: ActionReceipt): UndoResult
    suspend fun reconcile()
    fun history(): Flow<List<ReminderHistoryEntry>>
}
