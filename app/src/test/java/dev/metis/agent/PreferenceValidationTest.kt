package dev.metis.agent

import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.SavedPreference
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertThrows
import org.junit.Test

class PreferenceValidationTest {
    @Test
    fun registeredKeysRequireTheirExactValueType() {
        SavedPreference(PreferenceKey.DAY_START_TIME, PreferenceValue.DayStart(LocalTime.of(7, 30)))
        SavedPreference(PreferenceKey.FOCUS_BLOCK_MINUTES, PreferenceValue.FocusMinutes(25))
        SavedPreference(PreferenceKey.WEEK_START_DAY, PreferenceValue.WeekStart(DayOfWeek.MONDAY))
        assertThrows(IllegalArgumentException::class.java) {
            SavedPreference(PreferenceKey.DAY_START_TIME, PreferenceValue.FocusMinutes(25))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavedPreference(PreferenceKey.FOCUS_BLOCK_MINUTES, PreferenceValue.WeekStart(DayOfWeek.MONDAY))
        }
    }

    @Test
    fun focusDurationsAreBounded() {
        PreferenceValue.FocusMinutes(5)
        PreferenceValue.FocusMinutes(240)
        assertThrows(IllegalArgumentException::class.java) { PreferenceValue.FocusMinutes(4) }
        assertThrows(IllegalArgumentException::class.java) { PreferenceValue.FocusMinutes(241) }
    }

    @Test
    fun dayStartUsesMinutePrecisionWithoutInventingSeconds() {
        PreferenceValue.DayStart(LocalTime.MIDNIGHT)
        PreferenceValue.DayStart(LocalTime.of(23, 59))
        assertThrows(IllegalArgumentException::class.java) { PreferenceValue.DayStart(LocalTime.of(6, 0, 1)) }
        assertThrows(IllegalArgumentException::class.java) { PreferenceValue.DayStart(LocalTime.of(6, 0, 0, 1)) }
    }
}
