package dev.metis.agent

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.metis.agent.platform.VoiceRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceInputScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun resetVoiceConsent() {
        VoiceRuntime.setOnlineFallbackAllowed(composeRule.activity, false)
    }

    @After fun clearVoiceConsent() {
        VoiceRuntime.setOnlineFallbackAllowed(composeRule.activity, false)
    }

    @Test
    fun onlineFallbackIsOffByDefaultAndExplicitlyControlledInYou() {
        assertEquals(false, VoiceRuntime.onlineFallbackAllowed(composeRule.activity))
        composeRule.onNodeWithTag("nav_YOU").performClick()
        val toggle = composeRule.onNodeWithTag("voice_online_toggle").performScrollTo()
        toggle.performClick()
        assertEquals(true, VoiceRuntime.onlineFallbackAllowed(composeRule.activity))
        toggle.performClick()
        assertEquals(false, VoiceRuntime.onlineFallbackAllowed(composeRule.activity))
    }

    @Test
    fun microphonePurposeIsExplainedBeforeRuntimePermissionRequest() {
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.revokeRuntimePermission(
                composeRule.activity.packageName, Manifest.permission.RECORD_AUDIO)
        }
        composeRule.onNodeWithTag("nav_AGENT").performClick()
        composeRule.onNodeWithText("Write a request").performClick()
        composeRule.onNodeWithText("Use voice").performScrollTo().performClick()
        composeRule.onNodeWithText("Use speech input?").assertIsDisplayed()
        assertEquals(PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(composeRule.activity, Manifest.permission.RECORD_AUDIO))
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.onNodeWithText("Close draft").performScrollTo().performClick()
    }
}
