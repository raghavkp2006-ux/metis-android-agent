package dev.metis.agent

import android.graphics.Rect
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule

/** Capture the unobscured viewport before typing; window registration/insets can outlive the IME. */
internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.viewportBottom(): Int =
    runOnIdle {
        val frame = Rect()
        activity.window.decorView.getWindowVisibleDisplayFrame(frame)
        check(frame.height() > 0) { "Visible viewport unavailable" }
        frame.bottom
    }
