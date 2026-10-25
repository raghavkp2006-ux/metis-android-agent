package dev.metis.agent

import dev.metis.agent.data.storage.GraphSavedDataV18
import dev.metis.agent.data.storage.LocalGraphReadsV18
import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.GraphReadAgentV18
import dev.metis.agent.domain.agent.GraphReadPlanV18
import dev.metis.agent.domain.agent.GraphReadValidationV18
import dev.metis.agent.domain.agent.GraphReadValidatorV18
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.storage.SavedGoal
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedReminder
import dev.metis.agent.domain.storage.SavedTask
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal class GraphDataV18 : GraphSavedDataV18 {
    var projectRows = emptyList<SavedProject>()
    var goalRows = emptyList<SavedGoal>()
    var personRows = emptyList<SavedPerson>()
    var taskRows = emptyList<SavedTask>()
    var promiseRows = emptyList<SavedPromise>()
    var reminderRows = emptyList<SavedReminder>()
    var reads = 0
    var fail = false
    private fun count() { reads++; check(!fail) }
    override suspend fun projects() = projectRows.also { count() }
    override suspend fun goals() = goalRows.also { count() }
    override suspend fun people() = personRows.also { count() }
    override suspend fun tasks() = taskRows.also { count() }
    override suspend fun promises() = promiseRows.also { count() }
    override suspend fun reminders() = reminderRows.also { count() }
}

internal class GraphFixtureV18 {
    val clock = ConversationClock()
    val session: UUID = UUID.randomUUID()
    val data = GraphDataV18()
    val store = ConversationTaskStore(clock)
    val port = LocalGraphReadsV18(ConversationReads(), data)
    fun agent(reads: AgentReadPort = ConversationReads()) = GraphReadAgentV18(
        ConfirmedTaskAgent(reads, store, { clock.zone }, readCandidate = ReadCandidate.V16),
        port, clock, { clock.zone })
    fun request(text: String, at: Instant = clock.instant(), sessionId: UUID = session) =
        AgentRequest(UUID.randomUUID(), text, InputSource.TEXT, at, conversationId = sessionId)
    fun plan(text: String): GraphReadPlanV18 =
        (GraphReadValidatorV18.validate(request(text), ZoneId.of("UTC")) as GraphReadValidationV18.Valid).plan
}

internal fun graphReminderV18(person: SavedPerson, task: SavedTask? = null, state: String = "PENDING",
                              at: Long = Instant.parse("2026-10-26T08:00:00Z").toEpochMilli()) =
    SavedReminder("Saved reminder", at, Instant.ofEpochMilli(at).atZone(ZoneId.of("UTC")).toLocalDateTime().toString(),
        "UTC", "APPROXIMATE", state, UUID.randomUUID().toString(), taskId = task?.metadata?.id,
        personId = person.metadata.id, platformToken = if (state == "SCHEDULED") "synthetic-token" else null)
