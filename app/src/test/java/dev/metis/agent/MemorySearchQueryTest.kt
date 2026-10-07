package dev.metis.agent

import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.domain.storage.MemorySearchQuery
import dev.metis.agent.domain.storage.MemorySearchText
import dev.metis.agent.domain.storage.SavedMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MemorySearchQueryTest {
    @Test
    fun `search text is converted into literal Unicode words`() {
        assertEquals("\"café\" \"DSP\"", MemorySearchText.expression("café, DSP!"))
        assertEquals("\"alpha\" \"OR\" \"beta\"", MemorySearchText.expression("alpha OR beta"))
        assertEquals("\"DROP\" \"TABLE\"", MemorySearchText.expression("\"'); DROP TABLE --"))
        assertNull(MemorySearchText.expression("*\";--"))
    }

    @Test
    fun `search limits and record links are validated`() {
        assertThrows(IllegalArgumentException::class.java) { MemorySearchQuery(" ") }
        assertThrows(IllegalArgumentException::class.java) { MemorySearchQuery("a".repeat(201)) }
        assertThrows(IllegalArgumentException::class.java) { MemorySearchQuery("DSP", limit = 101) }
        assertThrows(IllegalArgumentException::class.java) { MemorySearchQuery("DSP", candidateLimit = 201) }
        assertThrows(IllegalArgumentException::class.java) {
            MemorySearchQuery("DSP", entityType = MemoryEntityType.TASK)
        }
        assertThrows(IllegalArgumentException::class.java) { SavedMemory("Fact", entityType = MemoryEntityType.TASK) }
    }
}
