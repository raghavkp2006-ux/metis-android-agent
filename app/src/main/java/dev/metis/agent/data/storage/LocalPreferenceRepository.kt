package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.storage.PreferenceRepository
import dev.metis.agent.domain.storage.RevisionConflictException
import dev.metis.agent.domain.storage.SavedPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalPreferenceRepository(
    private val database: PersonalDatabase,
    cipher: FieldCipher,
    private val now: () -> Long = System::currentTimeMillis,
) : PreferenceRepository {
    private val dao = database.preferences()
    private val codec = PreferenceCodec(cipher)
    private val recordCodec = RecordCodec(cipher)

    override fun observePreferences() = dao.observe().map { rows -> rows.map { codec.decode(it) } }
        .flowOn(Dispatchers.IO)

    override suspend fun savePreference(preference: SavedPreference) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), recordCodec)
            val old = dao.preference(preference.metadata.id)
            val metadata = nextMetadata(preference.metadata, old?.metadata, now())
            require(old == null || old.key == preference.key.name)
            val row = codec.encode(preference, metadata)
            if (old == null) dao.insert(row) else dao.update(row)
        }
    }

    override suspend fun deletePreference(id: String, revision: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), recordCodec)
            if (dao.delete(id, revision) != 1) throw RevisionConflictException()
        }
    }
}
