package dev.metis.agent.domain.storage

data class SavedReminder(
    val title: String,
    val triggerAt: Long,
    val localDateTime: String,
    val zoneId: String,
    val precision: String,
    val schedulingState: String,
    val idempotencyKey: String,
    val taskId: String? = null,
    val personId: String? = null,
    val platformToken: String? = null,
    val deliveredAt: Long? = null,
    override val metadata: RecordMetadata = RecordMetadata(),
) : FoundationRecord {
    init {
        validateFoundationText(title)
        java.time.LocalDateTime.parse(localDateTime)
        java.time.ZoneId.of(zoneId)
        require(precision in setOf("EXACT", "APPROXIMATE"))
        require(schedulingState in setOf("PENDING", "SCHEDULED", "FIRED", "CANCELLED", "FAILED",
            "UNSUPPORTED", "DENIED"))
        validateFoundationId(idempotencyKey)
        taskId?.let { validateFoundationId(it) }
        personId?.let { validateFoundationId(it) }
        platformToken?.let { validateFoundationText(it) }
        require(java.time.Instant.ofEpochMilli(triggerAt).atZone(java.time.ZoneId.of(zoneId)).toLocalDateTime() ==
            java.time.LocalDateTime.parse(localDateTime))
        require(schedulingState != "SCHEDULED" || platformToken != null)
        require(deliveredAt == null || (schedulingState == "FIRED" && deliveredAt >= triggerAt))
    }
}
