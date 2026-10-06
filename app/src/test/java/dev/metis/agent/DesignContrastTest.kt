package dev.metis.agent

import androidx.compose.ui.graphics.Color
import dev.metis.agent.presentation.designsystem.AgentDarkColors
import dev.metis.agent.presentation.designsystem.AgentLightColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignContrastTest {
    @Test
    fun `light and dark semantic text colors meet normal-text AA contrast`() {
        listOf(AgentLightColors, AgentDarkColors).forEach { colors ->
            val pairs = listOf(
                "body" to (colors.onBackground to colors.background),
                "surface" to (colors.onSurface to colors.surface),
                "secondary text" to (colors.onSurfaceVariant to colors.background),
                "variant text" to (colors.onSurfaceVariant to colors.surfaceVariant),
                "primary button" to (colors.onPrimary to colors.primary),
                "secondary button" to (colors.primary to colors.surface),
                "primary container" to (colors.onPrimaryContainer to colors.primaryContainer),
                "secondary container" to (colors.onSecondaryContainer to colors.secondaryContainer),
                "error text" to (colors.error to colors.background),
                "error container" to (colors.onErrorContainer to colors.errorContainer),
                "inverse" to (colors.inverseOnSurface to colors.inverseSurface),
            )
            pairs.forEach { (role, pair) ->
                val contrast = contrast(pair.first, pair.second)
                assertTrue("$role contrast $contrast must be at least 4.5:1", contrast >= 4.5)
            }
            assertTrue("Input boundary must be at least 3:1", contrast(colors.outline, colors.background) >= 3.0)
        }
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(channel: Float): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
}
