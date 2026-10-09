package dev.metis.agent.domain.storage

data class EventTimelineEntry(
    val id: String,
    val type: String,
    val source: String,
    val timestamp: Long,
    val summary: String,
    val title: String?,
    val status: String?,
    val verification: String?,
    val requestId: String?,
    val actionId: String?,
    val audit: List<SavedActionAudit>,
)
