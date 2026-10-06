package dev.metis.agent.presentation.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** Synthetic examples for the IDE preview only; never included in the user-facing foundation. */
@Preview(name = "Light", showBackground = true, widthDp = 360, heightDp = 800)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text", showBackground = true, widthDp = 320, fontScale = 2f)
@Preview(name = "Tablet", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
internal fun DesignSystemPreview() {
    AgentTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AgentSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AgentSpacing.large),
            ) {
                Text("Synthetic component preview", style = MaterialTheme.typography.headlineMedium)
                PrimaryButton("Primary action", {})
                PrimaryButton("Working", {}, loading = true)
                SecondaryButton("Secondary action", {})
                AgentIconButton(Icons.Default.Close, "Close preview", {})
                AgentComposer("Preview request", {}, {})
                AgentMessage("A sample response.", timestamp = "08:00")
                ActionProposal(
                    ProposalDisplay(
                        "Sample reminder", listOf("Tomorrow, 08:00 AM · Asia/Kolkata", "Approximate timing"),
                        "Local action", "Requested in the preview", listOf("Synthetic evidence"), canAccept = true,
                    ), {}, {},
                )
                ActionReceipt(ReceiptDisplay("Sample outcome", ReceiptOutcome.PENDING, "Not verified", "08:00"))
                TaskRow("Sample task", "Tomorrow · High priority", false, {})
                ScheduleBlock("Sample block", "08:00–08:30", "Proposed", conflict = "Sample conflict")
                MemoryRow("Sample fact", "Explicit preference · Synthetic source", onEdit = {}, onDelete = {})
                TimelineRow("Sample event", "Today, 08:00", "Sample outcome")
                EmptyState("Nothing here yet", "Create an item when the capability is available.")
                LoadingState("Loading sample items")
                ErrorState("Sample error", {})
                PermissionExplainer("Sample capability", "Sample access reason", "Sample data scope", true, {}, {})
            }
        }
    }
}

@Preview(name = "Expired confirmation", showBackground = true, fontScale = 2f, widthDp = 320)
@Composable
internal fun ConfirmationPreview() {
    AgentTheme {
        ConfirmationSheet(
            ProposalDisplay(
                "Sample confirmation", listOf("Target and timing details"), "Local action",
                "Requested in the preview", canAccept = false,
            ), {}, {},
        )
    }
}
