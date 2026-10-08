package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.PREFERENCE_VALUE_SCHEMA_VERSION
import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceSource
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.PreferenceValueKind
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedPreference
import java.time.DayOfWeek
import java.time.LocalTime

internal class PreferenceCodec(private val cipher: FieldCipher) {
    fun encode(preference: SavedPreference, metadata: StoredMetadata): PreferenceEntity {
        val (kind, raw) = when (val value = preference.value) {
            is PreferenceValue.DayStart -> PreferenceValueKind.LOCAL_TIME to value.time.toString()
            is PreferenceValue.FocusMinutes -> PreferenceValueKind.DURATION_MINUTES to value.minutes.toString()
            is PreferenceValue.WeekStart -> PreferenceValueKind.WEEKDAY to value.day.name
        }
        return PreferenceEntity(
            metadata, preference.key.name, cipher.encrypt(raw, binding(metadata.id)), kind.name,
            PREFERENCE_VALUE_SCHEMA_VERSION, preference.source.name,
        )
    }

    fun decode(row: PreferenceEntity): SavedPreference {
        require(row.valueSchemaVersion == PREFERENCE_VALUE_SCHEMA_VERSION)
        val raw = cipher.decrypt(row.typedValue, binding(row.metadata.id))
        val value = when (PreferenceValueKind.valueOf(row.valueKind)) {
            PreferenceValueKind.LOCAL_TIME -> PreferenceValue.DayStart(LocalTime.parse(raw))
            PreferenceValueKind.DURATION_MINUTES -> PreferenceValue.FocusMinutes(raw.toInt())
            PreferenceValueKind.WEEKDAY -> PreferenceValue.WeekStart(DayOfWeek.valueOf(raw))
        }
        return SavedPreference(
            PreferenceKey.valueOf(row.key), value,
            RecordMetadata(row.metadata.id, row.metadata.createdAt, row.metadata.updatedAt, row.metadata.revision),
            PreferenceSource.valueOf(row.source),
        )
    }

    private fun binding(id: String) = "preferences/$id/typed_value"
}
