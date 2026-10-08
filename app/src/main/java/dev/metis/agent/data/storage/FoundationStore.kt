package dev.metis.agent.data.storage

import androidx.room.withTransaction
import dev.metis.agent.domain.storage.FoundationRecord
import dev.metis.agent.domain.storage.FoundationRepository
import dev.metis.agent.domain.storage.RevisionConflictException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface FoundationEntity { fun recordMetadata(): StoredMetadata }

interface FoundationAccess<E : FoundationEntity> {
    fun observe(): Flow<List<E>>
    suspend fun find(id: String): E?
    suspend fun insert(row: E)
    suspend fun update(row: E)
    suspend fun delete(id: String, revision: Long): Int
}

internal interface FoundationCodec<T : FoundationRecord, E : FoundationEntity> {
    fun encode(record: T, metadata: StoredMetadata): E
    fun decode(row: E): T
}

internal class FoundationStore<T : FoundationRecord, E : FoundationEntity>(
    private val database: PersonalDatabase,
    private val access: FoundationAccess<E>,
    private val codec: FoundationCodec<T, E>,
    private val recordCodec: RecordCodec,
    private val policy: FoundationPolicy<T> = FoundationPolicy(),
) : FoundationRepository<T> {
    override fun observe() = access.observe().map { rows -> rows.map(codec::decode) }.flowOn(Dispatchers.IO)

    override suspend fun save(record: T) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), recordCodec)
            val old = access.find(record.metadata.id)
            check(!policy.appendOnly || old == null) { "Append-only record." }
            val metadata = nextMetadata(record.metadata, old?.recordMetadata(), System.currentTimeMillis())
            policy.validate(record)
            val row = codec.encode(record, metadata)
            if (old == null) access.insert(row) else access.update(row)
        }
    }

    override suspend fun delete(id: String, revision: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            requireReadableKey(database.records(), recordCodec)
            policy.beforeDeletion(id)
            if (access.delete(id, revision) != 1) throw RevisionConflictException()
            policy.afterDeletion(id)
        }
    }
}

internal data class FoundationPolicy<T>(
    val appendOnly: Boolean = false,
    val validate: suspend (T) -> Unit = {},
    val afterDeletion: suspend (String) -> Unit = {},
    val beforeDeletion: suspend (String) -> Unit = {},
)

internal fun RecordCodec.encryptJson(value: String, table: String, id: String, field: String): ByteArray {
    FoundationJson.validate(value)
    return encrypt(value, table, id, field)
}

internal fun RecordCodec.decryptJson(value: ByteArray, table: String, id: String, field: String): String =
    decrypt(value, table, id, field).also(FoundationJson::validate)
