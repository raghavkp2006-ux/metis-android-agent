package dev.metis.agent.presentation.navigation

import android.net.Uri
import android.database.sqlite.SQLiteException
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
import java.security.GeneralSecurityException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExportDialogHost(
    state: BackupWorkspaceState,
    manager: DisciplineBackupManager,
    picker: DisciplineBackupPicker,
    exportCallback: (Uri?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (state.showExportPassword) ExportPasswordDialog(
        state.exportPassword, state.exportRepeat, state.busy,
        onPasswordChange = { state.exportPassword = it },
        onRepeatChange = { state.exportRepeat = it },
        onDismiss = { state.showExportPassword = false; state.exportPassword = ""; state.exportRepeat = "" },
        onConfirm = {
            val password = state.exportPassword.toCharArray()
            state.exportPassword = ""; state.exportRepeat = ""; state.showExportPassword = false
            state.busy = true; state.status = "Preparing encrypted export…"
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        manager.createExport(password, BuildConfig.VERSION_NAME, System.currentTimeMillis())
                    }
                    state.pendingExport = bytes
                    state.status = "Choose where to save the encrypted export."
                    picker.createDocument("metis-discipline.metis", exportCallback)
                } catch (failure: IOException) {
                    state.status = failure.exportFailureStatus()
                } catch (failure: IllegalArgumentException) {
                    state.status = failure.exportFailureStatus()
                } catch (failure: IllegalStateException) {
                    state.status = failure.exportFailureStatus()
                } catch (failure: GeneralSecurityException) {
                    state.status = failure.exportFailureStatus()
                } catch (failure: SQLiteException) {
                    state.status = failure.exportFailureStatus()
                } catch (failure: SecurityException) {
                    state.status = failure.exportFailureStatus()
                } finally {
                    password.fill('\u0000')
                    state.busy = false
                }
            }
        },
    )
}

@Composable
internal fun ImportDialogHost(
    state: BackupWorkspaceState,
    manager: DisciplineBackupManager,
    documents: DisciplineBackupDocumentAccess,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (state.showImportPassword) ImportPasswordDialog(
        state.importPassword, state.busy,
        onPasswordChange = { state.importPassword = it },
        onDismiss = { state.showImportPassword = false; state.importPassword = ""; state.selectedUri = null },
        onConfirm = {
            val password = state.importPassword.toCharArray()
            val uri = state.selectedUri
            state.importPassword = ""; state.showImportPassword = false
            state.busy = true; state.status = "Opening and validating export…"
            scope.launch {
                try {
                    val import = withContext(Dispatchers.IO) {
                        val file = requireNotNull(uri)
                        documents.open(file)?.use { manager.stageImport(it, password) }
                            ?: throw IOException("File is unavailable")
                    }
                    state.staged = import
                    state.status = "Export validated. Review the preview before importing."
                } catch (failure: IOException) {
                    state.staged = null
                    state.status = failure.importFailureStatus()
                } catch (failure: SecurityException) {
                    state.staged = null
                    state.status = failure.importFailureStatus()
                } catch (failure: IllegalArgumentException) {
                    state.staged = null
                    state.status = failure.importFailureStatus()
                } catch (failure: GeneralSecurityException) {
                    state.staged = null
                    state.status = failure.importFailureStatus()
                } finally {
                    password.fill('\u0000')
                    state.selectedUri = null
                    state.busy = false
                }
            }
        },
    )
}

@Composable
internal fun PreviewDialogHost(
    state: BackupWorkspaceState,
    manager: DisciplineBackupManager,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    state.staged?.let { import ->
        ImportPreviewDialog(import, state.busy, onDismiss = { state.staged = null }, onConfirm = {
            state.busy = true; state.staged = null; state.status = "Importing…"
            scope.launch {
                try {
                    val counts = withContext(Dispatchers.IO) { manager.applyImport(import) }
                    state.status = counts.asStatus()
                } catch (failure: IOException) {
                    state.status = failure.applyFailureStatus()
                } catch (failure: IllegalStateException) {
                    state.status = failure.applyFailureStatus()
                } catch (failure: IllegalArgumentException) {
                    state.status = failure.applyFailureStatus()
                } catch (failure: GeneralSecurityException) {
                    state.status = failure.applyFailureStatus()
                } catch (failure: SQLiteException) {
                    state.status = failure.applyFailureStatus()
                } catch (failure: SecurityException) {
                    state.status = failure.applyFailureStatus()
                } finally {
                    state.busy = false
                }
            }
        })
    }
}

