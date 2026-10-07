package dev.metis.agent

import android.content.Context
import dev.metis.agent.data.storage.KeystoreFieldCipher
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.data.storage.PersonalDatabase

/** App-scoped connection. No seed, fallback deletion, plaintext mirror, or exported inspection. */
object PersonalStorage {
    @Volatile private var repository: LocalPersonalRepository? = null

    fun repository(context: Context): LocalPersonalRepository = repository ?: synchronized(this) {
        repository ?: LocalPersonalRepository(PersonalDatabase.open(context), KeystoreFieldCipher())
            .also { repository = it }
    }
}
