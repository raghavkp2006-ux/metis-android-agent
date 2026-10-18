package dev.metis.agent

import dev.metis.agent.data.storage.KeystoreFieldCipher
import dev.metis.agent.data.storage.LocalDisciplineRepository
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedTask
import java.security.KeyStore
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DisciplineStorageTest {
    private val fixture = StorageTestFixture()
    private val at = Instant.parse("2026-03-10T12:00:00Z")
    private var zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 3, 10)
    private val repository = LocalDisciplineRepository(fixture.database, fixture.cipher,
        Clock.fixed(at, ZoneOffset.UTC), { zone })
    private val window = DisciplineDateRange(today.minusDays(7), today.plusDays(7))
    @After fun cleanup() = fixture.close()

    @Test fun encryptedFieldsRoundTripAndTicksAreDirectAtomicWrites(): Unit = runBlocking {
        val habit = habit()
        val checkin = repository.saveCheckin(day(today), mapOf(habit.metadata.id to true))
        val result = repository.checkins(window).single()
        assertEquals(checkin, result.checkin)
        assertTrue(result.ticks.single().completed)
        assertEquals(habit, repository.habits(window).single())
        val h = requireNotNull(fixture.database.habitDefinitions().find(habit.metadata.id))
        val c = requireNotNull(fixture.database.dailyCheckins().find(checkin.metadata.id))
        assertFalse(h.name.contentEquals(habit.name.toByteArray()))
        assertFalse(requireNotNull(c.mood).contentEquals("3".toByteArray()))
        assertEquals("3", fixture.cipher.decrypt(c.mood, "daily_checkins/${checkin.metadata.id}/mood"))
        assertEquals(habit.description, fixture.cipher.decrypt(requireNotNull(h.description),
            "habit_definitions/${habit.metadata.id}/description"))
        assertEquals(checkin.note, fixture.cipher.decrypt(requireNotNull(c.note),
            "daily_checkins/${checkin.metadata.id}/note"))
    }

    @Test fun duplicateDayCannotCreateAnotherCheckin(): Unit = runBlocking {
        val saved = repository.saveCheckin(day(today))
        assertThrows(Exception::class.java) { runBlocking { repository.saveCheckin(day(today)) } }
        assertEquals(saved, repository.checkins(window).single().checkin)
    }

    @Test fun sevenDayBackfillIsInclusiveAndFutureAndEighthDayAreRejected(): Unit = runBlocking {
        repository.saveCheckin(day(today.minusDays(7)))
        listOf(today.minusDays(8), today.plusDays(1)).forEach { date ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.saveCheckin(day(date)) } }
        }
        assertEquals(listOf(today.minusDays(7)), repository.checkins(window).map { it.checkin.localDate })
    }

    @Test fun rangeQueriesReturnOnlyRequestedDaysAndTheirTicks(): Unit = runBlocking {
        val habit = habit()
        (0L..2L).forEach { offset -> repository.saveCheckin(day(today.minusDays(offset)), mapOf(habit.metadata.id to true)) }
        val result = repository.checkins(DisciplineDateRange(today.minusDays(2), today.minusDays(1)))
        assertEquals(listOf(today.minusDays(2), today.minusDays(1)), result.map { it.checkin.localDate })
        assertTrue(result.all { value -> value.ticks.single().checkinId == value.checkin.metadata.id })
        val outside = repository.saveHabit(HabitDefinition("Synthetic future habit", today.plusDays(4)))
        assertFalse(repository.habits(DisciplineDateRange(today, today)).any { it.metadata.id == outside.metadata.id })
        assertTrue(repository.checkins(DisciplineDateRange(today.minusDays(6), today.minusDays(3))).isEmpty())
    }

    @Test fun staleHabitAndCheckinWritesConflictAndTickUpdatesKeepOnePair(): Unit = runBlocking {
        val habit = habit()
        val renamed = repository.saveHabit(habit.copy(name = "Synthetic renamed"))
        assertEquals(1L, renamed.metadata.revision)
        assertThrows(RevisionConflictException::class.java) { runBlocking { repository.saveHabit(habit) } }
        assertThrows(RevisionConflictException::class.java) { runBlocking { repository.archiveHabit(habit.metadata.id, 0) } }
        val checkin = repository.saveCheckin(day(today), mapOf(habit.metadata.id to true))
        val firstId = repository.checkins(window).single().ticks.single().id
        val toggled = repository.setTick(checkin.metadata.id, 0, habit.metadata.id, false)
        assertEquals(1L, toggled.metadata.revision)
        assertEquals(firstId, repository.checkins(window).single().ticks.single().id)
        assertFalse(repository.checkins(window).single().ticks.single().completed)
        assertThrows(RevisionConflictException::class.java) { runBlocking { repository.saveCheckin(checkin) } }
        assertThrows(RevisionConflictException::class.java) { runBlocking { repository.deleteCheckin(checkin.metadata.id, 0) } }
    }

    @Test fun competingTickWritesHaveExactlyOneRevisionWinner(): Unit = runBlocking {
        val habit = habit()
        val checkin = repository.saveCheckin(day(today))
        val results = coroutineScope {
            listOf(true, false).map { completed -> async {
                runCatching { repository.setTick(checkin.metadata.id, 0, habit.metadata.id, completed) }
            } }.map { it.await() }
        }
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is RevisionConflictException)
        assertEquals(1L, repository.checkins(window).single().checkin.metadata.revision)
    }

    @Test fun invalidTickRollsBackMoodRevisionAndEarlierTickWrites(): Unit = runBlocking {
        val habit = habit()
        val checkin = repository.saveCheckin(day(today))
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.saveCheckin(checkin.copy(mood = 5),
            linkedMapOf(habit.metadata.id to true, UUID.randomUUID().toString() to true)) } }
        val result = repository.checkins(window).single()
        assertEquals(checkin, result.checkin)
        assertTrue(result.ticks.isEmpty())
    }

    @Test fun deletingEitherParentCascadesOnlyItsTicksAndInvalidatesAffectedDayRevision(): Unit = runBlocking {
        val first = habit()
        val second = habit()
        val checkin = repository.saveCheckin(day(today), mapOf(first.metadata.id to true, second.metadata.id to false))
        repository.deleteHabit(first.metadata.id, 0)
        val remaining = repository.checkins(window).single()
        assertEquals(second.metadata.id, remaining.ticks.single().habitId)
        assertEquals(1L, remaining.checkin.metadata.revision)
        assertThrows(RevisionConflictException::class.java) { runBlocking { repository.saveCheckin(checkin) } }
        repository.deleteCheckin(checkin.metadata.id, 1)
        assertTrue(repository.checkins(window).isEmpty())
        assertTrue(fixture.database.habitTicks().range(window.from.toString(), window.through.toString()).isEmpty())
        assertEquals(second, repository.habits(window).single())
    }

    @Test fun archiveAfterTodaysTickKeepsItExpectedAndRestoreCreatesANewIdentity(): Unit = runBlocking {
        val habit = habit()
        repository.saveCheckin(day(today), mapOf(habit.metadata.id to true))
        val count = habit.expectedDays(window)
        val archived = repository.archiveHabit(habit.metadata.id, 0)
        assertEquals(today.plusDays(1), archived.archivedAt)
        assertTrue(archived.isExpectedOn(today))
        assertEquals(8, archived.expectedDays(window))
        assertTrue(count > archived.expectedDays(window))
        val restored = repository.restoreHabit(habit.metadata.id, archived.metadata.revision)
        assertNotEquals(habit.metadata.id, restored.metadata.id)
        assertEquals(today, restored.startLocalDate)
        assertTrue(restored.active)
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.saveHabit(archived.copy(archivedAt = null))
        } }
        assertEquals(habit.metadata.id, repository.checkins(window).single().ticks.single().habitId)
    }

    @Test fun archiveAtOrBeforeStartIsRejectedWithoutDeletingTheDefinition(): Unit = runBlocking {
        listOf(today, today.plusDays(1)).forEach { start ->
            val habit = repository.saveHabit(HabitDefinition("Synthetic not yet archiveable", start))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.archiveHabit(habit.metadata.id, 0) } }
            assertTrue(requireNotNull(fixture.database.habitDefinitions().find(habit.metadata.id)).active)
            repository.deleteHabit(habit.metadata.id, 0)
        }
    }

    @Test fun zoneChangesNeverRewriteRecordedDaysOrExpectedCountsAndArchiveFollowsLatestTick(): Unit = runBlocking {
        zone = ZoneId.of("Pacific/Auckland")
        val localToday = at.atZone(zone).toLocalDate()
        val habit = habit()
        repository.saveCheckin(day(localToday), mapOf(habit.metadata.id to true))
        val range = DisciplineDateRange(habit.startLocalDate, localToday)
        val count = repository.habits(range).single().expectedDays(range)
        zone = ZoneId.of("America/Los_Angeles")
        assertEquals(count, repository.habits(range).single().expectedDays(range))
        val saved = repository.checkins(range).single().checkin
        assertEquals(localToday, saved.localDate)
        assertEquals("Pacific/Auckland", saved.zoneId)
        val archived = repository.archiveHabit(habit.metadata.id, 0)
        assertEquals(localToday.plusDays(1), archived.archivedAt)
        assertEquals(count, archived.expectedDays(range))
    }

    @Test fun cadenceStartDateAndCapturedEntryZoneAreImmutable(): Unit = runBlocking {
        val habit = habit()
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.saveHabit(habit.copy(startLocalDate = today))
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.saveHabit(habit.copy(cadence = HabitCadence.weekdays(setOf(DayOfWeek.MONDAY))))
        } }
        val checkin = repository.saveCheckin(day(today))
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.saveCheckin(checkin.copy(zoneId = "Europe/London"))
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.saveCheckin(checkin.copy(localDate = today.minusDays(1)))
        } }
    }

    @Test fun wrongOrMissingKeysFailWithoutReturningReplacementDataOrCreatingAKey(): Unit = runBlocking {
        habit()
        repository.saveCheckin(day(today))
        val alias = "${fixture.alias}.wrong"
        val cipher = KeystoreFieldCipher(alias)
        val wrong = LocalDisciplineRepository(fixture.database, cipher, Clock.fixed(at, ZoneOffset.UTC), { zone })
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            assertThrows(Exception::class.java) { runBlocking { wrong.checkins(window) } }
            assertThrows(Exception::class.java) { runBlocking {
                LocalPersonalRepository(fixture.database, cipher).saveTask(SavedTask("Synthetic blocked write"))
            } }
            assertNull(keys.getKey(alias, null))
            cipher.encrypt("Synthetic unrelated key", "synthetic/probe")
            assertThrows(Exception::class.java) { runBlocking { wrong.habits(window) } }
            assertEquals(1, repository.habits(window).size)
            assertEquals(1, repository.checkins(window).size)
            assertEquals(0, fixture.database.records().decisionTaskCount())
        } finally { keys.deleteEntry(alias) }
    }

    private suspend fun habit() = repository.saveHabit(HabitDefinition("Synthetic private habit", today.minusDays(7),
        description = "Synthetic private description", metadata = RecordMetadata(createdAt = 10)))
    private fun day(date: LocalDate) = DailyCheckin(date, zone.id, 3, "Synthetic private note",
        RecordMetadata(createdAt = 10))
}
