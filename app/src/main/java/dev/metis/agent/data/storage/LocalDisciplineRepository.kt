package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.DisciplineDates
import dev.metis.agent.domain.storage.DisciplineRepository
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** No consent or proposal pipeline: explicit habits/check-ins are direct, revision-checked writes. */
@Suppress("TooManyFunctions") // One transactional API owns habits, check-ins and their dependent ticks.
class LocalDisciplineRepository(
    private val database: PersonalDatabase,
    cipher: FieldCipher,
    private val clock: Clock = Clock.systemUTC(),
    private val currentZone: () -> ZoneId = { ZoneId.systemDefault() },
) : DisciplineRepository {
    private val habits = database.habitDefinitions()
    private val days = database.dailyCheckins()
    private val ticks = database.habitTicks()
    private val codec = DisciplineCodec(cipher)
    private val recordCodec = RecordCodec(cipher)

    override suspend fun habits(range: DisciplineDateRange) = transaction {
        habits.range(range.from.toString(), range.through.toString()).map(codec::decode)
    }

    override suspend fun checkins(range: DisciplineDateRange) = transaction {
        val rows = days.range(range.from.toString(), range.through.toString())
        val byDay = ticks.range(range.from.toString(), range.through.toString()).groupBy { it.checkinId }
        rows.map { row -> CheckinDay(codec.decode(row), byDay[row.metadata.id].orEmpty().map {
            HabitTick(it.checkinId, it.habitId, it.completed, it.id)
        }) }
    }

    override suspend fun saveHabit(habit: HabitDefinition) = transaction {
        val old = habits.find(habit.metadata.id)
        val metadata = nextMetadata(habit.metadata, old?.metadata, clock.millis())
        require(habit.active) { "Create a new definition to restore an archived habit." }
        val row = codec.encode(habit, metadata)
        if (old == null) habits.insert(row) else {
            require(old.active && old.startLocalDate == row.startLocalDate && old.cadence == row.cadence) {
                "Habit start and cadence are immutable; archived definitions cannot be edited."
            }
            requireChanged(habits.rename(metadata.id, old.metadata.revision, row.name, row.description,
                metadata.updatedAt))
        }
        codec.decode(row)
    }

    override suspend fun archiveHabit(id: String, revision: Long) = transaction {
        val row = requireHabit(id, revision)
        val habit = codec.decode(row)
        require(habit.active) { "Habit is already archived." }
        val today = clock.instant().atZone(currentZone()).toLocalDate()
        val afterLastTick = ticks.latestDate(id)?.let { DisciplineDates.parse(it).plusDays(1) }
        val date = maxOf(today, afterLastTick ?: today)
        val archived = habit.copy(archivedAt = date)
        val metadata = nextMetadata(habit.metadata, row.metadata, clock.millis())
        requireChanged(habits.archive(id, revision, date.toString(), metadata.updatedAt))
        archived.copy(metadata = metadata.domainMetadata())
    }

    override suspend fun restoreHabit(id: String, revision: Long) = transaction {
        val old = codec.decode(requireHabit(id, revision))
        require(!old.active) { "Only archived habits can be restored as new definitions." }
        val at = clock.instant()
        val restored = old.copy(startLocalDate = at.atZone(currentZone()).toLocalDate(), archivedAt = null,
            metadata = RecordMetadata(createdAt = at.toEpochMilli()))
        val metadata = nextMetadata(restored.metadata, null, at.toEpochMilli())
        habits.insert(codec.encode(restored, metadata))
        restored
    }

    override suspend fun deleteHabit(id: String, revision: Long) = transaction {
        requireHabit(id, revision)
        days.touchForHabitDeletion(id, clock.millis())
        requireChanged(habits.delete(id, revision))
    }

    override suspend fun saveCheckin(checkin: DailyCheckin, completions: Map<String, Boolean>) = transaction {
        persist(checkin, completions)
    }

    override suspend fun setTick(checkinId: String, revision: Long, habitId: String, completed: Boolean) = transaction {
        val row = days.find(checkinId) ?: throw RevisionConflictException()
        if (row.metadata.revision != revision) throw RevisionConflictException()
        persist(codec.decode(row), mapOf(habitId to completed))
    }

    override suspend fun deleteCheckin(id: String, revision: Long) = transaction {
        requireChanged(days.delete(id, revision))
    }

    private suspend fun persist(checkin: DailyCheckin, completions: Map<String, Boolean>): DailyCheckin {
        val at = clock.instant()
        val zone = currentZone()
        DisciplineDates.requireWritable(checkin.localDate, at, zone)
        val old = days.find(checkin.metadata.id)
        val metadata = nextMetadata(checkin.metadata, old?.metadata, at.toEpochMilli())
        if (old == null) require(checkin.zoneId == zone.id) { "Capture the user's current zone at entry time." }
        else require(old.localDate == checkin.localDate.toString() && old.zoneId == checkin.zoneId) {
            "A recorded local date and entry zone cannot be rewritten."
        }
        val row = codec.encode(checkin, metadata)
        if (old == null) days.insert(row) else requireChanged(days.update(metadata.id, old.metadata.revision,
            row.mood, row.note, metadata.updatedAt))
        completions.forEach { (habitId, completed) ->
            val habit = codec.decode(habits.find(habitId) ?: error("Habit definition unavailable."))
            require(habit.isExpectedOn(checkin.localDate)) { "Habit is not expected on this local day." }
            val tick = ticks.find(metadata.id, habitId)
            if (tick == null) ticks.insert(HabitTickEntity(UUID.randomUUID().toString(), metadata.id,
                habitId, completed))
            else requireChanged(ticks.update(metadata.id, habitId, completed))
        }
        return codec.decode(row)
    }

    private suspend fun requireHabit(id: String, revision: Long): HabitDefinitionEntity {
        val row = habits.find(id) ?: throw RevisionConflictException()
        if (row.metadata.revision != revision) throw RevisionConflictException()
        return row
    }

    private suspend fun <T> transaction(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), recordCodec)
            block()
        }
    }
}

private fun requireChanged(count: Int) { if (count != 1) throw RevisionConflictException() }
private fun StoredMetadata.domainMetadata() = RecordMetadata(id, createdAt, updatedAt, revision)
