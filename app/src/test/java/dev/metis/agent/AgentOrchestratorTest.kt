package dev.metis.agent

import dev.metis.agent.domain.agent.AgentIntent
import dev.metis.agent.domain.agent.AgentRequest
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.AgentSpecialist
import dev.metis.agent.domain.agent.AutonomyLevel
import dev.metis.agent.domain.agent.BaselineContextBuilder
import dev.metis.agent.domain.agent.ContextBuilder
import dev.metis.agent.domain.agent.EntityExtractor
import dev.metis.agent.domain.agent.ExtractedEntity
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.agent.IntentClassifier
import dev.metis.agent.domain.agent.IntentPrediction
import dev.metis.agent.domain.agent.LocalAgentOrchestrator
import dev.metis.agent.domain.agent.ParsedRequest
import dev.metis.agent.domain.agent.ProposalPolicy
import dev.metis.agent.domain.agent.ResolvedContext
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.agent.TaskAction
import dev.metis.agent.domain.agent.TaskMutation
import dev.metis.agent.domain.agent.baselineAgentOrchestrator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {
    @Test
    fun everyInputSourceUsesAnExplicitUnsupportedBaselineWithoutProposalsOrReceipts(): Unit = runBlocking {
        InputSource.entries.forEach {
            val request = request(it)
            val result = baselineAgentOrchestrator().process(request)
            assertEquals(request.id, result.requestId)
            assertEquals(AgentResultStatus.UNSUPPORTED, result.status)
            assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
        }
    }

    @Test
    fun negationStopsBeforeContextExtractionAndSpecialists(): Unit = runBlocking {
        val fixture = PipelineFixture(IntentPrediction(AgentIntent.CREATE_TASK, 1.0, negated = true))
        assertEquals(AgentResultStatus.DENIED, fixture.pipeline().process(request()).status)
        assertEquals(0, fixture.contextCalls)
        assertEquals(0, fixture.specialistCalls)
    }

    @Test
    fun unknownAndLowOrMediumConfidenceProduceFollowUpBeforeMutation(): Unit = runBlocking {
        listOf(IntentPrediction(AgentIntent.UNKNOWN, 1.0), IntentPrediction(AgentIntent.CREATE_TASK, 0.59),
            IntentPrediction(AgentIntent.CREATE_TASK, 0.89)).forEach {
            val fixture = PipelineFixture(it)
            val result = fixture.pipeline().process(request())
            assertEquals(AgentResultStatus.FOLLOW_UP, result.status)
            assertEquals(setOf("intent"), result.followUp?.missingFields)
            assertEquals(0, fixture.specialistCalls)
        }
    }

    @Test
    fun highConfidenceCannotOverrideAnswerOnlyOrUnavailableCapabilities(): Unit = runBlocking {
        listOf(testContext(autonomy = AutonomyLevel.ANSWER_ONLY), testContext(capabilities = emptySet())).forEach {
            val fixture = PipelineFixture(context = it)
            val result = fixture.pipeline().process(request())
            assertEquals(AgentResultStatus.DENIED, result.status)
            assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
        }
    }

    @Test
    fun resolvedHighConfidenceSuggestionProducesCorrelatedExpiringProposalWithoutExecution(): Unit = runBlocking {
        val request = request()
        val result = PipelineFixture().pipeline().process(request)
        assertEquals(AgentResultStatus.PROPOSAL, result.status)
        val proposal = result.proposals.single()
        assertEquals(request.id, proposal.requestId)
        assertEquals(Instant.ofEpochSecond(300), proposal.expiresAt)
        assertTrue(proposal.requiresConfirmation && result.completedActions.isEmpty())
        assertThrows(UnsupportedOperationException::class.java) { (result.proposals as MutableList).clear() }
    }

    @Test
    fun unresolvedTimeReturnsFollowUpWithoutCallingSpecialist(): Unit = runBlocking {
        val context = ResolvedContext(Instant.EPOCH, ZoneId.of("UTC"), AutonomyLevel.SUGGEST,
            testContext().capabilities, setOf("AM/PM"))
        val fixture = PipelineFixture(context = context)
        assertEquals(setOf("AM/PM"), fixture.pipeline().process(request()).followUp?.missingFields)
        assertEquals(0, fixture.specialistCalls)
    }

    @Test
    fun wrongRequestCorrelationAndPrivateExceptionsFailWithGenericMessage(): Unit = runBlocking {
        val wrongRequest = PipelineFixture(response = {
            SpecialistResult.Suggestion(TaskAction(identity(), TaskMutation.Create("task")), "reason")
        }).pipeline().process(request())
        assertEquals(AgentResultStatus.FAILED, wrongRequest.status)
        val failure = PipelineFixture(response = { error("Synthetic private content") }).pipeline().process(request())
        assertEquals(AgentResultStatus.FAILED, failure.status)
        assertFalse(failure.message.contains("private"))
        assertTrue(failure.completedActions.isEmpty())
    }

    @Test
    fun cancellationPropagatesAndReadOnlyAnswersNeedNoMutationAuthority() {
        assertThrows(CancellationException::class.java) {
            runBlocking { PipelineFixture(response = { throw CancellationException() }).pipeline().process(request()) }
        }
        runBlocking {
            val result = PipelineFixture(context = testContext(autonomy = AutonomyLevel.ANSWER_ONLY),
                response = { SpecialistResult.Answer("Local answer") }).pipeline().process(request())
            assertEquals(AgentResultStatus.ANSWER, result.status)
            assertTrue(result.proposals.isEmpty() && result.completedActions.isEmpty())
        }
    }

    @Test
    fun baselineContextUsesInjectedClockAndZoneWithNoAuthority(): Unit = runBlocking {
        val context = BaselineContextBuilder(Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")),
            { ZoneId.of("Asia/Kolkata") }).snapshot(request())
        assertEquals(Instant.EPOCH, context.now)
        assertEquals(ZoneId.of("Asia/Kolkata"), context.zoneId)
        assertEquals(AutonomyLevel.ANSWER_ONLY, context.autonomy)
        assertTrue(context.capabilities.available.isEmpty())
    }
}

private class PipelineFixture(
    private val prediction: IntentPrediction = IntentPrediction(AgentIntent.CREATE_TASK, 1.0),
    private val context: ResolvedContext = testContext(),
    private val response: (ParsedRequest) -> SpecialistResult = {
        SpecialistResult.Suggestion(TaskAction(identity(it.request.id), TaskMutation.Create("task")), "User request")
    },
) : IntentClassifier, EntityExtractor, ContextBuilder, AgentSpecialist {
    var contextCalls = 0
    var specialistCalls = 0
    override suspend fun classify(request: AgentRequest) = prediction
    override suspend fun extract(request: AgentRequest): List<ExtractedEntity> = emptyList()
    override suspend fun snapshot(request: AgentRequest): ResolvedContext { contextCalls++; return context }
    override suspend fun respond(request: ParsedRequest): SpecialistResult {
        specialistCalls++
        return response(request)
    }
    fun pipeline() = LocalAgentOrchestrator(this, this, this, this, ProposalPolicy())
}
private fun request(source: InputSource = InputSource.TEXT) =
    AgentRequest(id(), "Synthetic request", source, Instant.EPOCH)
