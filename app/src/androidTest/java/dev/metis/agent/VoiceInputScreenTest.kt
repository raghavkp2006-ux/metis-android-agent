package dev.metis.agent

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
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

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend() }


    @Before fun resetVoiceConsent() {
        VoiceRuntime.setOnlineFallbackAllowed(composeRule.activity, false)
    }

    @After fun clearVoiceConsent() {
        VoiceRuntime.setOnlineFallbackAllowed(composeRule.activity, false)
    }

    @Test
    fun onlineFallbackIsOffByDefaultAndExplicitlyControlledInYou() {
        assertEquals(false, VoiceRuntime.onlineFallbackAllowed(composeRule.activity))
        composeRule.navigateWorkspace("SETTINGS")
        val toggle = composeRule.onNodeWithTag("voice_online_toggle")
        toggle.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(true, VoiceRuntime.onlineFallbackAllowed(composeRule.activity))
        toggle.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(false, VoiceRuntime.onlineFallbackAllowed(composeRule.activity))
    }

    @Test
    fun microphonePurposeIsExplainedBeforeRuntimePermissionRequest() {
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.revokeRuntimePermission(
                composeRule.activity.packageName, Manifest.permission.RECORD_AUDIO)
        }
        composeRule.navigateWorkspace("CHAT")
        composeRule.enterFrontend()
        composeRule.onNodeWithTag("voice_button").performClick()
        composeRule.onNodeWithText("Use speech input?").assertIsDisplayed()
        assertEquals(PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(composeRule.activity, Manifest.permission.RECORD_AUDIO))
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.enterFrontend()
    }
}
