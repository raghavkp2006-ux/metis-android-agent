package dev.metis.agent.domain.storage

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class DailyCheckin(
    val localDate: LocalDate,
    val zoneId: String,
    val mood: Int? = null,
    val note: String? = null,
    val metadata: RecordMetadata = RecordMetadata(),
) {
    init {
        DisciplineDates.requireCanonical(localDate)
        ZoneId.of(zoneId)
        require(mood == null || mood in 1..MAX_MOOD)
        require(note == null || note.length <= FOUNDATION_CONTENT_LIMIT)
    }
    companion object { const val MAX_MOOD = 5 }
}

data class HabitTick(
    val checkinId: String,
    val habitId: String,
    val completed: Boolean,
    val id: String = UUID.randomUUID().toString(),
) {
    init { listOf(id, checkinId, habitId).forEach(::validateFoundationId) }
}

data class CheckinDay(val checkin: DailyCheckin, val ticks: List<HabitTick>)

/** Direct low-risk writes. A check-in's revision protects its mood, note and tick set together. */
interface DisciplineRepository {
    suspend fun habits(range: DisciplineDateRange): List<HabitDefinition>
    suspend fun checkins(range: DisciplineDateRange): List<CheckinDay>
    suspend fun saveHabit(habit: HabitDefinition): HabitDefinition
    suspend fun archiveHabit(id: String, revision: Long): HabitDefinition
    suspend fun restoreHabit(id: String, revision: Long): HabitDefinition
    suspend fun deleteHabit(id: String, revision: Long)
    /** Completions patch the supplied habits; omitted ticks are preserved. */
    suspend fun saveCheckin(checkin: DailyCheckin, completions: Map<String, Boolean> = emptyMap()): DailyCheckin
    suspend fun setTick(checkinId: String, revision: Long, habitId: String, completed: Boolean): DailyCheckin
    suspend fun deleteCheckin(id: String, revision: Long)
}
