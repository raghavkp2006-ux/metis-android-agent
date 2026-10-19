package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.CheckinDay
import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDateRange
import dev.metis.agent.domain.storage.DisciplineDates
import dev.metis.agent.domain.storage.DisciplineExportBounds
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.HabitTick
import dev.metis.agent.domain.storage.RecordMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val METADATA_LONG_FIELDS = 3
private const val CANONICAL_DATE_BYTES = 10
private const val UUID_TEXT_BYTES = 36
private const val PAYLOAD_MAGIC = 0x50415931

internal data class DisciplineBackupPayload(
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val habits: List<HabitDefinition>,
    val checkins: List<CheckinDay>,
)

internal data class DisciplineBackupPreview(
    val habits: List<HabitDefinition>,
    val checkins: List<CheckinDay>,
    val minDate: LocalDate?,
    val maxDate: LocalDate?,
) {
    val tickCount: Int get() = checkins.sumOf { it.ticks.size }
}

internal class StagedDisciplineImport internal constructor(
    internal val payload: DisciplineBackupPayload,
    val preview: DisciplineBackupPreview,
)

internal sealed class DisciplineBackupFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Authentication(cause: Throwable) : DisciplineBackupFailure(
        "The password is incorrect or the encrypted file was modified.", cause,
    )
    class Corrupted(message: String, cause: Throwable? = null) : DisciplineBackupFailure(message, cause)
    class UnsupportedVersion : DisciplineBackupFailure("This export version is not supported.")
    class Oversized : DisciplineBackupFailure("The export exceeds the 16 MB size limit.")
    class Key(cause: Throwable) : DisciplineBackupFailure("The encryption key could not be used.", cause)
    class Storage(cause: Throwable) : DisciplineBackupFailure("The file could not be read or saved.", cause)
}

internal class DisciplineBackupManager(private val repository: dev.metis.agent.domain.storage.DisciplineRepository) {
    suspend fun createExport(password: CharArray, appVersion: String, createdAt: Long): ByteArray {
        val payload = DisciplineBackupFormat.readRepository(repository, appVersion, createdAt)
        val plain = DisciplineBackupFormat.encode(payload)
        return try {
            DisciplineBackupFormat.encrypt(plain, password)
        } finally {
            plain.fill(0)
        }
    }

    fun stageImport(input: InputStream, password: CharArray): StagedDisciplineImport {
        val plain = DisciplineBackupFormat.decrypt(input, password)
        val payload = try {
            DisciplineBackupFormat.decodePayload(plain)
        } finally {
            plain.fill(0)
        }
        return StagedDisciplineImport(payload, DisciplineBackupFormat.preview(payload))
    }

    suspend fun applyImport(staged: StagedDisciplineImport) = repository.importRecords(
        staged.payload.habits, staged.payload.checkins,
    )
}

/** Version 1 is an opaque, authenticated binary container. No plaintext payload is staged on disk. */
internal object DisciplineBackupFormat {
    const val SCHEMA_VERSION = 7
    const val MIN_PASSWORD_LENGTH = 12
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_HABITS = 2_000
    const val MAX_CHECKINS = 20_000
    const val MAX_TICKS = 200_000
    internal const val ITERATIONS = 600_000
    internal const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    internal val magic = "METISEXP".toByteArray(StandardCharsets.US_ASCII)
    internal const val VERSION = 1
    internal const val GCM_TAG_BYTES = TAG_BITS / Byte.SIZE_BITS
    internal const val MAX_KDF_ITERATIONS = 2_000_000
    internal const val READ_BUFFER_BYTES = 8192
    internal const val HEADER_BYTES = 8 + Short.SIZE_BYTES + Int.SIZE_BYTES + SALT_BYTES + NONCE_BYTES

    fun encode(payload: DisciplineBackupPayload): ByteArray {
        BackupValidation.validate(payload)
        require(BackupPayloadCodec.estimatedPayloadBytes(payload) <= MAX_FILE_BYTES - HEADER_BYTES - GCM_TAG_BYTES) {
            "Export exceeds the size limit."
        }
        val plain = BackupPayloadCodec.encodePayload(payload)
        require(plain.size + HEADER_BYTES + GCM_TAG_BYTES <= MAX_FILE_BYTES) { "Export exceeds the size limit." }
        return plain
    }

    fun encrypt(payload: ByteArray, password: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(hasMinimumPasswordLength(password)) { "Use a password with at least 12 characters." }
        require(payload.size <= MAX_FILE_BYTES - HEADER_BYTES - GCM_TAG_BYTES) { "Export exceeds the size limit." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteArrayOutputStream(HEADER_BYTES).also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.write(magic); out.writeShort(VERSION); out.writeInt(ITERATIONS); out.write(salt); out.write(nonce)
            }
        }.toByteArray()
        val key = BackupHeaderKdf.deriveKey(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(payload)
        } catch (failure: GeneralSecurityException) {
            throw DisciplineBackupFailure.Key(failure)
        } finally { key.fill(0) }
    }

    fun decrypt(input: InputStream, password: CharArray): ByteArray {
        require(hasMinimumPasswordLength(password)) { "Use a password with at least 12 characters." }
        val file = BackupHeaderKdf.readBounded(input)
        BackupHeaderKdf.requireContainerLength(file)
        val header = file.copyOfRange(0, HEADER_BYTES)
        val reader = DataInputStream(ByteArrayInputStream(header))
        val readMagic = ByteArray(magic.size).also(reader::readFully)
        BackupHeaderKdf.requireMagic(readMagic)
        val version = reader.readUnsignedShort()
        BackupHeaderKdf.requireVersion(version)
        val iterations = reader.readInt()
        BackupHeaderKdf.requireIterations(iterations)
        val salt = ByteArray(SALT_BYTES).also(reader::readFully)
        val nonce = ByteArray(NONCE_BYTES).also(reader::readFully)
        val key = BackupHeaderKdf.deriveKey(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (failure: AEADBadTagException) {
            throw DisciplineBackupFailure.Authentication(failure)
        } catch (failure: GeneralSecurityException) {
            throw DisciplineBackupFailure.Key(failure)
        } finally { key.fill(0) }
    }

    fun hasMinimumPasswordLength(password: CharArray) = BackupHeaderKdf.hasMinimumPasswordLength(password)

    fun decodePayload(bytes: ByteArray) = BackupPayloadCodec.decodePayload(bytes)
    fun validate(payload: DisciplineBackupPayload) = BackupValidation.validate(payload)
    fun preview(payload: DisciplineBackupPayload) = BackupValidation.preview(payload)
    suspend fun readRepository(
        repository: dev.metis.agent.domain.storage.DisciplineRepository,
        appVersion: String,
        createdAt: Long,
    ) = BackupPayloadCodec.readRepository(repository, appVersion, createdAt)
}

private object BackupHeaderKdf {
    fun requireContainerLength(file: ByteArray) {
        if (file.size < DisciplineBackupFormat.HEADER_BYTES + DisciplineBackupFormat.GCM_TAG_BYTES) {
            throw DisciplineBackupFailure.Corrupted("The export is truncated or invalid.")
        }
    }

    fun requireMagic(value: ByteArray) {
        if (!value.contentEquals(DisciplineBackupFormat.magic)) {
            throw DisciplineBackupFailure.Corrupted("This is not a METIS discipline export.")
        }
    }

    fun requireVersion(value: Int) {
        if (value != DisciplineBackupFormat.VERSION) throw DisciplineBackupFailure.UnsupportedVersion()
    }

    fun requireIterations(value: Int) {
        if (value < DisciplineBackupFormat.ITERATIONS || value > DisciplineBackupFormat.MAX_KDF_ITERATIONS) {
            throw DisciplineBackupFailure.Corrupted("Invalid export security parameters.")
        }
    }









    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, DisciplineBackupFormat.KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (failure: GeneralSecurityException) {
            throw DisciplineBackupFailure.Key(failure)
        } finally {
            spec.clearPassword()
        }
    }

    fun hasMinimumPasswordLength(password: CharArray): Boolean {
        var index = 0
        var codePoints = 0
        while (index < password.size) {
            if (Character.isHighSurrogate(password[index]) && index + 1 < password.size &&
                Character.isLowSurrogate(password[index + 1])) index++
            index++
            codePoints++
        }
        return codePoints >= DisciplineBackupFormat.MIN_PASSWORD_LENGTH
    }

    fun readBounded(input: InputStream): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(DisciplineBackupFormat.READ_BUFFER_BYTES)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > DisciplineBackupFormat.MAX_FILE_BYTES) throw DisciplineBackupFailure.Oversized()
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }
}

private object BackupValidation {
    private const val APP_VERSION_MAX_CHARACTERS = 128
    fun validate(payload: DisciplineBackupPayload) {
        require(payload.appVersion.isNotBlank() && payload.appVersion.length <= APP_VERSION_MAX_CHARACTERS) {
            "Invalid app version."
        }
        require(payload.schemaVersion == DisciplineBackupFormat.SCHEMA_VERSION) {
            "This database schema version is not supported."
        }
        require(payload.createdAt >= 0) { "Invalid export creation time." }
        require(payload.habits.size <= DisciplineBackupFormat.MAX_HABITS) { "Export has too many habits." }
        require(payload.checkins.size <= DisciplineBackupFormat.MAX_CHECKINS) { "Export has too many check-ins." }
        require(payload.habits.map { it.metadata.id }.toSet().size == payload.habits.size) { "Duplicate habit id." }
        require(payload.checkins.map { it.checkin.metadata.id }.toSet().size == payload.checkins.size) {
            "Duplicate check-in id."
        }
        require(payload.checkins.map { it.checkin.localDate }.toSet().size == payload.checkins.size) {
            "Duplicate check-in day."
        }
        val habits = payload.habits.associateBy { it.metadata.id }
        val ticks = payload.checkins.flatMap { it.ticks }
        require(ticks.size <= DisciplineBackupFormat.MAX_TICKS) { "Export has too many ticks." }
        require(ticks.map { it.id }.toSet().size == ticks.size) { "Duplicate tick id." }
        require(ticks.map { it.checkinId to it.habitId }.toSet().size == ticks.size) { "Duplicate check-in tick." }
        payload.checkins.forEach { day -> day.ticks.forEach { tick ->
            require(tick.checkinId == day.checkin.metadata.id) { "Tick belongs to a different check-in." }
            val habit = habits[tick.habitId] ?: throw IllegalArgumentException("Tick references a missing habit.")
            require(habit.isExpectedOn(day.checkin.localDate)) { "Tick is outside the habit window or cadence." }
        } }
    }
    fun preview(payload: DisciplineBackupPayload): DisciplineBackupPreview {
        validate(payload)
        val dates = payload.habits.flatMap { listOfNotNull(it.startLocalDate, it.archivedAt) } +
            payload.checkins.map { it.checkin.localDate }
        return DisciplineBackupPreview(payload.habits, payload.checkins, dates.minOrNull(), dates.maxOrNull())
    }
}

private object BackupPayloadCodec {
    private const val APP_VERSION_MAX_BYTES = 128
    private const val HABIT_NAME_MAX_BYTES = 800
    private const val NOTE_MAX_BYTES = 16_000
    private const val ZONE_MAX_BYTES = 256
    internal const val GCM_TAG_BYTES = 16
    private const val PAYLOAD_FIXED_BYTES = Int.SIZE_BYTES * 4 + Long.SIZE_BYTES
    private const val HABIT_FIXED_BYTES = Int.SIZE_BYTES
    private const val CHECKIN_FIXED_BYTES = Byte.SIZE_BYTES + Int.SIZE_BYTES
    private const val TICK_FIXED_BYTES = Byte.SIZE_BYTES
    private const val UUID_ENCODED_BYTES = 40L

    fun decodePayload(bytes: ByteArray): DisciplineBackupPayload {
        BackupPayloadChecks.requirePayloadSize(bytes)
        try {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            BackupPayloadChecks.requirePayloadMagic(input.readInt())
            val appVersion = input.readBoundedString(APP_VERSION_MAX_BYTES)
            val schema = input.readInt()
            BackupPayloadChecks.requireSchema(schema)
            val createdAt = input.readLong()
            val habitCount = input.readInt().checkedCount(DisciplineBackupFormat.MAX_HABITS)
            val habits = List(habitCount) {
                val metadata = input.readMetadata()
                val name = input.readBoundedString(HABIT_NAME_MAX_BYTES)
                val description = input.readNullableString(NOTE_MAX_BYTES)
                val mask = input.readInt()
                val start = input.readStrictDate()
                val archived = input.readNullableDate()
                HabitDefinition(name, start, HabitCadence(mask), description, archived, metadata)
            }
            val checkinCount = input.readInt().checkedCount(DisciplineBackupFormat.MAX_CHECKINS)
            var totalTicks = 0
            val checkins = List(checkinCount) {
                val metadata = input.readMetadata()
                val date = input.readStrictDate()
                val zone = input.readBoundedString(ZONE_MAX_BYTES)
                val mood = if (input.readBoolean()) input.readInt() else null
                val note = input.readNullableString(NOTE_MAX_BYTES)
                val tickCount = input.readInt().checkedCount(DisciplineBackupFormat.MAX_TICKS)
                totalTicks += tickCount
                BackupPayloadChecks.requireTotalTicks(totalTicks)
                val ticks = List(tickCount) {
                    HabitTick(input.readUuid(), input.readUuid(), input.readBoolean(), input.readUuid())
                }
                CheckinDay(DailyCheckin(date, zone, mood, note, metadata), ticks)
            }
            BackupPayloadChecks.requireConsumed(input)
            return DisciplineBackupPayload(appVersion, schema, createdAt, habits,
        checkins).also(DisciplineBackupFormat::validate)
        } catch (failure: IOException) {
            throw BackupPayloadChecks.normalizePayloadFailure(failure)
        } catch (failure: IllegalArgumentException) {
            throw DisciplineBackupFailure.Corrupted("The export contains invalid discipline data.", failure)
        }
    }

    suspend fun readRepository(repository: dev.metis.agent.domain.storage.DisciplineRepository,
        appVersion: String, createdAt: Long): DisciplineBackupPayload {
        val bounds = repository.exportBounds()
        if (bounds == null) return DisciplineBackupPayload(appVersion,
        DisciplineBackupFormat.SCHEMA_VERSION, createdAt, emptyList(), emptyList())
        val habits = linkedMapOf<String, HabitDefinition>()
        val checkins = linkedMapOf<String, CheckinDay>()
        var estimatedBytes = PAYLOAD_FIXED_BYTES.toLong() + stringBytes(appVersion)
        var from = bounds.from
        while (!from.isAfter(bounds.through)) {
            val through = minOf(from.plusDays(DisciplineDateRange.MAX_DAYS - 1), bounds.through)
            val range = DisciplineDateRange(from, through)
            repository.habits(range).forEach { habit ->
                if (habits.putIfAbsent(habit.metadata.id, habit) == null) {
                    estimatedBytes += estimatedHabitBytes(habit)
                    require(estimatedBytes <= DisciplineBackupFormat.MAX_FILE_BYTES -
                        DisciplineBackupFormat.HEADER_BYTES - GCM_TAG_BYTES) { "Export exceeds the size limit." }
                }
            }
            repository.checkins(range).forEach { day ->
                if (checkins.putIfAbsent(day.checkin.metadata.id, day) == null) {
                    estimatedBytes += estimatedCheckinBytes(day)
                    require(estimatedBytes <= DisciplineBackupFormat.MAX_FILE_BYTES -
                        DisciplineBackupFormat.HEADER_BYTES - GCM_TAG_BYTES) { "Export exceeds the size limit." }
                }
            }
            require(habits.size <= DisciplineBackupFormat.MAX_HABITS &&
        checkins.size <= DisciplineBackupFormat.MAX_CHECKINS &&
                checkins.values.sumOf { it.ticks.size } <= DisciplineBackupFormat.MAX_TICKS) {
                "Export exceeds record limits."
            }
            from = through.plusDays(1)
        }
        return DisciplineBackupPayload(appVersion, DisciplineBackupFormat.SCHEMA_VERSION, createdAt,
            habits.values.toList(), checkins.values.toList()).also(DisciplineBackupFormat::validate)
    }
    fun encodePayload(payload: DisciplineBackupPayload): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeInt(PAYLOAD_MAGIC); out.writeString(payload.appVersion); out.writeInt(payload.schemaVersion)
            out.writeLong(payload.createdAt); out.writeInt(payload.habits.size)
            payload.habits.forEach { habit ->
                out.writeMetadata(habit.metadata); out.writeString(habit.name);
        out.writeNullableString(habit.description)
                out.writeInt(habit.cadence.weekdayMask); out.writeString(habit.startLocalDate.toString())
                out.writeNullableString(habit.archivedAt?.toString())
            }
            out.writeInt(payload.checkins.size)
            payload.checkins.forEach { day ->
                val checkin = day.checkin
                out.writeMetadata(checkin.metadata); out.writeString(checkin.localDate.toString());
        out.writeString(checkin.zoneId)
                out.writeBoolean(checkin.mood != null); checkin.mood?.let(out::writeInt);
        out.writeNullableString(checkin.note)
                out.writeInt(day.ticks.size)
                day.ticks.forEach { tick ->
                    out.writeString(tick.checkinId); out.writeString(tick.habitId); out.writeBoolean(tick.completed)
                    out.writeString(tick.id)
                }
            }
        }
    }.toByteArray()

    fun estimatedPayloadBytes(payload: DisciplineBackupPayload): Long =
        PAYLOAD_FIXED_BYTES + stringBytes(payload.appVersion) + payload.habits.sumOf(::estimatedHabitBytes) +
            payload.checkins.sumOf(::estimatedCheckinBytes)
    private fun estimatedHabitBytes(habit: HabitDefinition): Long = metadataBytes(habit.metadata) +
        stringBytes(habit.name) + nullableStringBytes(habit.description) + HABIT_FIXED_BYTES +
        stringBytes(habit.startLocalDate.toString()) +
        nullableStringBytes(habit.archivedAt?.toString())
    private fun estimatedCheckinBytes(day: CheckinDay): Long {
        val checkin = day.checkin
        return metadataBytes(checkin.metadata) + stringBytes(checkin.localDate.toString()) +
        stringBytes(checkin.zoneId) +
            CHECKIN_FIXED_BYTES - (if (checkin.mood == null) Int.SIZE_BYTES else 0) +
            nullableStringBytes(checkin.note) + Int.SIZE_BYTES +
            day.ticks.size * (stringBytes(checkin.metadata.id) + UUID_ENCODED_BYTES * 2 + TICK_FIXED_BYTES)
    }
    private fun metadataBytes(metadata: RecordMetadata) =
        stringBytes(metadata.id) + Long.SIZE_BYTES * METADATA_LONG_FIELDS
    private fun stringBytes(value: String) = Int.SIZE_BYTES.toLong() + value.toByteArray(StandardCharsets.UTF_8).size
    private fun nullableStringBytes(value: String?) = if (value == null) 1L else 1L + stringBytes(value)
}

private object BackupPayloadChecks {
    fun requirePayloadSize(bytes: ByteArray) {
        if (bytes.size > DisciplineBackupFormat.MAX_FILE_BYTES) throw DisciplineBackupFailure.Oversized()
    }

    fun requirePayloadMagic(value: Int) {
        if (value != PAYLOAD_MAGIC) throw DisciplineBackupFailure.Corrupted("Invalid discipline payload.")
    }

    fun requireSchema(value: Int) {
        if (value != DisciplineBackupFormat.SCHEMA_VERSION) throw DisciplineBackupFailure.UnsupportedVersion()
    }

    fun requireTotalTicks(value: Int) {
        if (value > DisciplineBackupFormat.MAX_TICKS) {
            throw DisciplineBackupFailure.Corrupted("Export has too many ticks.")
        }
    }

    fun requireConsumed(input: DataInputStream) {
        if (input.available() != 0) {
            throw DisciplineBackupFailure.Corrupted("Unexpected data at the end of the export.")
        }
    }

    fun normalizePayloadFailure(failure: IOException): IOException =
        if (failure is DisciplineBackupFailure) failure
        else DisciplineBackupFailure.Corrupted("The export is truncated or invalid.", failure)
}

private fun DataOutputStream.writeString(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    writeInt(bytes.size); write(bytes)
}
private fun DataOutputStream.writeNullableString(value: String?) { writeBoolean(value != null);
        value?.let(::writeString) }
private fun DataOutputStream.writeMetadata(value: RecordMetadata) {
    writeString(value.id); writeLong(value.createdAt); writeLong(value.updatedAt); writeLong(value.revision)
}
private fun DataInputStream.readBoundedString(maxBytes: Int): String {
    val length = readInt()
    if (length < 0 || length > maxBytes) throw IOException("Invalid string size.")
    val bytes = ByteArray(length); readFully(bytes)
    return String(bytes, StandardCharsets.UTF_8).also {
        if (!it.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes)) throw IOException("Invalid UTF-8 text.")
    }
}
private fun DataInputStream.readNullableString(maxBytes: Int): String? =
    if (readBoolean()) readBoundedString(maxBytes) else null
private fun DataInputStream.readMetadata(): RecordMetadata {
    val id = readUuid(); val created = readLong(); val updated = readLong(); val revision = readLong()
    return RecordMetadata(id, created, updated, revision)
}
private fun DataInputStream.readStrictDate(): LocalDate =
    DisciplineDates.parse(readBoundedString(CANONICAL_DATE_BYTES))
private fun DataInputStream.readNullableDate(): LocalDate? =
    readNullableString(CANONICAL_DATE_BYTES)?.let(DisciplineDates::parse)
private fun DataInputStream.readUuid(): String {
    val value = readBoundedString(UUID_TEXT_BYTES)
    if (UUID.fromString(value).toString() != value) throw IOException("Invalid record id.")
    return value
}
private fun Int.checkedCount(max: Int): Int = also { if (it < 0 ||
        it > max) throw IOException("Invalid record count.") }
