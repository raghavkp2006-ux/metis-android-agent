package dev.metis.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.domain.insights.BehavioralReflectionEngine
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedEvent
import dev.metis.agent.platform.BehavioralAnalysisRuntime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BehavioralReflectionScreenTest {
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend() }


    @Before fun resetConsent() = BehavioralAnalysisRuntime.setEnabled(composeRule.activity, false)

    @After fun clearConsent() = BehavioralAnalysisRuntime.setEnabled(composeRule.activity, false)

    @Test fun consentDefaultsOffAndReviewsRequireExplicitOptIn() {
        assertFalse(BehavioralAnalysisRuntime.enabled(composeRule.activity))
        val repository = PersonalStorage.repository(composeRule.activity)
        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.now().atZone(zone).toLocalDate()
        val syntheticEvents = (1L..3L).map { weeksBack ->
            val timestamp = today.minusWeeks(weeksBack).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
            SavedEvent("TASK_COMPLETED", "USER", timestamp, 0.5f, 1,
                metadata = RecordMetadata(createdAt = timestamp))
        }
        runBlocking { syntheticEvents.forEach { repository.foundation.event.save(it) } }
        try {
        composeRule.navigateWorkspace("INSIGHTS")
        composeRule.onNodeWithText("Daily and weekly reflection").performScrollTo().assertIsDisplayed()
        val consent = composeRule.onNodeWithTag("behavioral_analysis_toggle").performScrollTo()
        consent.assertIsOff()
        composeRule.onNodeWithText("Review today").assertDoesNotExist()

        consent.performClick()
        composeRule.waitUntil(TIMEOUT) { BehavioralAnalysisRuntime.enabled(composeRule.activity) }
        composeRule.onNodeWithTag("behavioral_analysis_toggle").assertIsOn()
        composeRule.onNodeWithText("Review today").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Review this week").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Review this week").performClick()
        composeRule.waitUntil(TIMEOUT) {
            runBlocking { repository.foundation.derivedInsight.observe().first().any {
                it.methodVersion == BehavioralReflectionEngine.METHOD_VERSION
            } }
        }
        composeRule.onNodeWithText("Saved reflections").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Delete saved reflections").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete saved reflections?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        composeRule.waitUntil(TIMEOUT) {
            runBlocking { repository.foundation.derivedInsight.observe().first().none {
                it.methodVersion == BehavioralReflectionEngine.METHOD_VERSION
            } }
        }

        composeRule.onNodeWithText("Review this week").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            runBlocking { repository.foundation.derivedInsight.observe().first().any {
                it.methodVersion == BehavioralReflectionEngine.METHOD_VERSION
            } }
        }

        composeRule.onNodeWithTag("behavioral_analysis_toggle").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) { !BehavioralAnalysisRuntime.enabled(composeRule.activity) }
        composeRule.waitUntil(TIMEOUT) {
            runBlocking { repository.foundation.derivedInsight.observe().first().none {
                it.methodVersion == BehavioralReflectionEngine.METHOD_VERSION
            } }
        }
        composeRule.onNodeWithTag("behavioral_analysis_toggle").assertIsOff()
        composeRule.onNodeWithText("Review today").assertDoesNotExist()
        } finally {
            BehavioralAnalysisRuntime.setEnabled(composeRule.activity, false)
            runBlocking {
                repository.foundation.derivedInsight.observe().first().filter {
                    it.methodVersion == BehavioralReflectionEngine.METHOD_VERSION
                }.forEach { repository.foundation.derivedInsight.delete(it.metadata.id, it.metadata.revision) }
                syntheticEvents.forEach { repository.foundation.event.delete(it.metadata.id, it.metadata.revision) }
            }
        }
    }

    private companion object { const val TIMEOUT = 10_000L }
}
