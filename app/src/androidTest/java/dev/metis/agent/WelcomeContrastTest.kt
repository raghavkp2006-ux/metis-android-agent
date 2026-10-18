package dev.metis.agent

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import dev.metis.agent.presentation.designsystem.AgentDarkColors
import dev.metis.agent.presentation.designsystem.AgentLightColors
import dev.metis.agent.presentation.designsystem.AgentTheme
import dev.metis.agent.presentation.navigation.WelcomeScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WelcomeContrastTest {
    @get:Rule val composeRule = createAndroidComposeRule<DesignSystemTestActivity>()

    @Test fun translucentWelcomeCardsKeepReadableTextInBothThemes() {
        val dark = mutableStateOf(true)
        composeRule.setContent { AgentTheme(darkTheme = dark.value) { WelcomeScreen {} } }
        listOf(true, false).forEach { darkTheme ->
            composeRule.runOnIdle { dark.value = darkTheme }
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText("METIS").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            val text = layouts.single().layoutInput.style.color.luminance()
            val surface = (if (darkTheme) AgentDarkColors else AgentLightColors).surface.luminance()
            val contrast = (maxOf(text, surface) + 0.05f) / (minOf(text, surface) + 0.05f)
            assertTrue("Welcome text must meet AA contrast in each theme", contrast >= 4.5f)
        }
    }
}
