package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.SavedPreference
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun youShowsExplicitSavedValuesAcrossRecreationUpdatesAndDeletion(): Unit = runBlocking {
        val repository = PersonalStorage.repository(composeRule.activity)
        val original = SavedPreference(PreferenceKey.FOCUS_BLOCK_MINUTES, PreferenceValue.FocusMinutes(25))
        var savedRevision = 0L
        var deleted = false
        try {
            repository.preferences.savePreference(original)
            composeRule.onNodeWithTag("nav_YOU").performClick()
            waitFor("25 minutes · Explicit preference")
            composeRule.onNodeWithText("Preferred focus block length").performScrollTo().assertIsDisplayed()
            composeRule.activityRule.scenario.recreate()
            waitFor("25 minutes · Explicit preference")
            repository.preferences.savePreference(original.copy(value = PreferenceValue.FocusMinutes(40)))
            savedRevision = 1L
            waitFor("40 minutes · Explicit preference")
            repository.preferences.deletePreference(original.metadata.id, savedRevision)
            deleted = true
            waitFor("No saved preferences")
            composeRule.onNodeWithText("No saved preferences").performScrollTo().assertIsDisplayed()
        } finally {
            if (!deleted) repository.preferences.deletePreference(original.metadata.id, savedRevision)
        }
    }

    private fun waitFor(text: String) = composeRule.waitUntil(10_000L) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
