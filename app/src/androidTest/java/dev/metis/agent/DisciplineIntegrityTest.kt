package dev.metis.agent

import dev.metis.agent.data.storage.HabitTickEntity
import dev.metis.agent.data.storage.LocalDisciplineRepository
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DisciplineIntegrityTest {
    private val fixture = StorageTestFixture()
    private val today = LocalDate.of(2026, 3, 10)
    private val zone = ZoneId.of("UTC")
    private val repository = LocalDisciplineRepository(fixture.database, fixture.cipher,
        Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), zone), { zone })
    private val range = DisciplineDateRange(today.minusDays(7), today)
    @After fun cleanup() = fixture.close()

    @Test fun ticksMustMatchTheHabitWindowAndCadenceButHistoricalArchiveBackfillIsAllowed(): Unit = runBlocking {
        val monday = repository.saveHabit(HabitDefinition("Synthetic Monday", today.minusDays(7),
            HabitCadence.weekdays(setOf(DayOfWeek.MONDAY))))
        val future = repository.saveHabit(HabitDefinition("Synthetic future", today.plusDays(1)))
        listOf(monday, future).forEach { habit ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                repository.saveCheckin(DailyCheckin(today, zone.id), mapOf(habit.metadata.id to true))
            } }
        }
        assertTrue(repository.checkins(range).isEmpty())
        val archived = repository.archiveHabit(monday.metadata.id, 0)
        val yesterday = today.minusDays(1)
        repository.saveCheckin(DailyCheckin(yesterday, zone.id), mapOf(archived.metadata.id to true))
        assertEquals(yesterday, repository.checkins(range).single().checkin.localDate)
    }

    @Test fun sqliteEnforcesUniqueTickPairsForeignKeysFlagsAndStrictLocalDates(): Unit = runBlocking {
        val habit = repository.saveHabit(HabitDefinition("Synthetic", today.minusDays(7)))
        val day = repository.saveCheckin(DailyCheckin(today, zone.id), mapOf(habit.metadata.id to true))
        assertThrows(Exception::class.java) { runBlocking { fixture.database.habitTicks().insert(
            HabitTickEntity(UUID.randomUUID().toString(), day.metadata.id, habit.metadata.id, false)) } }
        assertThrows(Exception::class.java) { runBlocking { fixture.database.habitTicks().insert(
            HabitTickEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(), habit.metadata.id, true)) } }
        val sql = fixture.database.openHelper.writableDatabase
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE habit_ticks SET completed = 2") }
        listOf("2026-2-28", "2026-02-29", "0000-01-01").forEach { date ->
            assertThrows(Exception::class.java) { sql.execSQL("""
                INSERT INTO daily_checkins(id,created_at,updated_at,revision,local_date,zone_id,mood,note)
                VALUES(?,10,10,0,?,'UTC',NULL,NULL)
            """.trimIndent(), arrayOf(UUID.randomUUID().toString(), date)) }
        }
        assertEquals(1, repository.checkins(range).single().ticks.size)
    }

    @Test fun sqlCannotUnarchiveRewriteDatesOrExcludeAnExistingTick(): Unit = runBlocking {
        val habit = repository.saveHabit(HabitDefinition("Synthetic", today.minusDays(7)))
        repository.saveCheckin(DailyCheckin(today, zone.id), mapOf(habit.metadata.id to true))
        val sql = fixture.database.openHelper.writableDatabase
        assertThrows(Exception::class.java) { sql.execSQL(
            "UPDATE habit_definitions SET archived_at = ?, active = 0", arrayOf(today.toString())) }
        repository.archiveHabit(habit.metadata.id, 0)
        listOf("UPDATE habit_definitions SET archived_at = NULL, active = 1",
            "UPDATE habit_definitions SET start_local_date = '2026-03-01'",
            "UPDATE habit_definitions SET cadence = 1", "UPDATE daily_checkins SET zone_id = 'Europe/London'",
            "UPDATE daily_checkins SET local_date = '2026-03-09'").forEach { query ->
            assertThrows(Exception::class.java) { sql.execSQL(query) }
        }
    }

    @Test fun authenticatedFieldBindingRejectsCiphertextSwaps(): Unit = runBlocking {
        repository.saveCheckin(DailyCheckin(today, zone.id, mood = 3, note = "Synthetic note"))
        fixture.database.openHelper.writableDatabase.execSQL("UPDATE daily_checkins SET mood = note")
        assertThrows(Exception::class.java) { runBlocking { repository.checkins(range) } }
    }
}
