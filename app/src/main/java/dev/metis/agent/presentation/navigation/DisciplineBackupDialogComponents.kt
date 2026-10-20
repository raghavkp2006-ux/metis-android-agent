package dev.metis.agent.presentation.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.metis.agent.BuildConfig
import dev.metis.agent.PersonalStorage
import dev.metis.agent.data.storage.DisciplineBackupManager
import dev.metis.agent.data.storage.StagedDisciplineImport
import dev.metis.agent.domain.storage.DisciplineImportCounts
import dev.metis.agent.presentation.designsystem.MetisCard
import dev.metis.agent.presentation.designsystem.MetisSectionHeader
import dev.metis.agent.presentation.designsystem.SecondaryButton
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExportPasswordDialog(
    password: String,
    repeat: String,
    busy: Boolean,
    onPasswordChange: (String) -> Unit,
    onRepeatChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Encrypt export") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a password of at least 12 characters and enter it twice. " +
                    "Keep it safe: it cannot be recovered.")
                PasswordField("Password", password, onPasswordChange, "export_password")
                PasswordField("Repeat password", repeat, onRepeatChange, "export_password_repeat")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && passwordHasMinimumLength(password) && password == repeat,
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ImportPasswordDialog(
    password: String,
    busy: Boolean,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Unlock export") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the password used to create this export. Data is validated before anything is changed.")
                PasswordField("Export password", password, onPasswordChange, "import_password")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && passwordHasMinimumLength(password),
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text("Validate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ImportPreviewDialog(
    import: StagedDisciplineImport,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val preview = import.preview
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Review import") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${preview.habits.size} habits · ${preview.checkins.size} check-ins · " +
                    "${preview.tickCount} completion ticks")
                Text(if (preview.minDate == null) "Record date range: none" else
                    "Record dates: ${preview.minDate} to ${preview.maxDate}")
                Text("Records with matching IDs are skipped and never overwritten. " +
                    "Check-ins on existing days and ticks with an existing check-in/habit pair are skipped; " +
                    "ticks for skipped check-ins are skipped too.")
                Text("Confirm to add the validated records in one transaction. A failure leaves saved data unchanged.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("confirm_discipline_import"),
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Cancel") }
        },
    )
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit, tag: String) {
    OutlinedTextField(
        value, onValueChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag(tag),
    )
}

internal fun DisciplineImportCounts.asStatus() =
    "Import complete. Added $habitsAdded habits, $checkinsAdded check-ins and $ticksAdded ticks; " +
        "skipped $habitsSkipped habits, $checkinsSkipped check-ins and $ticksSkipped ticks."

private fun passwordHasMinimumLength(value: String) = value.codePointCount(0, value.length) >=
    dev.metis.agent.data.storage.DisciplineBackupFormat.MIN_PASSWORD_LENGTH


