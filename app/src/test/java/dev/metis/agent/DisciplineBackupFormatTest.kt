package dev.metis.agent

import dev.metis.agent.data.storage.DisciplineBackupFormat
import dev.metis.agent.data.storage.DisciplineBackupFailure
import dev.metis.agent.data.storage.DisciplineBackupPayload
import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import dev.metis.agent.domain.storage.RecordMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DisciplineBackupFormatTest {
    private val day = LocalDate.parse("2026-03-10")
    private val habitId = uuid(1)
    private val checkinId = uuid(2)
    private val tickId = uuid(3)
    private val habit = HabitDefinition("Read a book marker-ö", day.minusDays(500),
        description = "secret description marker", metadata = RecordMetadata(habitId, 1, 2, 1))
    private val checkin = DailyCheckin(day, "Asia/Kolkata", 4, "private note marker",
        RecordMetadata(checkinId, 2, 3, 1))
    private val payload = DisciplineBackupPayload("0.1.0", 7, 1_800_000_000_000,
        listOf(habit), listOf(CheckinDay(checkin, listOf(HabitTick(checkinId, habitId, true, tickId)))))
    private val password = "correct horse battery staple".toCharArray()

    @Test fun containerAndPayloadRoundTripAndNoPayloadMarkersArePlaintext() {
        val file = DisciplineBackupFormat.encrypt(DisciplineBackupFormat.encode(payload), password)
        val plain = DisciplineBackupFormat.decrypt(ByteArrayInputStream(file), password)
        assertEquals(payload, DisciplineBackupFormat.decodePayload(plain))
        val visible = String(file, StandardCharsets.ISO_8859_1)
        listOf("Read a book marker-ö", "secret description marker", "private note marker", "2026-03-10",
            "Asia/Kolkata").forEach { assertFalse("plaintext marker leaked: $it", visible.contains(it)) }
        assertFalse("record count encoding leaked", file.map { it.toInt() and 0xff }.windowed(4)
            .any { it == listOf(0, 0, 0, 1) })
    }

    @Test fun wrongPasswordTamperingAndEveryContainerRegionFailAuthentication() {
        val valid = DisciplineBackupFormat.encrypt(DisciplineBackupFormat.encode(payload), password)
        assertThrows(DisciplineBackupFailure.Authentication::class.java) {
            DisciplineBackupFormat.decrypt(ByteArrayInputStream(valid), "incorrect horse battery staple".toCharArray())
        }
        val regions = listOf(0, 8, 10, 14, 30, 41, 42, valid.lastIndex)
        regions.forEach { index ->
            val altered = valid.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertThrows("mutated file offset $index", IOException::class.java) {
                DisciplineBackupFormat.decrypt(ByteArrayInputStream(altered), password)
            }
        }
    }

    @Test fun truncationVersionMismatchWrongAadAndOversizeAreRejected() {
        val valid = DisciplineBackupFormat.encrypt(DisciplineBackupFormat.encode(payload), password)
        listOf(valid.copyOf(20), valid.copyOf(valid.size - 1)).forEach { truncated ->
            assertThrows(IOException::class.java) {
                DisciplineBackupFormat.decrypt(ByteArrayInputStream(truncated), password)
            }
        }
        val version = valid.copyOf().also { it[9] = 2 }
        assertThrows(DisciplineBackupFailure.UnsupportedVersion::class.java) {
            DisciplineBackupFormat.decrypt(ByteArrayInputStream(version), password)
        }
        val wrongAad = valid.copyOf().also { it[30] = (it[30].toInt() xor 0x20).toByte() }
        assertThrows(IOException::class.java) {
            DisciplineBackupFormat.decrypt(ByteArrayInputStream(wrongAad), password)
        }
        val oversized = ByteArrayInputStream(ByteArray(DisciplineBackupFormat.MAX_FILE_BYTES + 1))
        assertThrows(DisciplineBackupFailure.Oversized::class.java) {
            DisciplineBackupFormat.decrypt(oversized, password)
        }
    }

    @Test fun exportNoncesAreUnique() {
        val first = DisciplineBackupFormat.encrypt(DisciplineBackupFormat.encode(payload), password)
        val second = DisciplineBackupFormat.encrypt(DisciplineBackupFormat.encode(payload), password)
        assertFalse(first.copyOfRange(30, 42).contentEquals(second.copyOfRange(30, 42)))
    }

    @Test fun passwordMinimumCountsUnicodeCodePoints() {
        assertFalse(DisciplineBackupFormat.hasMinimumPasswordLength("😀😀😀😀😀😀".toCharArray()))
        assertTrue(DisciplineBackupFormat.hasMinimumPasswordLength("😀😀😀😀😀😀😀😀😀😀😀😀".toCharArray()))
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineBackupFormat.encrypt(DisciplineBackupFormat.encode(payload), "12345678901".toCharArray())
        }
    }

    @Test fun validatorRejectsDuplicateIdsDaysMissingAndMismatchedTicksAndUnsupportedSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineBackupFormat.validate(payload.copy(habits = listOf(habit, habit)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineBackupFormat.validate(payload.copy(checkins = listOf(
                CheckinDay(checkin, emptyList()),
        CheckinDay(checkin.copy(metadata = RecordMetadata(uuid(4))), emptyList()))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineBackupFormat.validate(payload.copy(checkins = listOf(CheckinDay(checkin,
                listOf(HabitTick(uuid(5), habitId, true, tickId))))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineBackupFormat.validate(payload.copy(habits = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineBackupFormat.validate(payload.copy(schemaVersion = 6))
        }
        assertThrows(IllegalArgumentException::class.java) {
            val mondayOnly = HabitDefinition("Monday", day, HabitCadence(1), metadata = RecordMetadata(uuid(6)))
            DisciplineBackupFormat.validate(payload.copy(habits = listOf(mondayOnly)))
        }
    }

    @Test fun payloadParserRejectsInvalidDateMoodCadenceArchiveAndTickReferences() {
        listOf(
            rawPayload(RawPayloadOptions(date = "2026-3-10")),
            rawPayload(RawPayloadOptions(mood = 0)),
            rawPayload(RawPayloadOptions(mood = 6)),
            rawPayload(RawPayloadOptions(start = "2026-3-01")),
            rawPayload(RawPayloadOptions(cadence = 128)),
            rawPayload(RawPayloadOptions(archived = "2026-03-01")),
            rawPayload(RawPayloadOptions(tickHabit = uuid(99))),
            rawPayload(RawPayloadOptions(tickCheckin = uuid(98))),
            rawPayload(RawPayloadOptions(schema = 6)),
            rawPayload(RawPayloadOptions(habitCount = DisciplineBackupFormat.MAX_HABITS + 1)),
            rawPayload(RawPayloadOptions(checkinCount = DisciplineBackupFormat.MAX_CHECKINS + 1)),
            rawPayload(RawPayloadOptions(tickCount = DisciplineBackupFormat.MAX_TICKS + 1)),
        ).forEach { bytes -> assertThrows(IOException::class.java) { DisciplineBackupFormat.decodePayload(bytes) } }
        assertThrows(IllegalArgumentException::class.java) {
            DisciplineBackupFormat.encrypt(ByteArray(DisciplineBackupFormat.MAX_FILE_BYTES), password)
        }
    }

    @Test fun exportWalksBoundedWindowsAcrossMoreThanFourHundredDays() = runBlocking {
        val old = LocalDate.parse("2024-01-01")
        val later = old.plusDays(850)
        val repo = FakeDisciplineRepository(Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC),
            { ZoneOffset.UTC })
        val oldCheckin = DailyCheckin(old, "UTC", metadata = RecordMetadata(uuid(20)))
        val laterCheckin = DailyCheckin(later, "UTC", metadata = RecordMetadata(uuid(21)))
        repo.seedForExport(listOf(HabitDefinition("long-running", old, metadata = RecordMetadata(uuid(22)))),
            listOf(CheckinDay(oldCheckin, emptyList()), CheckinDay(laterCheckin, emptyList())))
        val result = DisciplineBackupFormat.readRepository(repo, "0.1.0", 1)
        assertEquals(3, repo.rangeReadCount / 2)
        assertEquals(listOf(old, later), result.checkins.map { it.checkin.localDate })
    }

    @Test fun importConflictSkipsExistingIdsAndDateWithoutOverwriting() = runBlocking {
        val repo = repository()
        repo.seedForExport(listOf(habit), listOf(CheckinDay(checkin,
            listOf(HabitTick(checkinId, habitId, true, tickId)))))
        val incoming = DisciplineBackupFormat.preview(payload)
        val counts = repo.importRecords(incoming.habits, incoming.checkins)
        assertEquals(0, counts.habitsAdded)
        assertEquals(0, counts.checkinsAdded)
        assertEquals(0, counts.ticksAdded)
        assertEquals(1, counts.habitsSkipped)
        assertEquals(1, counts.checkinsSkipped)
        assertEquals(1, counts.ticksSkipped)
        assertEquals("Read a book marker-ö", repo.habits(DisciplineDateRange(day.minusDays(1), day)).single().name)
    }

    @Test fun injectedMidImportFailureRollsBackEveryRecord() = runBlocking {
        val repo = repository()
        val before = repo.allStored()
        repo.failImportAfterWriteAt = 2
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.importRecords(listOf(habit), listOf(CheckinDay(checkin,
                listOf(HabitTick(checkinId, habitId, true, tickId))))) }
        }
        assertEquals(before, repo.allStored())
    }

    private fun repository() = FakeDisciplineRepository(
        Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC), { ZoneOffset.UTC })

    private data class RawPayloadOptions(
        val date: String = "2026-03-10",
        val mood: Int? = 4,
        val cadence: Int = 0,
        val archived: String? = null,
        val tickHabit: String? = null,
        val schema: Int = 7,
        val start: String = "2026-03-01",
        val tickCheckin: String? = null,
        val habitCount: Int = 1,
        val checkinCount: Int = 1,
        val tickCount: Int = 1,
    )

    private fun rawPayload(options: RawPayloadOptions): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            fun str(value: String) { val encoded = value.toByteArray(); out.writeInt(encoded.size); out.write(encoded) }
            fun nullable(value: String?) { out.writeBoolean(value != null); value?.let(::str) }
            fun metadata(id: String) { str(id); out.writeLong(1); out.writeLong(2); out.writeLong(1) }
            out.writeInt(0x50415931); str("0.1.0"); out.writeInt(options.schema); out.writeLong(1)
            out.writeInt(options.habitCount); metadata(habitId); str("Book"); nullable(null)
            out.writeInt(options.cadence)
            str(options.start); nullable(options.archived)
            out.writeInt(options.checkinCount); metadata(checkinId); str(options.date); str("UTC")
            out.writeBoolean(options.mood != null)
            options.mood?.let(out::writeInt); nullable(null); out.writeInt(options.tickCount)
            str(options.tickCheckin ?: checkinId); str(options.tickHabit ?: habitId)
            out.writeBoolean(true); str(tickId)
        }
    }.toByteArray()

    private fun uuid(value: Int) = UUID(0, value.toLong()).toString()
}
