package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.EventTimelineEntry
import kotlinx.coroutines.flow.combine
import org.json.JSONObject

/** Correlate immutable historical outcomes, never infer their result from current entity state. */
class LocalEventTimeline(private val stores: FoundationRepositories) {
    fun observe() = combine(stores.event.observe(), stores.actionAudit.observe()) { events, audits ->
        val byAction = audits.groupBy { it.actionRunId }
        events.map { event ->
            val payload = event.payloadMetadata?.let(::JSONObject)
            EventTimelineEntry(event.metadata.id, event.type, event.source, event.timestamp,
                if (payload == null) "Personal event details removed or unavailable."
                else payload.optString("summary").ifBlank { "Saved local event." },
                payload?.optString("title")?.takeIf { it.isNotBlank() },
                payload?.optString("status")?.takeIf { it.isNotBlank() },
                payload?.optString("verification")?.takeIf { it.isNotBlank() },
                event.requestId, event.actionId,
                byAction[event.actionId].orEmpty().sortedWith(compareBy({ it.timestamp }, { it.metadata.id })))
        }
    }
}
