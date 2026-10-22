package dev.metis.agent.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dev.metis.agent.domain.contacts.ContactCandidate
import dev.metis.agent.domain.contacts.ContactMatcher
import dev.metis.agent.domain.contacts.ContactResolution
import dev.metis.agent.domain.contacts.ContactsResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The provider is read only, queried only after the user starts Find contact. */
class AndroidContactsResolver(private val context: Context) : ContactsResolver {
    override suspend fun resolve(query: String): ContactResolution = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext ContactResolution.PermissionNeeded
        try {
            val contacts = readNames()
            when (val result = ContactMatcher.resolve(query, contacts)) {
                is ContactResolution.Resolved -> ContactResolution.Resolved(withMaskedPhone(result.contact))
                is ContactResolution.Ambiguous -> ContactResolution.Ambiguous(
                    result.candidates.map(::withMaskedPhone), result.requiresRefinement)
                else -> result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            // A grant can be revoked while the query is running.
            ContactResolution.PermissionNeeded
        } catch (_: RuntimeException) {
            // Provider failures never include personal fields in UI or diagnostics.
            ContactResolution.NotFound
        }
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(context,
        Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun readNames(): List<ContactCandidate> {
        val projection = arrayOf(ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val cursor = context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
            projection, null, null, null) ?: return emptyList()
        return cursor.use {
            val idIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            buildList {
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)?.trim().orEmpty()
                    if (name.isNotBlank()) add(ContactCandidate(it.getString(idIndex), name))
                }
            }
        }
    }

    private fun withMaskedPhone(candidate: ContactCandidate): ContactCandidate {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val cursor = context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, arrayOf(candidate.id), null) ?: return candidate
        val masked = cursor.use {
            val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (it.moveToFirst()) mask(it.getString(numberIndex).orEmpty()) else null
        }
        return ContactCandidate(candidate.id, candidate.displayName, masked)
    }

    private fun mask(number: String): String? {
        val digits = number.filter(Char::isDigit)
        return digits.takeIf { it.length >= MASK_DIGITS }?.takeLast(MASK_DIGITS)?.let { "••$it" }
    }

    private companion object { const val MASK_DIGITS = 2 }
}
