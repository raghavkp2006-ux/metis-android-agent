package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.DailyCheckin
import dev.metis.agent.domain.storage.DisciplineDates
import dev.metis.agent.domain.storage.HabitCadence
import dev.metis.agent.domain.storage.HabitDefinition
import dev.metis.agent.domain.storage.RecordMetadata

internal class DisciplineCodec(private val cipher: FieldCipher) {
    fun encode(value: HabitDefinition, metadata: StoredMetadata) = HabitDefinitionEntity(
        metadata, encrypt(value.name, "habit_definitions", metadata.id, "name"),
        value.description?.let { encrypt(it, "habit_definitions", metadata.id, "description") },
        value.cadence.weekdayMask, value.active, value.startLocalDate.toString(), value.archivedAt?.toString(),
    )

    fun decode(row: HabitDefinitionEntity) = HabitDefinition(
        decrypt(row.name, "habit_definitions", row.metadata.id, "name"),
        DisciplineDates.parse(row.startLocalDate), HabitCadence(row.cadence),
        row.description?.let { decrypt(it, "habit_definitions", row.metadata.id, "description") },
        row.archivedAt?.let(DisciplineDates::parse), row.metadata.domain(),
    ).also { check(it.active == row.active) }

    fun encode(value: DailyCheckin, metadata: StoredMetadata) = DailyCheckinEntity(
        metadata, value.localDate.toString(), value.zoneId,
        value.mood?.let { encrypt(it.toString(), "daily_checkins", metadata.id, "mood") },
        value.note?.let { encrypt(it, "daily_checkins", metadata.id, "note") },
    )

    fun decode(row: DailyCheckinEntity) = DailyCheckin(
        DisciplineDates.parse(row.localDate), row.zoneId,
        row.mood?.let { decrypt(it, "daily_checkins", row.metadata.id, "mood").toInt() },
        row.note?.let { decrypt(it, "daily_checkins", row.metadata.id, "note") }, row.metadata.domain(),
    )

    private fun encrypt(value: String, table: String, id: String, field: String) =
        cipher.encrypt(value, "$table/$id/$field")
    private fun decrypt(value: ByteArray, table: String, id: String, field: String) =
        cipher.decrypt(value, "$table/$id/$field")
}

private fun StoredMetadata.domain() = RecordMetadata(id, createdAt, updatedAt, revision)
