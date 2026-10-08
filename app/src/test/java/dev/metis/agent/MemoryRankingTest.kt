package dev.metis.agent

import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.MemoryRanking
import dev.metis.agent.domain.storage.MemoryRankingPolicy
import dev.metis.agent.domain.storage.MemoryReference
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedMemory
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRankingTest {
    @Test
    fun normalizedWeightsAndHalfLifeProduceExplainableScores() {
        val memory = SavedMemory("Café DSP", metadata = RecordMetadata(createdAt = 0), importance = 0.8f)
        val query = MemorySearchQuery("CAFE", at = 100, ranking = MemoryRankingPolicy(
            relevance = 3.0, importance = 2.5, recency = 2.0, relationship = 1.5, confidence = 1.0,
            halfLifeMillis = 100,
        ))
        val score = MemoryRanking.score(memory, query)
        assertEquals(0.75, score.relevance, 0.00001)
        assertEquals(0.5, score.recency, 0.00001)
        assertEquals((3 * 0.75 + 2.5 * 0.8 + 2 * 0.5 + 1) / 10, score.total, 0.00001)
        assertTrue(score.total in 0.0..1.0)
    }

    @Test
    fun importanceCanOutrankRecencyAndConfigurableWeightsCanReverseThat() {
        val important = SavedMemory("Synthetic exam", metadata = RecordMetadata(createdAt = 0), importance = 1f)
        val recent = SavedMemory("Synthetic exam", metadata = RecordMetadata(createdAt = 100), importance = 0f)
        val query = MemorySearchQuery("exam", at = 100, ranking = MemoryRankingPolicy(halfLifeMillis = 100))
        assertEquals(important, MemoryRanking.rank(listOf(recent, important), query).first().first)
        val recencyOnly = query.copy(ranking = MemoryRankingPolicy(0.0, 0.0, 1.0, 0.0, 0.0, 100))
        assertEquals(recent, MemoryRanking.rank(listOf(important, recent), recencyOnly).first().first)
    }

    @Test
    fun explicitLinkedContextBoostsOnlyExactStoredReferences() {
        val person = UUID.randomUUID().toString()
        val linked = SavedMemory("Synthetic fact", entityType = MemoryEntityType.PERSON, entityId = person)
        val other = linked.copy(metadata = RecordMetadata(), entityId = UUID.randomUUID().toString())
        val query = MemorySearchQuery(at = linked.metadata.updatedAt,
            relatedEntities = setOf(MemoryReference(MemoryEntityType.PERSON, person)),
            ranking = MemoryRankingPolicy(0.0, 0.0, 0.0, 1.0, 0.0))
        assertEquals(1.0, MemoryRanking.score(linked, query).total, 0.0)
        assertEquals(0.0, MemoryRanking.score(other, query).total, 0.0)
        assertEquals(0.0, MemoryRanking.score(other, query).relevance, 0.0)
    }

    @Test
    fun deterministicTiesAndExtremeClockValuesStayFinite() {
        val first = SavedMemory("Synthetic fact", metadata =
            RecordMetadata("00000000-0000-0000-0000-000000000001", Long.MIN_VALUE))
        val second = first.copy(metadata = first.metadata.copy(id = "00000000-0000-0000-0000-000000000002"))
        val query = MemorySearchQuery("fact", at = Long.MAX_VALUE)
        assertEquals(listOf(first, second), MemoryRanking.rank(listOf(second, first), query).map { it.first })
        assertTrue(MemoryRanking.score(first, query).total.isFinite())
        assertEquals(1.0, MemoryRanking.score(first, query.copy(at = Long.MIN_VALUE)).recency, 0.0)
    }

    @Test
    fun invalidConfigurationAndUnboundedContextAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MemoryRankingPolicy(relevance = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { MemoryRankingPolicy(0.0, 0.0, 0.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { MemoryRankingPolicy(importance = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { MemoryRankingPolicy(halfLifeMillis = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            MemorySearchQuery(relatedEntities = (0..20).map {
                MemoryReference(MemoryEntityType.PERSON, UUID.randomUUID().toString())
            }.toSet())
        }
        assertEquals("", MemorySearchQuery().text)
    }
}
