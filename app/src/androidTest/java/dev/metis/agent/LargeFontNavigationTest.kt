package dev.metis.agent

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.MetisAppShell
import dev.metis.agent.presentation.navigation.ShellUiState
import dev.metis.agent.presentation.navigation.RequestUiState
import dev.metis.agent.presentation.navigation.RecordsUiState
import dev.metis.agent.presentation.navigation.Workspace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFontNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test fun darkDoubleSizeTextKeepsDrawerAndComposerReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            val state = remember { mutableStateOf(ShellUiState()) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AgentTheme(darkTheme = true) {
                    MetisAppShell(state.value, RequestUiState(),
                        { state.value = state.value.copy(workspace = it) }, {}, {}, {}, {}, {}, {}, {}, {},
                        RecordsUiState(loading = false), {}, {}, {}, {}, {})
                }
            }
        }
        Workspace.entries.forEach { workspace ->
            composeRule.onNodeWithTag("open_navigation").performClick()
            composeRule.onNodeWithTag("workspace_${workspace.name}").performScrollTo()
                .assertHeightIsAtLeast(48.dp).performClick()
            composeRule.onNodeWithTag("composer_input").assertIsDisplayed()
            composeRule.onNodeWithTag("send_request").assertHeightIsAtLeast(48.dp)
        }
    }
}
