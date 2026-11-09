@file:Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber", "TooGenericExceptionCaught")

package dev.metis.agent.presentation.navigation

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.metis.agent.PersonalStorage
import dev.metis.agent.data.storage.TimetableImporter
import dev.metis.agent.data.storage.TimetableRepository
import dev.metis.agent.data.storage.TimetableValidationResult
import dev.metis.agent.presentation.designsystem.MetisCard
import dev.metis.agent.presentation.designsystem.MetisSectionHeader
import dev.metis.agent.presentation.designsystem.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

internal interface TimetableDocumentPicker {
    fun openDocument(onResult: (Uri?) -> Unit)
}

internal interface TimetableDocumentAccess {
    fun open(uri: Uri): InputStream?
}

@Composable
internal fun TimetableWorkspace(
    modifier: Modifier = Modifier,
    repositoryOverride: TimetableRepository? = null,
    pickerOverride: TimetableDocumentPicker? = null,
    documentAccessOverride: TimetableDocumentAccess? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = repositoryOverride ?: remember {
        PersonalStorage.repository(context.applicationContext).timetable
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingImportResult by remember { mutableStateOf<TimetableValidationResult?>(null) }
    var blockCount by remember { mutableStateOf(0) }
    var ruleCount by remember { mutableStateOf(0) }
    var canUndo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        blockCount = repository.getBlocks().size
        ruleCount = repository.getRoutineRules().size
        canUndo = repository.canUndo()
    }

    var pickerCallback by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pickerCallback?.invoke(uri)
    }

    val systemPicker = remember(openDocLauncher) {
        object : TimetableDocumentPicker {
            override fun openDocument(onResult: (Uri?) -> Unit) {
                pickerCallback = onResult
                openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }
        }
    }
    val picker = pickerOverride ?: systemPicker

    val docAccess = documentAccessOverride ?: remember(context) {
        object : TimetableDocumentAccess {
            override fun open(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)
        }
    }

    val handleFilePicked: (Uri?) -> Unit = { uri ->
        if (uri == null) {
            statusMessage = "Import cancelled."
        } else {
            scope.launch {
                val jsonString = withContext(Dispatchers.IO) {
                    try {
                        docAccess.open(uri)?.bufferedReader()?.use { it.readText() }
                    } catch (e: Exception) {
                        Log.w("TimetableWorkspace", "Failed to open SAF URI: $uri", e)
                        null
                    }
                }
                if (jsonString == null) {
                    statusMessage = "Failed to read timetable file."
                } else {
                    val result = TimetableImporter.parseAndValidate(jsonString)
                    pendingImportResult = result
                }
            }
        }
    }

    MetisCard(modifier = modifier) {
        MetisSectionHeader("Timetable & Schedule")
        if (blockCount > 0 || ruleCount > 0) {
            Text("Active schedule: $blockCount blocks, $ruleCount routine rules.")
        } else {
            Text("No timetable imported. Import a JSON schedule to answer class and free-time questions.")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SecondaryButton(
                label = "Import schedule",
                onClick = {
                    statusMessage = null
                    picker.openDocument(handleFilePicked)
                },
                modifier = Modifier.testTag("import_timetable_button"),
            )
            if (canUndo) {
                SecondaryButton(
                    label = "Undo import",
                    onClick = {
                        scope.launch {
                            val undone = repository.undoReplace()
                            if (undone) {
                                blockCount = repository.getBlocks().size
                                ruleCount = repository.getRoutineRules().size
                                canUndo = repository.canUndo()
                                statusMessage = "Schedule import undone."
                            } else {
                                statusMessage = "Undo failed: no backup available."
                            }
                        }
                    },
                    modifier = Modifier.testTag("undo_timetable_button"),
                )
            }
        }

        statusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("timetable_status_message"),
            )
        }
    }

    // Preview / Validation Dialog
    pendingImportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { pendingImportResult = null },
            title = {
                Text(if (result.valid) "Confirm Schedule Import" else "Validation Errors")
            },
            text = {
                Column {
                    if (!result.valid) {
                        Text(
                            "The schedule file contains errors and cannot be imported:",
                            color = MaterialTheme.colorScheme.error,
                        )
                        result.errors.forEach { err ->
                            Text("• $err", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text("Schedule summary:")
                        Text("Total blocks: ${result.totalBlocksCount}")
                        result.blocksCountByDay.forEach { (day, count) ->
                            if (count > 0) {
                                Text("• ${TimetableImporter.weekdayName(day)}: $count blocks")
                            }
                        }
                        Text("Routine rules: ${result.totalRulesCount}")
                        Text(
                            "\nImporting will atomically replace any previous timetable. You can undo this later.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                if (result.valid) {
                    TextButton(
                        onClick = {
                            val blocks = result.blocks
                            val rules = result.rules
                            pendingImportResult = null
                            scope.launch {
                                repository.replaceSchedule(blocks, rules)
                                blockCount = repository.getBlocks().size
                                ruleCount = repository.getRoutineRules().size
                                canUndo = repository.canUndo()
                                statusMessage = "Imported ${blocks.size} blocks and ${rules.size} routine rules."
                            }
                        },
                        modifier = Modifier.testTag("confirm_import_button"),
                    ) {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingImportResult = null },
                    modifier = Modifier.testTag("cancel_import_button"),
                ) {
                    Text(if (result.valid) "Cancel" else "Dismiss")
                }
            },
        )
    }
}
