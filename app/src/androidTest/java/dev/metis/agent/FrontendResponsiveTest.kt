package dev.metis.agent

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.MetisAppShell
import dev.metis.agent.presentation.navigation.RecordsUiState
import dev.metis.agent.presentation.navigation.RequestUiState
import dev.metis.agent.presentation.navigation.ShellUiState
import org.junit.Rule
import org.junit.Test

class FrontendResponsiveTest {
    @get:Rule val composeRule = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test fun sidebarStartsAt840AndDisappearsBelowIt() {
        val width = mutableStateOf(839f)
        composeRule.setContent {
            val pixels = LocalContext.current.resources.displayMetrics.widthPixels
            val request = remember { RequestUiState() }
            CompositionLocalProvider(LocalDensity provides Density(pixels / width.value)) {
                AgentTheme {
                    MetisAppShell(ShellUiState(), request, {}, {}, {}, {}, {}, {}, {}, {}, {},
                        RecordsUiState(loading = false), {}, {}, {}, {}, {})
                }
            }
        }
        composeRule.onNodeWithTag("navigation_sidebar").assertDoesNotExist()
        composeRule.runOnIdle { width.value = 840f }
        composeRule.onNodeWithTag("navigation_sidebar").assertExists()
        composeRule.runOnIdle { width.value = 839f }
        composeRule.onNodeWithTag("navigation_sidebar").assertDoesNotExist()
    }
}
