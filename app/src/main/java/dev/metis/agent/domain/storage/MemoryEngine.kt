package dev.metis.agent.domain.storage

/** Explicit user mutations only. No inference, command dispatch or background retention job. */
class MemoryEngine(
    private val memories: MemoryRepository,
    private val retention: MemoryRetentionRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun retrieve(query: MemorySearchQuery): MemorySearchResult = memories.searchMemories(query)

    suspend fun related(reference: MemoryReference, includeDerived: Boolean = false): MemorySearchResult =
        retrieve(MemorySearchQuery(entityType = reference.type, entityId = reference.id, at = now(),
            origin = if (includeDerived) null else MemoryOrigin.EXPLICIT))

    suspend fun working(): MemorySearchResult =
        retrieve(MemorySearchQuery(type = MemoryType.WORKING, origin = MemoryOrigin.EXPLICIT, at = now()))

    suspend fun saveExplicit(
        content: String, type: MemoryType, importance: Float, expiry: MemoryExpiryChoice,
        original: SavedMemory? = null,
    ) {
        require(original == null || original.origin == MemoryOrigin.EXPLICIT)
        val at = now()
        val expires = when (expiry) {
            MemoryExpiryChoice.KEEP -> original?.expiresAt
            MemoryExpiryChoice.FOREVER -> null
            MemoryExpiryChoice.DAY -> Math.addExact(at, DAY_MILLIS)
            MemoryExpiryChoice.WEEK -> Math.addExact(at, WEEK_MILLIS)
        }
        val workingExpiry = if (type == MemoryType.WORKING) minOf(expires ?: Long.MAX_VALUE,
            Math.addExact(at, DAY_MILLIS)) else expires
        val memory = original?.copy(content = content, type = type, importance = importance, expiresAt = workingExpiry)
            ?: SavedMemory(content, metadata = RecordMetadata(createdAt = at), type = type,
                importance = importance, expiresAt = workingExpiry)
        memories.saveMemory(memory)
    }

    suspend fun forget(memory: SavedMemory) = memories.deleteMemory(memory.metadata.id, memory.metadata.revision)
    suspend fun reviewExpired(): MemoryExpiryReview = retention.reviewExpired(now())
    suspend fun deleteExpired(review: MemoryExpiryReview): Int = retention.deleteExpired(review)

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val WEEK_MILLIS = 604_800_000L
    }
}

enum class MemoryExpiryChoice { KEEP, FOREVER, DAY, WEEK }
