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

internal interface DisciplineBackupPicker {
    fun createDocument(fileName: String, onResult: (Uri?) -> Unit)
    fun openDocument(onResult: (Uri?) -> Unit)
}

internal interface DisciplineBackupDocumentAccess {
    suspend fun write(uri: Uri, bytes: ByteArray)
    fun open(uri: Uri): InputStream?
}

@Composable
internal fun DisciplineBackupWorkspace(
    modifier: Modifier = Modifier,
    managerOverride: DisciplineBackupManager? = null,
    pickerOverride: DisciplineBackupPicker? = null,
    documentAccessOverride: DisciplineBackupDocumentAccess? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { BackupWorkspaceState() }
    val manager = managerOverride ?: remember {
        DisciplineBackupManager(PersonalStorage.repository(context.applicationContext).discipline)
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        state.exportResult?.invoke(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        state.importResult?.invoke(uri)
    }
    val systemPicker = remember(exportPicker, importPicker) {
        object : DisciplineBackupPicker {
            override fun createDocument(fileName: String, onResult: (Uri?) -> Unit) {
                state.exportResult = onResult
                exportPicker.launch(fileName)
            }
            override fun openDocument(onResult: (Uri?) -> Unit) {
                state.importResult = onResult
                importPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
            }
        }
    }
    val picker = pickerOverride ?: systemPicker
    val documents = documentAccessOverride ?: remember(context) {
        object : DisciplineBackupDocumentAccess {
            override suspend fun write(uri: Uri, bytes: ByteArray) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: error("No output stream")
            }
            override fun open(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)
        }
    }
    val exportCallback: (Uri?) -> Unit = { uri ->
        val bytes = state.pendingExport
        state.pendingExport = null
        if (uri == null || bytes == null) {
            bytes?.fill(0)
            state.status = "Export cancelled. Your data was not changed."
        } else {
            state.busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { documents.write(uri, bytes) }
                    state.status = "Encrypted export saved."
                } catch (failure: IOException) {
                    state.status = failure.writeFailureStatus()
                } catch (failure: SecurityException) {
                    state.status = failure.writeFailureStatus()
                } catch (failure: IllegalStateException) {
                    state.status = failure.writeFailureStatus()
                } finally {
                    bytes.fill(0)
                    state.busy = false
                }
            }
        }
    }
    val importCallback: (Uri?) -> Unit = { uri ->
        if (uri == null) state.status = "Import cancelled. Your data was not changed."
        else { state.selectedUri = uri; state.showImportPassword = true }
    }
    SideEffect { state.exportResult = exportCallback; state.importResult = importCallback }

    MetisCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetisSectionHeader("Discipline backup")
            Text("Export and restore your user-created habits, daily check-ins, moods, notes and completion ticks.")
            Text("Exports are encrypted with a password you choose. A forgotten password cannot be recovered.",
                color = MaterialTheme.colorScheme.error)
            SecondaryButton("Export discipline data", { if (!state.busy) state.showExportPassword = true },
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("discipline_export"),
                enabled = !state.busy, loading = state.busy)
            SecondaryButton("Import discipline data", { if (!state.busy) picker.openDocument(importCallback) },
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("discipline_import"),
                enabled = !state.busy, loading = state.busy)
            if (state.busy) Text("Working…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.status.isNotEmpty()) Text(state.status, modifier = Modifier.testTag("discipline_backup_status")
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = state.status })
        }
    }

    ExportDialogHost(state, manager, picker, exportCallback, scope)
    ImportDialogHost(state, manager, documents, scope)
    PreviewDialogHost(state, manager, scope)
}

internal class BackupWorkspaceState {
    var showExportPassword by mutableStateOf(false)
    var showImportPassword by mutableStateOf(false)
    var exportPassword by mutableStateOf("")
    var exportRepeat by mutableStateOf("")
    var importPassword by mutableStateOf("")
    var selectedUri by mutableStateOf<Uri?>(null)
    var staged by mutableStateOf<StagedDisciplineImport?>(null)
    var pendingExport by mutableStateOf<ByteArray?>(null)
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var exportResult by mutableStateOf<((Uri?) -> Unit)?>(null)
    var importResult by mutableStateOf<((Uri?) -> Unit)?>(null)
}

