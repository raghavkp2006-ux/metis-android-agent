package dev.metis.agent.domain.storage

import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/** Registered planning inputs only. Authority and consent settings require a separate policy flow. */
enum class PreferenceKey { DAY_START_TIME, FOCUS_BLOCK_MINUTES, WEEK_START_DAY }
enum class PreferenceSource { EXPLICIT }
enum class PreferenceValueKind { LOCAL_TIME, DURATION_MINUTES, WEEKDAY }

sealed interface PreferenceValue {
    data class DayStart(val time: LocalTime) : PreferenceValue {
        init { require(time.second == 0 && time.nano == 0) }
    }
    data class FocusMinutes(val minutes: Int) : PreferenceValue {
        init { require(minutes in MIN_FOCUS_MINUTES..MAX_FOCUS_MINUTES) }
    }
    data class WeekStart(val day: DayOfWeek) : PreferenceValue
}

data class SavedPreference(
    val key: PreferenceKey,
    val value: PreferenceValue,
    val metadata: RecordMetadata = RecordMetadata(),
    val source: PreferenceSource = PreferenceSource.EXPLICIT,
) {
    init {
        require(when (key) {
            PreferenceKey.DAY_START_TIME -> value is PreferenceValue.DayStart
            PreferenceKey.FOCUS_BLOCK_MINUTES -> value is PreferenceValue.FocusMinutes
            PreferenceKey.WEEK_START_DAY -> value is PreferenceValue.WeekStart
        })
    }
}

interface PreferenceRepository {
    fun observePreferences(): Flow<List<SavedPreference>>
    suspend fun savePreference(preference: SavedPreference)
    suspend fun deletePreference(id: String, revision: Long)
}

const val PREFERENCE_VALUE_SCHEMA_VERSION = 1
private const val MIN_FOCUS_MINUTES = 5
private const val MAX_FOCUS_MINUTES = 240
