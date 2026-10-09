package dev.metis.agent

import dev.metis.agent.domain.insights.BehavioralReflectionEngine
import dev.metis.agent.domain.insights.ReflectionPeriod
import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BehavioralReflectionTest {
    @Test fun consentIsRequiredAndOnlySupportedOutcomeTypesAreAnalyzed() {
        val events = listOf(event("TASK_COMPLETED", at("2026-10-10T09:00:00Z"), "private payload"),
            event("TASK_CREATED", at("2026-10-10T10:00:00Z"), "must not be read"))

        assertNull(BehavioralReflectionEngine.reflect(events, ReflectionPeriod.DAILY, NOW, ZoneOffset.UTC, false))
        val result = requireNotNull(BehavioralReflectionEngine.reflect(
            events, ReflectionPeriod.DAILY, NOW, ZoneOffset.UTC, true,
        ))
        assertEquals(1, result.completedTasks)
        assertEquals(0, result.postponedTasks)
        assertEquals(0, result.completedFocusSessions)
        assertTrue(result.repeatedPatterns.isEmpty())
    }

    @Test fun dailyAndWeeklyWindowsUseTheRequestedZoneAndExcludeFutureEvents() {
        val zone = ZoneId.of("Asia/Kolkata")
        val events = listOf(
            event("TASK_COMPLETED", at("2026-10-10T00:00:00Z")), // 05:30 today locally
            event("TASK_POSTPONED", at("2026-10-04T18:29:00Z")), // 23:59 Sunday locally
            event("FOCUS_COMPLETED", at("2026-10-04T18:30:00Z")), // Monday 00:00 locally
            event("TASK_POSTPONED", at("2026-10-04T18:31:00Z")), // Monday 00:01 locally
            event("TASK_COMPLETED", at("2026-10-10T12:01:00Z")), // future relative to NOW
        )

        val daily = requireNotNull(BehavioralReflectionEngine.reflect(
            events, ReflectionPeriod.DAILY, NOW, zone, true,
        ))
        assertEquals(1, daily.completedTasks)
        assertEquals(0, daily.completedFocusSessions)
        val weekly = requireNotNull(BehavioralReflectionEngine.reflect(
            events, ReflectionPeriod.WEEKLY, NOW, zone, true,
        ))
        assertEquals(1, weekly.postponedTasks)
        assertEquals(1, weekly.completedFocusSessions)
        assertEquals(at("2026-10-04T18:30:00Z"), weekly.startAt)
    }

    @Test fun repeatedPatternRequiresThreeSamplesAcrossThreeDistinctWeekdays() {
        val wednesdays = listOf("2026-09-23T09:00:00Z", "2026-09-30T09:00:00Z", "2026-10-07T09:00:00Z")
            .map { event("TASK_COMPLETED", at(it)) }
        val tooFewSamples = requireNotNull(BehavioralReflectionEngine.reflect(
            wednesdays.take(2), ReflectionPeriod.WEEKLY, NOW, ZoneOffset.UTC, true,
        ))
        assertTrue(tooFewSamples.repeatedPatterns.isEmpty())

        val result = requireNotNull(BehavioralReflectionEngine.reflect(
            wednesdays, ReflectionPeriod.WEEKLY, NOW, ZoneOffset.UTC, true,
        ))
        val pattern = result.repeatedPatterns.single()
        assertEquals("TASK_COMPLETED", pattern.eventType)
        assertEquals(3, pattern.sampleCount)
        assertEquals(3, pattern.distinctDays)
        assertEquals(3, pattern.weekday)
        assertEquals(at("2026-09-13T00:00:00Z"), result.evidenceStartAt)
        assertEquals(NOW, result.evidenceEndAt)
        assertEquals(at("2026-10-07T09:00:00Z"), result.sourceWatermark)
    }

    @Test fun duplicateEventsOnOneDateDoNotCountAsRepeatedAcrossDays() {
        val sameDate = listOf("09:00", "10:00", "11:00").map { time ->
            event("FOCUS_COMPLETED", at("2026-10-07T${time}:00Z"))
        }
        val result = requireNotNull(BehavioralReflectionEngine.reflect(
            sameDate, ReflectionPeriod.WEEKLY, NOW, ZoneOffset.UTC, true,
        ))
        assertFalse(result.repeatedPatterns.isNotEmpty())
    }

    @Test fun eventsOutsideRollingEvidenceWindowCannotEstablishPattern() {
        val evidence = listOf("2026-08-26T09:00:00Z", "2026-09-30T09:00:00Z", "2026-10-07T09:00:00Z")
            .map { event("TASK_POSTPONED", at(it)) }
        val result = requireNotNull(BehavioralReflectionEngine.reflect(
            evidence, ReflectionPeriod.WEEKLY, NOW, ZoneOffset.UTC, true,
        ))
        assertTrue(result.repeatedPatterns.isEmpty())
    }

    private fun event(type: String, timestamp: Long, payload: String? = null) = SavedEvent(
        type = type, source = "USER", timestamp = timestamp, importance = 0.5f, schemaVersion = 1,
        payloadMetadata = payload,
        metadata = RecordMetadata(createdAt = timestamp),
    )

    private fun at(value: String) = Instant.parse(value).toEpochMilli()

    private companion object { val NOW = Instant.parse("2026-10-10T12:00:00Z").toEpochMilli() }
}
