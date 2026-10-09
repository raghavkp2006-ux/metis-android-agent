package dev.metis.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metis.agent.data.storage.LocalPersonalRepository
import dev.metis.agent.domain.storage.SavedDerivedInsight
import dev.metis.agent.domain.storage.SavedHabit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BehavioralConsentStorageTest {
    private val fixture = StorageTestFixture()
    @After fun cleanup() = fixture.close()

    @Test fun habitsAndInsightsRequireCurrentConsentAndRemainDeletableAfterWithdrawal() = runBlocking {
        var consent = false
        val repository = LocalPersonalRepository(fixture.database, fixture.cipher,
            behavioralConsent = { consent })
        val habit = SavedHabit(
            name = "Repeated focus",
            definition = """{"weekday":3,"event_type":"FOCUS_COMPLETED"}""",
            sampleCount = 3,
            confidence = 0.75f,
            observationStart = NOW - 14 * DAY,
            observationEnd = NOW,
            methodVersion = "EVENT_WEEKDAY_FREQUENCY_V1",
            consentRequired = true,
        )
        val insight = SavedDerivedInsight(
            kind = "WEEKLY",
            computedAt = NOW,
            observationStart = NOW - 7 * DAY,
            observationEnd = NOW,
            sampleCount = 3,
            methodVersion = "EVENT_WEEKDAY_FREQUENCY_V1",
            confidence = 0.75f,
            statisticsJson = """{"patterns":[]}""",
            sourceWatermark = NOW,
        )
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.foundation.habit.save(habit) } }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.foundation.derivedInsight.save(insight) }
        }

        consent = true
        repository.foundation.habit.save(habit)
        repository.foundation.derivedInsight.save(insight)
        assertEquals(1, repository.foundation.habit.observe().first().size)
        assertEquals(1, repository.foundation.derivedInsight.observe().first().size)

        consent = false
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.foundation.derivedInsight.save(insight.copy(computedAt = NOW + 1,
                observationStart = NOW - 7 * DAY, observationEnd = NOW + 1,
                metadata = dev.metis.agent.domain.storage.RecordMetadata(createdAt = NOW + 1))) }
        }
        repository.foundation.derivedInsight.delete(insight.metadata.id, insight.metadata.revision)
        repository.foundation.habit.delete(habit.metadata.id, habit.metadata.revision)
        assertEquals(0, repository.foundation.habit.observe().first().size)
        assertEquals(0, repository.foundation.derivedInsight.observe().first().size)
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 1_800_000_000_000L
    }
}
