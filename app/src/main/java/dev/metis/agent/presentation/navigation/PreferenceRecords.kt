package dev.metis.agent.presentation.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.metis.agent.R
import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.SavedPreference
import dev.metis.agent.presentation.designsystem.EmptyState
import dev.metis.agent.presentation.designsystem.MemoryRow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun PreferenceRecords(preferences: List<SavedPreference>) {
    Text(stringResource(R.string.preferences_title), style = MaterialTheme.typography.titleMedium)
    if (preferences.isEmpty()) EmptyState(
        stringResource(R.string.preferences_empty), stringResource(R.string.preferences_help),
    )
    preferences.forEach { preference ->
        val label = stringResource(when (preference.key) {
            PreferenceKey.DAY_START_TIME -> R.string.preference_day_start
            PreferenceKey.FOCUS_BLOCK_MINUTES -> R.string.preference_focus_length
            PreferenceKey.WEEK_START_DAY -> R.string.preference_week_start
        })
        val value = when (val typed = preference.value) {
            is PreferenceValue.DayStart -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(typed.time)
            is PreferenceValue.FocusMinutes -> pluralStringResource(
                R.plurals.preference_minutes, typed.minutes, typed.minutes,
            )
            is PreferenceValue.WeekStart -> typed.day.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
        MemoryRow(label, stringResource(R.string.preference_value, value))
    }
}
