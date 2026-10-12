@file:Suppress("MagicNumber") // Saved record length and unique-zone-offset bounds are protocol constants.

package dev.metis.agent.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.ScheduleRepository
import dev.metis.agent.domain.storage.ScheduleStatus
import java.time.Instant
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val CALENDAR_REASON = "Local calendar entry"

internal fun SavedSchedule.isCalendarEditable() = status == ScheduleStatus.ACCEPTED &&
    (reason == CALENDAR_REASON || reason.startsWith("$CALENDAR_REASON: ")) &&
    taskId == null && routineId == null &&
    scoreComponentsJson == null

internal data class CalendarDraft(
    val original: SavedSchedule? = null,
    val title: String = original?.title.orEmpty(),
    val date: String = original?.startAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(original.zoneId))
        .toLocalDate().toString() }.orEmpty(),
    val start: String = original?.startAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(original.zoneId))
        .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }.orEmpty(),
    val endDate: String = original?.endAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(original.zoneId))
        .toLocalDate().toString() }.orEmpty(),
    val end: String = original?.endAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(original.zoneId))
        .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }.orEmpty(),
    val details: String = original?.reason?.removePrefix(CALENDAR_REASON)?.removePrefix(": ").orEmpty(),
)

internal data class CalendarEditorState(
    val draft: CalendarDraft? = null,
    val deleting: SavedSchedule? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
)

/** Calendar drafts stay in RAM. This editor never changes planner-owned schedule blocks. */
internal class CalendarEditorViewModel(private val repository: ScheduleRepository,
                                       private val zone: ZoneId = ZoneId.systemDefault()) : ViewModel() {
    private val state = MutableStateFlow(CalendarEditorState())
    val uiState = state.asStateFlow()

    fun edit(day: LocalDate, event: SavedSchedule? = null) {
        if (state.value.busy || (event != null && !event.isCalendarEditable())) return
        state.value = CalendarEditorState(draft = if (event == null) CalendarDraft(
            date = day.toString(), endDate = day.toString(), start = "09:00", end = "10:00",
        ) else CalendarDraft(event))
    }

    fun change(draft: CalendarDraft) {
        if (!state.value.busy && state.value.draft != null) state.value = state.value.copy(draft = draft,
            error = null)
    }

    fun cancel() { if (!state.value.busy) state.value = CalendarEditorState() }

    fun confirmDelete(event: SavedSchedule) {
        if (!state.value.busy && event.isCalendarEditable()) state.value = CalendarEditorState(deleting = event)
    }

    @Suppress("ReturnCount") // Validation failures leave the draft open for correction.
    fun save() {
        val draft = state.value.draft ?: return
        val event = try { buildEvent(draft, draft.original?.zoneId?.let(ZoneId::of) ?: zone) }
        catch (_: DateTimeException) {
            state.value = state.value.copy(error = "Use a valid date (YYYY-MM-DD) and 24-hour time (HH:mm).")
            return
        } catch (_: IllegalArgumentException) {
            state.value = state.value.copy(error = "Use a title, valid dates and 24-hour times. " +
                "The end must follow the start; ambiguous local times need another time.")
            return
        }
        execute { repository.saveSchedule(event) }
    }

    fun delete() {
        val event = state.value.deleting ?: return
        if (!event.isCalendarEditable()) return
        execute { repository.deleteSchedule(event.metadata.id, event.metadata.revision) }
    }

    private fun execute(operation: suspend () -> Unit) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                operation()
                state.value = CalendarEditorState(finished = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RevisionConflictException) {
                state.value = CalendarEditorState(error = "This event changed. Reload and try again.")
            } catch (_: Exception) {
                state.value = CalendarEditorState(error = "Could not save the calendar change.")
            }
        }
    }
}

internal fun buildEvent(draft: CalendarDraft, zone: ZoneId): SavedSchedule {
    val title = draft.title.trim()
    require(title.isNotEmpty() && title.length <= 4_000)
    val reason = if (draft.details.isBlank()) CALENDAR_REASON else "$CALENDAR_REASON: ${draft.details.trim()}"
    require(reason.length <= 4_000)
    val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
    val start = LocalDateTime.of(LocalDate.parse(draft.date, dateFormat), LocalTime.parse(draft.start, timeFormat))
    val end = LocalDateTime.of(LocalDate.parse(draft.endDate, dateFormat), LocalTime.parse(draft.end, timeFormat))
    fun instant(local: LocalDateTime): Long {
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.size == 1)
        return local.toInstant(offsets.single()).toEpochMilli()
    }
    return SavedSchedule(title, instant(start), instant(end), zone.id, reason,
        metadata = draft.original?.metadata ?: dev.metis.agent.domain.storage.RecordMetadata(),
        planId = draft.original?.planId ?: java.util.UUID.randomUUID().toString(),
        status = ScheduleStatus.ACCEPTED)
}
