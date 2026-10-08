package dev.metis.agent

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.FieldCipher
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.RecordConstraints
import dev.metis.agent.domain.storage.PreferenceKey
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedPreference
import dev.metis.agent.domain.storage.SavedTask
import java.security.KeyStore
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceStorageTest {
    private val fixture = StorageTestFixture()
    private val preferences = fixture.repository.preferences

    @After fun cleanup() = fixture.close()

    @Test
    fun typedPreferencesAreEncryptedAndSurviveDatabaseReopen(): Unit = runBlocking {
        val original = listOf(
            SavedPreference(PreferenceKey.DAY_START_TIME, PreferenceValue.DayStart(LocalTime.of(6, 17))),
            SavedPreference(PreferenceKey.FOCUS_BLOCK_MINUTES, PreferenceValue.FocusMinutes(37)),
            SavedPreference(PreferenceKey.WEEK_START_DAY, PreferenceValue.WeekStart(DayOfWeek.WEDNESDAY)),
        )
        original.forEach { preferences.savePreference(it) }
        assertEquals(original, preferences.observePreferences().first())
        val row = requireNotNull(fixture.database.preferences().preference(original.first().metadata.id))
        assertFalse(String(row.typedValue, Charsets.UTF_8).contains("06:17"))
        fixture.database.close()
        val diskText = String(fixture.context.getDatabasePath(fixture.name).readBytes(), Charsets.ISO_8859_1)
        assertFalse(diskText.contains("06:17"))
        assertFalse(diskText.contains("WEDNESDAY"))
        val reopened = Room.databaseBuilder(fixture.context, PersonalDatabase::class.java, fixture.name)
            .addCallback(RecordConstraints).build()
        try {
            assertEquals(original, LocalPersonalRepository(reopened, fixture.cipher).preferences.observePreferences().first())
        } finally { reopened.close() }
    }

    @Test
    fun uniquenessRevisionChecksAndImmutableKeysPreventSilentReplacement(): Unit = runBlocking {
        val original = focus()
        preferences.savePreference(original)
        assertThrows(Exception::class.java) { runBlocking { preferences.savePreference(focus()) } }
        preferences.savePreference(original.copy(value = PreferenceValue.FocusMinutes(45)))
        val updated = preferences.observePreferences().first().single()
        assertEquals(1L, updated.metadata.revision)
        assertThrows(RevisionConflictException::class.java) { runBlocking { preferences.savePreference(original) } }
        assertThrows(RevisionConflictException::class.java) { runBlocking { preferences.deletePreference(original.metadata.id, 0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { preferences.savePreference(updated.copy(
            key = PreferenceKey.WEEK_START_DAY, value = PreferenceValue.WeekStart(DayOfWeek.MONDAY),
        )) } }
        assertEquals(listOf(updated), preferences.observePreferences().first())
        preferences.deletePreference(updated.metadata.id, 1)
        assertEquals(emptyList<SavedPreference>(), preferences.observePreferences().first())
    }

    @Test
    fun lostKeyInPreferenceOnlyStorageBlocksEveryRepositoryWriteWithoutReplacement(): Unit = runBlocking {
        val original = focus()
        preferences.savePreference(original)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(fixture.alias) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { preferences.observePreferences().first() } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { preferences.savePreference(original) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { preferences.deletePreference(original.metadata.id, 0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { fixture.repository.saveTask(SavedTask("Blocked task")) } }
        assertNotNull(fixture.database.preferences().preference(original.metadata.id))
        assertNull(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(fixture.alias, null))
        assertEquals(1, fixture.database.records().recordCount())
    }

    @Test
    fun encryptionFailureRollsBackWithoutCreatingRecords(): Unit = runBlocking {
        val failedCipher = object : FieldCipher {
            override fun encrypt(value: String, binding: String): ByteArray = error("Synthetic failure")
            override fun decrypt(value: ByteArray, binding: String): String = error("Synthetic failure")
        }
        val failing = LocalPersonalRepository(fixture.database, failedCipher).preferences
        assertThrows(IllegalStateException::class.java) { runBlocking { failing.savePreference(focus()) } }
        assertEquals(0, fixture.database.records().recordCount())
    }

    @Test
    fun sqlConstraintsRejectUnknownOrAuthorityKeysKindsOriginsAndVersions(): Unit = runBlocking {
        val original = focus()
        preferences.savePreference(original)
        val db = fixture.database.openHelper.writableDatabase
        listOf(
            "UPDATE preferences SET `key` = 'AUTONOMY_LEVEL'", "UPDATE preferences SET `key` = 'BEHAVIORAL_CONSENT'",
            "UPDATE preferences SET value_kind = 'WEEKDAY'", "UPDATE preferences SET source = 'DERIVED'",
            "UPDATE preferences SET value_schema_version = 2", "UPDATE preferences SET revision = -1",
            "UPDATE preferences SET updated_at = created_at - 1",
        ).forEach { statement -> assertThrows(Exception::class.java) { db.execSQL(statement) } }
        assertEquals(listOf(original), preferences.observePreferences().first())
    }

    @Test
    fun corruptedEncryptedValueFailsWithoutPartialResults(): Unit = runBlocking {
        val original = focus()
        preferences.savePreference(original)
        preferences.savePreference(SavedPreference(
            PreferenceKey.DAY_START_TIME, PreferenceValue.DayStart(LocalTime.of(7, 0)),
        ))
        fixture.database.openHelper.writableDatabase.execSQL(
            "UPDATE preferences SET typed_value = ?", arrayOf(byteArrayOf(0)),
        )
        assertThrows(IllegalArgumentException::class.java) { runBlocking { preferences.observePreferences().first() } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { fixture.repository.saveTask(SavedTask("Blocked")) } }
        assertEquals(2, fixture.database.records().recordCount())
    }

    private fun focus() = SavedPreference(PreferenceKey.FOCUS_BLOCK_MINUTES, PreferenceValue.FocusMinutes(25))
}
