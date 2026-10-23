package dev.metis.agent.domain.agent

import java.time.LocalDate
import java.time.ZoneId

internal data class PromiseDetails(val person: String?, val description: String?, val dueDate: LocalDate?)

/** Conservative grammar: the first name token, or tokens before an article, are the typed name. */
internal object PromiseResolution {
    private val due = Regex("\\s+by\\s+(today|tomorrow|\\d{4}-\\d{2}-\\d{2}|monday|tuesday|" +
        "wednesday|thursday|friday|saturday|sunday)$", RegexOption.IGNORE_CASE)
    private val commitment = Regex("^i (?:promised|owe)(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
    private val told = Regex("^i told(?:\\s+(.*?))?\\s+i['’]d(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
    private val boundary = Regex("\\s+(?=(?:the|a|an|to)\\s)", RegexOption.IGNORE_CASE)
    private val capitalizedName = Regex("^\\S+(?:\\s+[A-Z][^\\s]*)*")

    @Suppress("ReturnCount") // Invalid or absent slots are kept distinct for clarification.
    fun parse(request: AgentRequest, zone: ZoneId): PromiseDetails? {
        val text = request.text.trim().trimEnd('.', '!')
        val dateMatch = due.find(text)
        if (dateMatch == null && Regex("\\s+by\\s+\\S", RegexOption.IGNORE_CASE).containsMatchIn(text))
            return null
        val body = if (dateMatch == null) text else text.substring(0, dateMatch.range.first)
        val date = dateMatch?.groupValues?.get(1)?.let { ReminderResolution.resolveDate(request, it, zone) }
        if (dateMatch != null && date == null) return null
        val toldMatch = told.matchEntire(body)
        if (toldMatch != null) return PromiseDetails(toldMatch.groupValues[1].trim().ifBlank { null },
            toldMatch.groupValues[2].trim().ifBlank { null }, date)
        val match = commitment.matchEntire(body) ?: return null
        val rest = match.groupValues[1].trim()
        if (rest.isBlank()) return PromiseDetails(null, null, date)
        if (rest.matches(Regex("^(?:the|a|an|to)\\b.*", RegexOption.IGNORE_CASE)))
            return PromiseDetails(null, rest, date)
        val split = boundary.find(rest)?.range?.first ?: capitalizedName.find(rest)?.value?.length
        return if (split == null || split == rest.length) PromiseDetails(rest, null, date)
        else PromiseDetails(rest.substring(0, split).trim(), rest.substring(split).trim(), date)
    }

    fun extract(request: AgentRequest, zone: ZoneId): List<ExtractedEntity> {
        val details = parse(request, zone) ?: return emptyList()
        return listOfNotNull(details.person?.let { value -> span(request, EntityKind.PERSON, value) },
            details.description?.let { value -> span(request, EntityKind.TITLE, value) },
            details.dueDate?.let { date ->
                val match = due.find(request.text.trimEnd('.', '!')) ?: return@let null
                entity(EntityKind.TIME, requireNotNull(match.groups[1]),
                    NormalizedValue.Text(date.toString()))
            })
    }

    private fun span(request: AgentRequest, type: EntityKind, value: String): ExtractedEntity? {
        val offset = request.text.indexOf(value, ignoreCase = true)
        return if (offset < 0) null else ExtractedEntity(type, request.text.substring(offset, offset + value.length),
            offset, offset + value.length, 1.0)
    }
}
