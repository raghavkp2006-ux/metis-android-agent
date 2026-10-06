package dev.metis.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import dev.metis.agent.presentation.designsystem.DesignSystemPreview
import dev.metis.agent.presentation.designsystem.ConfirmationPreview

/** Debug-only isolated test host and synthetic visual review surface. Absent from release. */
class DesignSystemTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("showcase", false)) {
            setContent {
                Box(Modifier.safeDrawingPadding()) {
                    if (intent.getBooleanExtra("confirmation", false)) ConfirmationPreview()
                    else DesignSystemPreview()
                }
            }
        }
    }
}
