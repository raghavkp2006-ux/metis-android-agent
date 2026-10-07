package dev.metis.agent

import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.domain.storage.SavedMemory
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PersonalRecordValidationTest {
    @Test
    fun `invalid personal record values fail before persistence`() {
        assertThrows(IllegalArgumentException::class.java) { SavedTask(" ") }
        assertThrows(IllegalArgumentException::class.java) { SavedTask("Task", dueAt = 10) }
        assertThrows(IllegalArgumentException::class.java) { SavedTask("Task", estimatedSeconds = 0) }
        assertThrows(IllegalArgumentException::class.java) { SavedTask("Task", priority = 4) }
        assertThrows(IllegalArgumentException::class.java) { SavedTask("Task", status = TaskStatus.COMPLETED) }
        assertThrows(IllegalArgumentException::class.java) { SavedSchedule("Block", 20, 10, "UTC", "Reason") }
        assertThrows(IllegalArgumentException::class.java) { SavedMemory("Fact", confidence = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SavedMemory("Fact", importance = 2f) }
    }

    @Test
    fun `unknown deadlines remain absent and facts have explicit provenance`() {
        val task = SavedTask("Task")
        assertEquals(null, task.dueAt)
        assertEquals(null, task.dueZoneId)
        assertEquals(MemoryOrigin.EXPLICIT, SavedMemory("Fact").origin)
    }
}
