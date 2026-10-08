package dev.metis.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.metis.agent.data.storage.FieldCipher
import dev.metis.agent.data.storage.KeystoreFieldCipher
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase
import dev.metis.agent.data.storage.PersonalMigrations
import dev.metis.agent.data.storage.RecordConstraints
import java.security.KeyStore
import java.util.UUID

internal class StorageTestFixture : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    val name = "storage-slice-test-${UUID.randomUUID()}.db"
    val alias = "metis.test.${UUID.randomUUID()}"
    val cipher = CountingCipher(KeystoreFieldCipher(alias))
    val database = Room.databaseBuilder(context, PersonalDatabase::class.java, name)
        .addMigrations(PersonalMigrations.FROM_1_TO_2, PersonalMigrations.FROM_2_TO_3, PersonalMigrations.FROM_3_TO_4)
        .addCallback(RecordConstraints).build()
    val repository = LocalPersonalRepository(database, cipher)

    override fun close() {
        database.close()
        context.deleteDatabase(name)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}

internal class CountingCipher(private val delegate: FieldCipher) : FieldCipher {
    var decryptions = 0
    override fun encrypt(value: String, binding: String) = delegate.encrypt(value, binding)
    override fun decrypt(value: ByteArray, binding: String): String {
        decryptions++
        return delegate.decrypt(value, binding)
    }
}
