package dev.metis.agent

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.NavigationShell
import dev.metis.agent.presentation.navigation.ShellDestination
import dev.metis.agent.presentation.navigation.ShellUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFontNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test
    fun darkLargeTextKeepsAllDestinationsAndComposerReachable() {
        val state = mutableStateOf(ShellUiState())
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AgentTheme(darkTheme = true) {
                    NavigationShell(state.value, { state.value = state.value.copy(destination = it) }, {}, {}, {})
                }
            }
        }
        ShellDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.name}").performScrollTo()
                .assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
            composeRule.onNodeWithTag("screen_title").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Write a request").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
    }
}
