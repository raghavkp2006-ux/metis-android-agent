@file:Suppress("MagicNumber") // Visual dimensions and animation timing.

package dev.metis.agent.presentation.designsystem

import androidx.compose.ui.graphics.luminance

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class MetisSemanticColors(
    val success: Color, val warning: Color, val orbCore: Color, val orbGlow: Color,
)

object MetisTokens {
    val colors: MetisSemanticColors
        @Composable get() = if (MaterialTheme.colorScheme.background.luminanceIsDark()) {
            MetisSemanticColors(Color(0xFF82DEB4), Color(0xFFFFD18C), Color(0xFFB6D8FF), Color(0xFF4E8EFF))
        } else {
            MetisSemanticColors(Color(0xFF16734D), Color(0xFF875100), Color(0xFF285BDA), Color(0xFF79ADFF))
        }
}

private fun Color.luminanceIsDark() = luminance() < 0.5f

@Composable
fun MetisCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun MetisSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

/** Decorative: the adjacent text announces assistant state once, rather than once per frame. */
@Composable
fun MetisOrb(modifier: Modifier = Modifier, diameter: Dp = 72.dp, active: Boolean = false) {
    val tokens = MetisTokens.colors
    val pulse = remember { Animatable(0.65f) }
    val resolver = LocalContext.current.contentResolver
    val animate = remember(resolver, active) {
        active && Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    LaunchedEffect(animate) {
        if (animate) {
            while (true) {
                pulse.animateTo(1f, tween(1600))
                pulse.animateTo(0.65f, tween(1600))
            }
        } else pulse.snapTo(0.65f)
    }
    Canvas(modifier.size(diameter)) {
        val radius = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(tokens.orbGlow.copy(alpha = 0.3f * pulse.value),
            Color.Transparent), center, radius))
        drawCircle(tokens.orbGlow.copy(alpha = 0.25f), radius * 0.66f, style = Stroke(1.dp.toPx()))
        drawArc(tokens.orbCore, -65f, 285f, false,
            topLeft = Offset(radius * 0.58f, radius * 0.58f),
            size = androidx.compose.ui.geometry.Size(radius * 0.84f, radius * 0.84f),
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawArc(tokens.orbCore.copy(alpha = 0.65f), 110f, 90f, false,
            topLeft = Offset(radius * 0.44f, radius * 0.44f),
            size = androidx.compose.ui.geometry.Size(radius * 1.12f, radius * 1.12f),
            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
