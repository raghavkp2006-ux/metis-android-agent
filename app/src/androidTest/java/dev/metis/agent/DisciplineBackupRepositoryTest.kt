package dev.metis.agent

import android.database.sqlite.SQLiteException
import dev.metis.agent.data.storage.DisciplineBackupFormat
import dev.metis.agent.data.storage.DisciplineBackupManager
import dev.metis.agent.data.storage.KeystoreFieldCipher
import dev.metis.agent.data.storage.LocalDisciplineRepository
import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.DisciplineExportBounds
import dev.metis.agent.domain.storage.DisciplineImportCounts
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import dev.metis.agent.domain.storage.RecordMetadata
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real encrypted Room and independent Android Keystore aliases, including the import transaction. */
class DisciplineBackupRepositoryTest {
    @Test fun exportImportsEveryFieldOnAFreshKeyAcrossMoreThanFourHundredDays(): Unit = runBlocking {
        StorageTestFixture().use { source -> StorageTestFixture().use { target ->
            assertNotEquals(source.alias, target.alias)
            val sourceRepository = repository(source)
            val targetRepository = repository(target)
            val (habits, days) = history()
            assertEquals(DisciplineImportCounts(2, 2, 2, 0, 0, 0), sourceRepository.importRecords(habits, days))
            val password = "Synthetic backup password!".toCharArray()
            try {
                val bytes = DisciplineBackupManager(sourceRepository).createExport(password, "5.1", 123L)
                val targetManager = DisciplineBackupManager(targetRepository)
                val staged = targetManager.stageImport(ByteArrayInputStream(bytes), password)
                assertEquals(DisciplineImportCounts(2, 2, 2, 0, 0, 0),
                    targetManager.applyImport(staged))
                assertEquals(habits.toSet(),
                    (targetRepository.habits(firstWindow()) + targetRepository.habits(lastWindow())).toSet())
                assertEquals(days, targetRepository.checkins(firstWindow()) + targetRepository.checkins(lastWindow()))
                val sourceHabit = requireNotNull(source.database.habitDefinitions().find(habits.first().metadata.id))
                val targetHabit = requireNotNull(target.database.habitDefinitions().find(habits.first().metadata.id))
                assertFalse(sourceHabit.name.contentEquals(targetHabit.name))
                assertFalse(requireNotNull(sourceHabit.description).contentEquals(
                    requireNotNull(targetHabit.description)))
                val sourceDay = requireNotNull(source.database.dailyCheckins().find(days.first().checkin.metadata.id))
                val targetDay = requireNotNull(target.database.dailyCheckins().find(days.first().checkin.metadata.id))
                assertFalse(requireNotNull(sourceDay.mood).contentEquals(requireNotNull(targetDay.mood)))
                assertFalse(requireNotNull(sourceDay.note).contentEquals(requireNotNull(targetDay.note)))
            } finally { password.fill('\u0000') }
        } }
    }

    @Test fun failedMidApplyRollsBackEarlierHabitWritesAndPreservesAllRows(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val repository = repository(fixture)
            val baseline = history().let { (habits, days) ->
                repository.importRecords(habits.take(1), days.take(1))
                snapshot(repository)
            }
            val counts = tableCounts(fixture)
            val incomingHabit = habit("atomic", FIRST.plusDays(2))
            val incomingDay = day("atomic", FIRST.plusDays(2), "UTC", 4, "Must roll back", incomingHabit)
            val sql = fixture.database.openHelper.writableDatabase
            sql.execSQL("CREATE TRIGGER fail_backup_day BEFORE INSERT ON daily_checkins " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic mid-import failure'); END")
            try {
                assertThrows(SQLiteException::class.java) { runBlocking {
                    repository.importRecords(listOf(incomingHabit), listOf(incomingDay))
                } }
            } finally { sql.execSQL("DROP TRIGGER fail_backup_day") }
            assertEquals(counts, tableCounts(fixture))
            assertEquals(baseline, snapshot(repository))
        }
    }

    @Test fun existingIdsAndDaysAreSkippedWithoutOverwritingRealRows(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val repository = repository(fixture)
            val existingHabit = habit("existing", FIRST)
            val original = day("existing", FIRST, "UTC", 2, "Original", existingHabit)
            val existingSecond = day("second", FIRST.plusDays(1), "Europe/London", 3, "Kept", existingHabit)
            repository.importRecords(listOf(existingHabit), listOf(original, existingSecond))
            val before = snapshot(repository)
            val addedHabit = habit("added", FIRST)
            val sameId = day("existing", FIRST, "UTC", 5, "Replacement", existingHabit)
            val sameDate = day("different-id", FIRST.plusDays(1), "UTC", 5, "Other", existingHabit)
            val addedDay = day("added", FIRST.plusDays(2), "Asia/Kolkata", 4, "Imported", addedHabit)
            val counts = repository.importRecords(
                listOf(existingHabit.copy(name = "Overwritten?"), addedHabit),
                listOf(sameId, sameDate, addedDay),
            )
            assertEquals(DisciplineImportCounts(1, 1, 1, 1, 2, 2), counts)
            val after = snapshot(repository)
            assertEquals((before.first + addedHabit).toSet(), after.first.toSet())
            assertEquals(before.second + addedDay, after.second)
            assertEquals(listOf(2L, 3L, 3L), tableCounts(fixture))
        }
    }

    @Test fun unreadableStorageKeyRejectsImportBeforeAnyWrite(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val repository = repository(fixture)
            val original = habit("stored", FIRST)
            repository.importRecords(listOf(original), listOf(day("stored", FIRST, "UTC", 2, "Stored", original)))
            val before = snapshot(repository)
            val counts = tableCounts(fixture)
            val missingAlias = "${fixture.alias}.missing"
            val unreadable = LocalDisciplineRepository(fixture.database, KeystoreFieldCipher(missingAlias))
            val added = habit("blocked", FIRST.plusDays(1))
            val failure = assertThrows(IllegalArgumentException::class.java) { runBlocking {
                unreadable.importRecords(listOf(added), listOf(day("blocked", FIRST.plusDays(1),
                    "UTC", 4, "Blocked", added)))
            } }
            assertTrue(failure.message.orEmpty().contains("Personal storage key unavailable"))
            assertNull(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(missingAlias, null))
            assertEquals(counts, tableCounts(fixture))
            assertEquals(before, snapshot(repository))
        }
    }

    @Test fun exportBoundsAndWindowedReaderCoverTheWholeHistory(): Unit = runBlocking {
        StorageTestFixture().use { fixture ->
            val repository = repository(fixture)
            assertNull(repository.exportBounds())
            val (habits, days) = history()
            repository.importRecords(habits, days)
            assertEquals(DisciplineExportBounds(FIRST, LAST), repository.exportBounds())
            assertEquals(2, repository.habits(firstWindow()).size)
            assertEquals(days.take(1), repository.checkins(firstWindow()))
            assertEquals(days.takeLast(1), repository.checkins(lastWindow()))
            val exported = DisciplineBackupFormat.readRepository(repository, "5.1", 123L)
            assertEquals(habits.toSet(), exported.habits.toSet())
            assertEquals(days, exported.checkins)
            assertEquals(DisciplineBackupFormat.SCHEMA_VERSION, exported.schemaVersion)
        }
    }

    private fun repository(fixture: StorageTestFixture) = LocalDisciplineRepository(fixture.database, fixture.cipher)

    private fun history(): Pair<List<HabitDefinition>, List<CheckinDay>> {
        val archived = habit("archived", FIRST, FIRST.plusDays(30), HabitCadence.DAILY)
        val weekly = habit("weekly", FIRST, cadence = HabitCadence.weekdays(setOf(DayOfWeek.MONDAY)))
        return listOf(archived, weekly) to listOf(
            day("first", FIRST, "Pacific/Auckland", 1, "First private note", archived),
            day("last", LAST, "America/Los_Angeles", 5, "Last private note", weekly),
        )
    }

    private fun habit(tag: String, start: LocalDate, archivedAt: LocalDate? = null,
        cadence: HabitCadence = HabitCadence.DAILY) = HabitDefinition(
        "Habit $tag", start, cadence, "Description $tag", archivedAt, metadata("habit-$tag"),
    )

    private fun day(tag: String, date: LocalDate, zone: String, mood: Int, note: String,
        habit: HabitDefinition): CheckinDay {
        val checkin = DailyCheckin(date, zone, mood, note, metadata("day-$tag"))
        return CheckinDay(checkin, listOf(HabitTick(checkin.metadata.id, habit.metadata.id,
            tag != "last", id("tick-$tag"))))
    }

    private fun metadata(tag: String) = RecordMetadata(id(tag), 10L, 10L, 0L)
    private fun id(tag: String) = java.util.UUID.nameUUIDFromBytes(tag.toByteArray()).toString()
    private fun firstWindow() = DisciplineDateRange(FIRST, FIRST.plusDays(29))
    private fun lastWindow() = DisciplineDateRange(LAST.minusDays(1), LAST)

    private suspend fun snapshot(repository: LocalDisciplineRepository) =
        DisciplineBackupFormat.readRepository(repository, "5.1", 123L).let { it.habits to it.checkins }

    private fun tableCounts(fixture: StorageTestFixture) = listOf(
        "habit_definitions", "daily_checkins", "habit_ticks",
    ).map { table ->
        fixture.database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }

    private companion object {
        val FIRST: LocalDate = LocalDate.of(2024, 1, 1)
        val LAST: LocalDate = LocalDate.of(2025, 3, 10)
    }
}
