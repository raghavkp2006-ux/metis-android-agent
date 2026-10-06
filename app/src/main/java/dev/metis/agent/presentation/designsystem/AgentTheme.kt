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

// Literal RGB values define the frozen palette; they are configuration, not algorithmic constants.
@Suppress("MagicNumber")
val AgentLightColors = lightColorScheme(
    primary = Color(0xFF006A60), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EEE9), onPrimaryContainer = Color(0xFF003730),
    secondary = Color(0xFF525252), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDED), onSecondaryContainer = Color(0xFF171717),
    tertiary = Color(0xFF006A60), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5EEE9), onTertiaryContainer = Color(0xFF003730),
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF171717),
    surface = Color.White, onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF525252),
    surfaceTint = Color(0xFF006A60),
    surfaceDim = Color(0xFFEDEDED), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF3F3F3), surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outline = Color(0xFF737373), outlineVariant = Color(0xFFE0E0E0),
    inverseSurface = Color(0xFF1E1E1E), inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF80D5C8),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF601410),
)

@Suppress("MagicNumber")
val AgentDarkColors = darkColorScheme(
    primary = Color(0xFF80D5C8), onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF004F47), onPrimaryContainer = Color(0xFFA8E8DD),
    secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF303030), onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFF80D5C8), onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF004F47), onTertiaryContainer = Color(0xFFA8E8DD),
    background = Color(0xFF121212), onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1E1E1E), onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF303030), onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceTint = Color(0xFF80D5C8),
    surfaceDim = Color(0xFF121212), surfaceBright = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF121212), surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1E1E), surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF303030),
    outline = Color(0xFF969696), outlineVariant = Color(0xFF383838),
    inverseSurface = Color(0xFFF5F5F5), inverseOnSurface = Color(0xFF171717),
    inversePrimary = Color(0xFF006A60),
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

// Sizes are the frozen type scale from UI_CONTRACT.md.
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
    small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AgentTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) AgentDarkColors else AgentLightColors,
        typography = AgentTypography, shapes = AgentShapes, content = content,
    )
}
