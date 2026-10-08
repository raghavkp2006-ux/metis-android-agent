package dev.metis.agent

import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.Capability
import dev.metis.agent.domain.agent.CapabilitySnapshot
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.RevisionTarget
import dev.metis.agent.domain.agent.RiskLevel
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.ValidationResult
import dev.metis.agent.domain.storage.MemoryEntityType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalPolicyTest {
    private val policy = ProposalPolicy()

    @Test
    fun answerOnlyAndUnimplementedHigherAutonomyCannotEnableMutation() {
        val action = TaskAction(identity(), TaskMutation.Create("task"))
        AutonomyLevel.entries.filter { it != AutonomyLevel.SUGGEST }.forEach {
            assertTrue(policy.review(action, testContext(autonomy = it)) is ValidationResult.Invalid)
        }
        assertEquals(ValidationResult.Valid, policy.review(action, testContext()))
    }

    @Test
    fun capabilityRevocationAndExpiryInvalidatePendingProposal() {
        val action = TaskAction(identity(), TaskMutation.Create("task"))
        val proposal = ActionProposal(id(), action, RiskLevel.R1, "reason", emptyList(), true,
            Instant.EPOCH, Instant.ofEpochSecond(300))
        assertEquals(ValidationResult.Valid, policy.reviewProposal(proposal, testContext(at = 299)))
        assertTrue(policy.reviewProposal(proposal, testContext(at = 300)) is ValidationResult.Invalid)
        assertTrue(policy.reviewProposal(proposal, testContext(at = -1)) is ValidationResult.Invalid)
        assertTrue(policy.reviewProposal(proposal, testContext(capabilities = emptySet())) is ValidationResult.Invalid)
    }

    @Test
    fun missingOrChangedEntityRevisionPreventsProposal() {
        val target = taskTarget()
        val action = TaskAction(identity(), TaskMutation.Delete(target))
        assertTrue(policy.review(action, testContext()) is ValidationResult.Invalid)
        assertTrue(policy.review(action, testContext(targets = listOf(target.copy(expectedRevision = 1))))
            is ValidationResult.Invalid)
        assertEquals(ValidationResult.Valid, policy.review(action, testContext(targets = listOf(target))))
    }

    @Test
    fun wrongEntityTypeCannotBeUsedForTaskMutation() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskMutation.Delete(RevisionTarget(EntityReference(MemoryEntityType.PERSON, id()), 0))
        }
    }

    @Test
    fun contextCopiesCapabilitiesAndReferencesAndRejectsDifferentCaptureTime() {
        val capabilities = mutableSetOf(Capability.LOCAL_WRITE)
        val targets = mutableListOf(taskTarget())
        val context = testContext(capabilities = capabilities, targets = targets)
        capabilities.clear()
        targets.clear()
        assertEquals(setOf(Capability.LOCAL_WRITE), context.capabilities.available)
        assertEquals(1, context.referencedEntities.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (context.referencedEntities as MutableList).clear()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResolvedContext(Instant.EPOCH, ZoneId.of("UTC"), AutonomyLevel.SUGGEST,
                CapabilitySnapshot(Instant.ofEpochSecond(1), emptySet()))
        }
    }
}
internal fun taskTarget() = RevisionTarget(EntityReference(MemoryEntityType.TASK, id()), 0)
internal fun testContext(
    at: Long = 0,
    autonomy: AutonomyLevel = AutonomyLevel.SUGGEST,
    capabilities: Set<Capability> = setOf(Capability.LOCAL_WRITE),
    targets: List<RevisionTarget> = emptyList(),
) = ResolvedContext(Instant.ofEpochSecond(at), ZoneId.of("UTC"), autonomy,
    CapabilitySnapshot(Instant.ofEpochSecond(at), capabilities), referencedEntities = targets)
