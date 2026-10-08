package dev.metis.agent.domain.storage

import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlin.math.pow

data class MemoryReference(val type: MemoryEntityType, val id: String) {
    init { require(UUID.fromString(id).toString() == id) }
}

data class MemoryRankingPolicy(
    val relevance: Double = 0.30,
    val importance: Double = 0.25,
    val recency: Double = 0.20,
    val relationship: Double = 0.15,
    val confidence: Double = 0.10,
    val halfLifeMillis: Long = DEFAULT_HALF_LIFE_MILLIS,
) {
    val totalWeight: Double get() = relevance + importance + recency + relationship + confidence
    init {
        require(listOf(relevance, importance, recency, relationship, confidence).all { it.isFinite() && it >= 0 })
        require(totalWeight.isFinite() && totalWeight > 0 && halfLifeMillis > 0)
    }
}

private const val DEFAULT_HALF_LIFE_MILLIS = 2_592_000_000L

/** Components are normalized inputs; total is their normalized weighted sum. No content is copied here. */
data class MemoryScore(
    val id: String,
    val total: Double,
    val relevance: Double,
    val importance: Double,
    val recency: Double,
    val relationship: Double,
    val confidence: Double,
)

object MemoryRanking {
    fun score(memory: SavedMemory, query: MemorySearchQuery): MemoryScore {
        val policy = query.ranking
        val wanted = words(query.text).toSet()
        val content = words(memory.content)
        val coverage = if (wanted.isEmpty()) 0.0 else wanted.count { it in content }.toDouble() / wanted.size
        val density = if (content.isEmpty()) 0.0 else content.count { it in wanted }.toDouble() / content.size
        val relevance = (coverage + density) / 2
        val age = (query.at.toDouble() - memory.metadata.updatedAt.toDouble()).coerceAtLeast(0.0)
        val recency = 0.5.pow(age / policy.halfLifeMillis)
        val reference = memory.entityType?.let { MemoryReference(it, requireNotNull(memory.entityId)) }
        val related = reference != null && (reference in query.relatedEntities ||
            (reference.type == query.entityType && reference.id == query.entityId))
        val relationship = if (related) 1.0 else 0.0
        val importance = memory.importance.toDouble()
        val confidence = memory.confidence.toDouble()
        val total = (policy.relevance * relevance + policy.importance * importance + policy.recency * recency +
            policy.relationship * relationship + policy.confidence * confidence) / policy.totalWeight
        return MemoryScore(memory.metadata.id, total, relevance, importance, recency, relationship, confidence)
    }

    fun rank(memories: List<SavedMemory>, query: MemorySearchQuery): List<Pair<SavedMemory, MemoryScore>> =
        memories.map { it to score(it, query) }.sortedWith(
            compareByDescending<Pair<SavedMemory, MemoryScore>> { it.second.total }
                .thenByDescending { it.first.metadata.updatedAt }.thenBy { it.first.metadata.id },
        )

    private fun words(text: String): List<String> = Regex("[\\p{L}\\p{N}]+").findAll(
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT),
    ).map { it.value }.toList()
}
