package dev.metis.agent.presentation.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// Literal RGB values define the METIS palette; they are configuration, not algorithmic constants.
@Suppress("MagicNumber")
val AgentLightColors = lightColorScheme(
    primary = Color(0xFF285BDA), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5EDFF), onPrimaryContainer = Color(0xFF112A62),
    secondary = Color(0xFF53627C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF0F8), onSecondaryContainer = Color(0xFF18243B),
    tertiary = Color(0xFF285BDA), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5EDFF), onTertiaryContainer = Color(0xFF112A62),
    background = Color(0xFFF7F9FE), onBackground = Color(0xFF18243B),
    surface = Color.White, onSurface = Color(0xFF18243B),
    surfaceVariant = Color(0xFFEAF0F8), onSurfaceVariant = Color(0xFF53627C),
    surfaceTint = Color(0xFF285BDA),
    surfaceDim = Color(0xFFEAF0F8), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F9FE),
    surfaceContainer = Color(0xFFF0F4FC), surfaceContainerHigh = Color(0xFFEAF0F8),
    surfaceContainerHighest = Color(0xFFE2E9F5),
    outline = Color(0xFF697A95), outlineVariant = Color(0xFFD6E0F0),
    inverseSurface = Color(0xFF1E1E1E), inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFFA8C5FF),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF601410),
)

@Suppress("MagicNumber")
val AgentDarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF), onPrimary = Color(0xFF09285E),
    primaryContainer = Color(0xFF17366A), onPrimaryContainer = Color(0xFFD7E4FF),
    secondary = Color(0xFFB2BFD5), onSecondary = Color(0xFF101D31),
    secondaryContainer = Color(0xFF22324B), onSecondaryContainer = Color(0xFFEEF3FF),
    tertiary = Color(0xFFAAC7FF), onTertiary = Color(0xFF09285E),
    tertiaryContainer = Color(0xFF17366A), onTertiaryContainer = Color(0xFFD7E4FF),
    background = Color(0xFF091321), onBackground = Color(0xFFEEF3FF),
    surface = Color(0xFF122035), onSurface = Color(0xFFEEF3FF),
    surfaceVariant = Color(0xFF22324B), onSurfaceVariant = Color(0xFFB2BFD5),
    surfaceTint = Color(0xFFAAC7FF),
    surfaceDim = Color(0xFF091321), surfaceBright = Color(0xFF2D3D57),
    surfaceContainerLowest = Color(0xFF091321), surfaceContainerLow = Color(0xFF0E1B2E),
    surfaceContainer = Color(0xFF122035), surfaceContainerHigh = Color(0xFF1A2A42),
    surfaceContainerHighest = Color(0xFF22324B),
    outline = Color(0xFF8395B1), outlineVariant = Color(0xFF2D3D57),
    inverseSurface = Color(0xFFEEF3FF), inverseOnSurface = Color(0xFF101D31),
    inversePrimary = Color(0xFF285BDA),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF601410), onErrorContainer = Color(0xFFF9DEDC),
)

object AgentSpacing {
    val tiny = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val screen = 16.dp
    val roomy = 20.dp
    val large = 24.dp
    val extraLarge = 32.dp
    val section = 40.dp
    val touchTarget = 48.dp
}

// Sizes are the accessible type scale from UI_CONTRACT.md.
@Suppress("MagicNumber")
private val AgentTypography = Typography(
    displaySmall = agentTextStyle(28, FontWeight.Medium),
    headlineMedium = agentTextStyle(24, FontWeight.Medium),
    titleLarge = agentTextStyle(18, FontWeight.Medium),
    bodyLarge = agentTextStyle(16),
    bodyMedium = agentTextStyle(14),
    bodySmall = agentTextStyle(12),
    labelLarge = agentTextStyle(14, FontWeight.Medium),
)

private fun agentTextStyle(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp,
    lineHeight = (size + 8).sp, fontWeight = weight,
)

private val AgentShapes = Shapes(
    small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AgentTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) AgentDarkColors else AgentLightColors,
        typography = AgentTypography, shapes = AgentShapes, content = content,
    )
}
