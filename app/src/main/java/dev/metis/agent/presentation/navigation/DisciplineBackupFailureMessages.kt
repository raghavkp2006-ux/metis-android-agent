package dev.metis.agent.presentation.navigation

import android.database.sqlite.SQLiteException
import dev.metis.agent.data.storage.DisciplineBackupFailure
import java.io.IOException
import java.security.GeneralSecurityException

internal fun Throwable.writeFailureStatus(): String = when (this) {
    is SecurityException -> "Export failed. Access to the destination was denied."
    is IllegalStateException -> "Export failed. The destination is unavailable."
    is IOException -> "Export failed. The file could not be saved."
    else -> "Export failed. The destination is unavailable."
}

internal fun Throwable.exportFailureStatus(): String = when (this) {
    is DisciplineBackupFailure.Key, is IllegalStateException -> "Export failed. The encryption key is unavailable."
    is DisciplineBackupFailure.Oversized -> "Export failed. The data exceeds the size limit."
    is GeneralSecurityException -> "Export failed. The encryption key is unavailable."
    is IllegalArgumentException -> "Export failed. The saved data could not be validated."
    is SQLiteException, is SecurityException -> "Export failed. Storage could not be read."
    is IOException -> "Export failed. Storage could not be read or written."
    else -> "Export failed. Storage could not be read or written."
}

internal fun Throwable.importFailureStatus(): String = when (this) {
    is DisciplineBackupFailure.Authentication ->
        "Import failed. The password is incorrect or the encrypted file was modified. No data was changed."
    is DisciplineBackupFailure.Corrupted -> "Import failed. The file is corrupted or invalid. No data was changed."
    is DisciplineBackupFailure.UnsupportedVersion ->
        "Import failed. This export version is not supported. No data was changed."
    is DisciplineBackupFailure.Oversized -> "Import failed. The file exceeds the size limit. No data was changed."
    is DisciplineBackupFailure.Key -> "Import failed. The encryption key is unavailable. No data was changed."
    is GeneralSecurityException -> "Import failed. The encryption key is unavailable. No data was changed."
    is SecurityException -> "Import failed. Access to the file was denied. No data was changed."
    is IllegalArgumentException -> "Import failed. The file contains invalid records. No data was changed."
    is IOException -> "Import failed. The file could not be read. No data was changed."
    else -> "Import failed. The file could not be read. No data was changed."
}

internal fun Throwable.applyFailureStatus(): String = when (this) {
    is IllegalStateException -> "Import failed. The storage key is unavailable. No data was changed."
    is GeneralSecurityException -> "Import failed. The storage key is unavailable. No data was changed."
    is IllegalArgumentException -> "Import failed. The records conflict with saved data. No data was changed."
    is SQLiteException, is SecurityException -> "Import failed. Storage could not be written. No data was changed."
    is IOException -> "Import failed. Storage could not be read or written. No data was changed."
    else -> "Import failed. Storage could not be read or written. No data was changed."
}
