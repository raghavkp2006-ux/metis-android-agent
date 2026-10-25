package dev.metis.agent.data.storage

import dev.metis.agent.domain.agent.GraphReadPlanV18
import dev.metis.agent.domain.agent.GraphReadKindV18
import java.time.Instant
import java.time.format.DateTimeFormatter

internal class GraphReadRowV18(val kind: String, val text: String, val due: Long?, val id: String) {
    override fun toString() = "GraphReadRowV18(redacted)"
}

internal object GraphReadAnswersV18 {
    private const val MAX_ROWS = 5
    private const val MAX_TEXT = 500
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm XXX")

    fun count(size: Int, noun: String) = "$size $noun" + if (size == 1) "" else "s"

    fun render(plan: GraphReadPlanV18, name: String, rows: List<GraphReadRowV18>, sources: String,
               nextDeadline: Boolean = false): String {
        val scope = when (plan.kind) {
            GraphReadKindV18.PROJECT_PENDING -> "Open tasks in"
            GraphReadKindV18.PERSON_PENDING -> "Pending with"
            GraphReadKindV18.OPEN_PROMISES -> "Open promises to"
        }
        val header = "$scope ${display(name)} — saved sources: $sources."
        if (rows.isEmpty()) return "$header Nothing is pending."
        val ordered = rows.sortedWith(compareBy<GraphReadRowV18> { it.due ?: Long.MAX_VALUE }
            .thenBy { it.kind }.thenBy { it.id })
        val shown = ordered.take(MAX_ROWS).joinToString("\n") {
            "• ${it.kind}: ${display(it.text)} — ${deadline(plan, it.due)}"
        }
        val more = if (rows.size > MAX_ROWS) "\nAnd ${rows.size - MAX_ROWS} more." else ""
        val next = if (nextDeadline) "\nNext deadline: ${deadline(plan, ordered.first().due)}." else ""
        return "$header\n$shown$more$next"
    }

    fun display(value: String): String {
        val plain = value.map {
            if (it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()) ' ' else it
        }.joinToString("")
        return plain.take(MAX_TEXT) + if (plain.length > MAX_TEXT) "… [saved text excerpt]" else ""
    }

    private fun deadline(plan: GraphReadPlanV18, at: Long?): String = if (at == null) "no saved deadline" else {
        val date = Instant.ofEpochMilli(at)
        "due ${date.atZone(plan.zone).format(dateFormat)}" +
            if (date < plan.request.timestamp) " (overdue)" else ""
    }
}
