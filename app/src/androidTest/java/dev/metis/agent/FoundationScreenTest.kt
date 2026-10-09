package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.storage.SavedEvent
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedReminder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun savedPeopleRemindersAndHistorySurviveRecreationWithoutEnablingActions(): Unit = runBlocking {
        val stores = PersonalStorage.repository(composeRule.activity).foundation
        val person = SavedPerson("Synthetic saved screen person")
        val reminder = SavedReminder("Synthetic saved screen reminder", 10, "1970-01-01T00:00:00.010", "UTC",
            "APPROXIMATE", "PENDING", UUID.randomUUID().toString())
        val event = SavedEvent("MEMORY_CREATED", "USER", 10, 0.5f, 1)
        try {
            stores.person.save(person)
            stores.reminder.save(reminder)
            stores.event.save(event)
            composeRule.onNodeWithTag("nav_YOU").performClick()
            waitFor(person.displayName)
            composeRule.onNodeWithTag("nav_PLAN").performClick()
            waitFor(reminder.title)
            composeRule.onNodeWithText("Saved reminders · only accepted registrations execute")
                .performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("nav_TIMELINE").performClick()
            waitFor("MEMORY CREATED")
            composeRule.activityRule.scenario.recreate()
            waitFor("MEMORY CREATED")
            composeRule.onNodeWithText("Write a request").performClick()
            composeRule.onNodeWithText("Send request").performScrollTo().assertIsNotEnabled()
        } finally {
            stores.event.delete(event.metadata.id, 0)
            stores.reminder.delete(reminder.metadata.id, 0)
            stores.person.delete(person.metadata.id, 0)
        }
    }

    private fun waitFor(text: String) {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private companion object { const val TIMEOUT = 10_000L }
}
