@file:Suppress("MagicNumber") // Welcome artwork placement and scrim opacity.

package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metis.agent.R
import dev.metis.agent.presentation.designsystem.MetisOrb
import dev.metis.agent.presentation.designsystem.PrimaryButton

@Composable
internal fun WelcomeScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().testTag("welcome_screen")) {
        Image(painterResource(R.drawable.metis_welcome_landscape), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.background))))
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(32.dp))
            Surface(shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MetisOrb(diameter = 88.dp)
                    Text("METIS", style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 8.sp))
                    Text("Plan. Remember. Achieve.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(48.dp))
            Surface(shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Your personal agent,\nrunning locally.", style = MaterialTheme.typography.headlineMedium)
                    Text("Your information stays on this device unless a feature clearly tells you otherwise.",
                        style = MaterialTheme.typography.bodyLarge)
                    Text("Plan your day. Remember what matters. Take the next step.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton("Get started", onStart, Modifier.fillMaxWidth().testTag("welcome_start"))
                }
            }
            Text("A calmer mind. A more intentional tomorrow.", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
