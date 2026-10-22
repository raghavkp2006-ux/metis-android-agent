package dev.metis.agent

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeWaitDiagnosticsTest {
    @get:Rule val compose = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test fun timeoutRethrowsAndLogsRedactedMergedAndUnmergedTrees() {
        val privateText = "Synthetic private task text 2718"
        val privateDescription = "Synthetic contact description 3141"
        val conditionName = "privacy probe ${SystemClock.elapsedRealtimeNanos()}"
        compose.setContent {
            Text(privateText, Modifier.testTag("privacy_probe").semantics {
                contentDescription = privateDescription
            })
        }

        assertThrows(ComposeTimeoutException::class.java) {
            compose.awaitCondition(conditionName, timeoutMs = 50L) { false }
        }

        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("logcat -d -s METIS_WAIT:E")
        val logs = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText() }
        val entries = logs.lineSequence().filter { it.contains("condition=$conditionName") }.toList()
        assertTrue(entries.any { it.contains("elapsedMs=") })
        assertTrue(entries.any { it.contains("tree=merged begin") })
        assertTrue(entries.any { it.contains("tree=unmerged begin") })
        assertTrue(entries.any { it.contains("tag=privacy_probe") })
        assertTrue(entries.any { it.contains("textLength=${privateText.length}") })
        assertTrue(entries.any { it.contains("contentDescriptionLength=${privateDescription.length}") })
        assertFalse(entries.any { it.contains(privateText) || it.contains(privateDescription) })
    }
}
