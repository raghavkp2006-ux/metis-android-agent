package dev.metis.agent

import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.DisciplineExportBounds
import dev.metis.agent.domain.storage.DisciplineImportCounts
import dev.metis.agent.domain.storage.DisciplineRepository
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * In-memory stand-in that mirrors the Phase 1 repository's revision, expected-day and archive rules.
 * It never fabricates key material; [failReads]/[failWrites] simulate the real key-readability failure.
 */
internal class FakeDisciplineRepository(
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : DisciplineRepository {
    private val habitRows = LinkedHashMap<String, HabitDefinition>()
    private val checkinRows = LinkedHashMap<String, DailyCheckin>()
    private val tickRows = LinkedHashMap<Pair<String, String>, HabitTick>()
    var failReads = false
    var failWrites = false
    var failImportAfterWriteAt: Int? = null
    var rangeReadCount = 0

    fun seedForExport(habits: List<HabitDefinition>, checkins: List<CheckinDay>) {
        habits.forEach { habitRows[it.metadata.id] = it }
        checkins.forEach { day -> checkinRows[day.checkin.metadata.id] = day.checkin
            day.ticks.forEach { tickRows[day.checkin.metadata.id to it.habitId] = it }
        }
    }

    fun allStored(): Triple<List<HabitDefinition>, List<DailyCheckin>, List<HabitTick>> =
        Triple(habitRows.values.toList(), checkinRows.values.toList(), tickRows.values.toList())

    private fun today() = clock.instant().atZone(zone()).toLocalDate()

    override suspend fun exportBounds(): DisciplineExportBounds? {
        guardRead()
        val starts = habitRows.values.map { it.startLocalDate } + checkinRows.values.map { it.localDate }
        val ends = habitRows.values.map { it.archivedAt ?: it.startLocalDate } + checkinRows.values.map { it.localDate }
        return if (starts.isEmpty()) null else DisciplineExportBounds(starts.min(), ends.max())
    }

    override suspend fun importRecords(habits: List<HabitDefinition>,
        checkins: List<CheckinDay>): DisciplineImportCounts {
        guardWrite()
        val originalHabits = LinkedHashMap(habitRows)
        val originalCheckins = LinkedHashMap(checkinRows)
        val originalTicks = LinkedHashMap(tickRows)
        return try {
            var hAdded = 0; var hSkipped = 0; var cAdded = 0; var cSkipped = 0; var tAdded = 0; var tSkipped = 0
            var writes = 0
            habits.forEach { habit -> if (habitRows.containsKey(habit.metadata.id)) hSkipped++ else {
                habitRows[habit.metadata.id] = habit; hAdded++; writes++; maybeFailImport(writes)
            } }
            checkins.forEach { day ->
                val checkin = day.checkin
                if (checkinRows.containsKey(checkin.metadata.id) ||
        checkinRows.values.any { it.localDate == checkin.localDate }) {
                    cSkipped++
                } else { checkinRows[checkin.metadata.id] = checkin; cAdded++; writes++; maybeFailImport(writes) }
            }
            checkins.flatMap { it.ticks }.forEach { tick ->
                if (!checkinRows.containsKey(tick.checkinId) || tickRows.containsKey(tick.checkinId to tick.habitId) ||
                    tickRows.values.any { it.id == tick.id }) tSkipped++
                else { tickRows[tick.checkinId to tick.habitId] = tick; tAdded++; writes++; maybeFailImport(writes) }
            }
            DisciplineImportCounts(hAdded, cAdded, tAdded, hSkipped, cSkipped, tSkipped)
        } catch (failure: Throwable) {
            habitRows.clear(); habitRows.putAll(originalHabits)
            checkinRows.clear(); checkinRows.putAll(originalCheckins)
            tickRows.clear(); tickRows.putAll(originalTicks)
            throw failure
        }
    }

    private fun maybeFailImport(writes: Int) {
        if (failImportAfterWriteAt == writes) error("Injected import failure")
    }

    /** Test-only peek at persisted state. */
    fun storedCheckin(date: LocalDate): DailyCheckin? = checkinRows.values.firstOrNull { it.localDate == date }

    private fun guardRead() { if (failReads) error("Unreadable key") }
    private fun guardWrite() { if (failWrites) error("Unreadable key") }

    override suspend fun habits(range: DisciplineDateRange): List<HabitDefinition> {
        guardRead()
        rangeReadCount++
        return habitRows.values.filter {
            !it.startLocalDate.isAfter(range.through) && (it.archivedAt == null || it.archivedAt.isAfter(range.from))
        }
    }

    override suspend fun checkins(range: DisciplineDateRange): List<CheckinDay> {
        guardRead()
        rangeReadCount++
        return checkinRows.values.filter {
            !it.localDate.isBefore(range.from) && !it.localDate.isAfter(range.through)
        }.sortedBy { it.localDate }.map { checkin ->
            CheckinDay(checkin, tickRows.values.filter { it.checkinId == checkin.metadata.id })
        }
    }

    override suspend fun saveHabit(habit: HabitDefinition): HabitDefinition {
        guardWrite()
        val old = habitRows[habit.metadata.id]
        if (old == null) {
            if (habit.metadata.revision != 0L) throw RevisionConflictException()
            habitRows[habit.metadata.id] = habit
            return habit
        }
        if (old.metadata.revision != habit.metadata.revision) throw RevisionConflictException()
        require(old.active && old.startLocalDate == habit.startLocalDate && old.cadence == habit.cadence)
        val updated = old.copy(name = habit.name, description = habit.description,
            metadata = old.metadata.bumped(clock.millis()))
        habitRows[habit.metadata.id] = updated
        return updated
    }

    override suspend fun archiveHabit(id: String, revision: Long): HabitDefinition {
        guardWrite()
        val habit = requireHabit(id, revision)
        require(habit.active) { "Habit is already archived." }
        val afterLastTick = tickRows.values.filter { it.habitId == id }
            .mapNotNull { tickRowsToDate(it) }.maxOrNull()?.plusDays(1)
        val date = maxOf(today(), afterLastTick ?: today())
        require(date.isAfter(habit.startLocalDate)) { "Archive date must follow the start." }
        val archived = habit.copy(archivedAt = date, metadata = habit.metadata.bumped(clock.millis()))
        habitRows[id] = archived
        return archived
    }

    override suspend fun restoreHabit(id: String, revision: Long): HabitDefinition {
        guardWrite()
        val old = requireHabit(id, revision)
        require(!old.active) { "Only archived habits can be restored." }
        val restored = old.copy(startLocalDate = today(), archivedAt = null, metadata = RecordMetadata())
        habitRows[restored.metadata.id] = restored
        return restored
    }

    override suspend fun deleteHabit(id: String, revision: Long) {
        guardWrite()
        requireHabit(id, revision)
        habitRows.remove(id)
        val affected = tickRows.filterValues { it.habitId == id }.keys.map { it.first }.toSet()
        tickRows.keys.removeIf { it.second == id }
        affected.forEach { checkinId -> checkinRows[checkinId]?.let {
            checkinRows[checkinId] = it.copy(metadata = it.metadata.bumped(clock.millis()))
        } }
    }

    override suspend fun saveCheckin(checkin: DailyCheckin, completions: Map<String, Boolean>): DailyCheckin {
        guardWrite()
        val old = checkinRows[checkin.metadata.id]
        val saved = if (old == null) {
            if (checkin.metadata.revision != 0L) throw RevisionConflictException()
            checkin
        } else {
            if (old.metadata.revision != checkin.metadata.revision ||
                old.localDate != checkin.localDate || old.zoneId != checkin.zoneId
            ) throw RevisionConflictException()
            checkin.copy(metadata = old.metadata.bumped(clock.millis()))
        }
        checkinRows[saved.metadata.id] = saved
        completions.forEach { (habitId, completed) -> applyTick(saved, habitId, completed) }
        return saved
    }

    override suspend fun setTick(
        checkinId: String, revision: Long, habitId: String, completed: Boolean,
    ): DailyCheckin {
        guardWrite()
        val old = checkinRows[checkinId] ?: throw RevisionConflictException()
        if (old.metadata.revision != revision) throw RevisionConflictException()
        val saved = old.copy(metadata = old.metadata.bumped(clock.millis()))
        checkinRows[checkinId] = saved
        applyTick(saved, habitId, completed)
        return saved
    }

    override suspend fun deleteCheckin(id: String, revision: Long) {
        guardWrite()
        val old = checkinRows[id] ?: throw RevisionConflictException()
        if (old.metadata.revision != revision) throw RevisionConflictException()
        checkinRows.remove(id)
        tickRows.keys.removeIf { it.first == id }
    }

    private fun applyTick(checkin: DailyCheckin, habitId: String, completed: Boolean) {
        val habit = habitRows[habitId] ?: error("Habit definition unavailable.")
        require(habit.isExpectedOn(checkin.localDate)) { "Habit is not expected on this local day." }
        tickRows[checkin.metadata.id to habitId] =
            HabitTick(checkin.metadata.id, habitId, completed, UUID.randomUUID().toString())
    }

    private fun tickRowsToDate(tick: HabitTick): LocalDate? = checkinRows[tick.checkinId]?.localDate

    private fun requireHabit(id: String, revision: Long): HabitDefinition {
        val habit = habitRows[id] ?: throw RevisionConflictException()
        if (habit.metadata.revision != revision) throw RevisionConflictException()
        return habit
    }

    private fun RecordMetadata.bumped(now: Long) =
        copy(updatedAt = maxOf(updatedAt, now), revision = revision + 1)
}
