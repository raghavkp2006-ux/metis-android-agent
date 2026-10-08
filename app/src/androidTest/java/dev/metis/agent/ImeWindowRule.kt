package dev.metis.agent

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Observe the actual keyboard window, including keyboards attached to a modal dialog. */
class ImeWindowRule : ExternalResource() {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var originalFlags = 0

    override fun before() {
        val info = automation.serviceInfo
        originalFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
    }

    override fun after() {
        val info = automation.serviceInfo
        info.flags = originalFlags
        automation.serviceInfo = info
    }

    fun isVisible(): Boolean {
        val windows = automation.windows
        // An unavailable window snapshot must not silently count as a hidden keyboard.
        check(windows.isNotEmpty()) { "Interactive window snapshot unavailable" }
        return windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }
}
