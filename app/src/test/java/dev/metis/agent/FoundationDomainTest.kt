package dev.metis.agent

import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedExperiment
import dev.metis.agent.domain.storage.SavedFocusSession
import dev.metis.agent.domain.storage.SavedReminder
import dev.metis.agent.domain.storage.SavedUserProfile
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Test

class FoundationDomainTest {
    @Test
    fun successAndHandoffNeedMatchingVerificationAndFinishedOutcome() {
        val request = UUID.randomUUID().toString()
        val proposal = UUID.randomUUID().toString()
        val token = UUID.randomUUID().toString()
        assertThrows(IllegalArgumentException::class.java) {
            SavedActionRun(request, proposal, token, "TASK", "{}", "LOW", "SUCCEEDED", 10, "UNVERIFIED")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavedActionRun(request, proposal, token, "TASK", "{}", "LOW", "HANDED_OFF", 10, "VERIFIED_LOCAL",
                finishedAt = 20, receipt = "{}")
        }
    }

    @Test
    fun timingAndExplicitConsentCannotBeInvented() {
        assertThrows(IllegalArgumentException::class.java) {
            SavedFocusSession(10, 60, "COMPLETED")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavedExperiment("Synthetic experiment", "Synthetic hypothesis", 10, 20, "{}", "ACTIVE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavedUserProfile("Synthetic name", "UTC", "en-IN", 0, true, true)
        }
    }

    @Test
    fun storedReminderTimeMustAgreeWithItsZoneAndLocalTime() {
        assertThrows(IllegalArgumentException::class.java) {
            SavedReminder("Synthetic reminder", 10, "2026-10-08T10:00", "UTC", "APPROXIMATE", "PENDING",
                UUID.randomUUID().toString())
        }
    }
}
