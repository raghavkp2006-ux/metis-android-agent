package dev.metis.agent

import dev.metis.agent.domain.storage.SavedTaskDependency
import dev.metis.agent.domain.storage.TaskDependencyGraph
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDependencyGraphTest {
    @Test
    fun validatesCanonicalDistinctTaskIds() {
        val id = UUID.randomUUID().toString()
        assertThrows(IllegalArgumentException::class.java) { SavedTaskDependency(id, id) }
        assertThrows(IllegalArgumentException::class.java) { SavedTaskDependency("bad", id) }
        assertThrows(IllegalArgumentException::class.java) { SavedTaskDependency(id, "1-1-1-1-1") }
    }

    @Test
    fun rejectsBackEdgesButAllowsSharedPrerequisitesAndDisconnectedGraphs() {
        val ids = List(5) { UUID.randomUUID().toString() }
        val edges = listOf(
            SavedTaskDependency(ids[0], ids[1]), SavedTaskDependency(ids[0], ids[2]),
            SavedTaskDependency(ids[1], ids[3]), SavedTaskDependency(ids[2], ids[3]),
        )
        assertTrue(TaskDependencyGraph.wouldCreateCycle(ids[3], ids[0], edges))
        assertFalse(TaskDependencyGraph.wouldCreateCycle(ids[4], ids[0], edges))
        assertFalse(TaskDependencyGraph.wouldCreateCycle(ids[1], ids[2], edges))
    }

    @Test
    fun handlesLongChainsWithoutRecursiveStackGrowth() {
        val ids = List(10_000) { UUID.randomUUID().toString() }
        val chain = ids.zipWithNext { task, prerequisite -> SavedTaskDependency(task, prerequisite) }
        assertTrue(TaskDependencyGraph.wouldCreateCycle(ids.last(), ids.first(), chain))
        assertFalse(TaskDependencyGraph.wouldCreateCycle(UUID.randomUUID().toString(), ids.first(), chain))
    }
}
