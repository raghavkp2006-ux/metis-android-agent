package dev.metis.agent.domain.agent

import java.time.Clock
import java.time.ZoneId

/** Explicit disabled language baseline until evaluated Phase 7 rules replace it. No guessed intent or ML. */
class UnsupportedLanguage : IntentClassifier, EntityExtractor, AgentSpecialist {
    override suspend fun classify(request: AgentRequest) = IntentPrediction(AgentIntent.UNSUPPORTED, 1.0)
    override suspend fun extract(request: AgentRequest): List<ExtractedEntity> = emptyList()
    override suspend fun respond(request: ParsedRequest) = SpecialistResult.Unsupported(REQUEST_UNSUPPORTED_MESSAGE)
}

/** Runtime time/zone facts only. Stored autonomy values cannot enable unimplemented capabilities. */
class BaselineContextBuilder(
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ContextBuilder {
    override suspend fun snapshot(request: AgentRequest): ResolvedContext {
        val now = clock.instant()
        return ResolvedContext(now, zone(), AutonomyLevel.ANSWER_ONLY, CapabilitySnapshot(now, emptySet()),
            screenContext = request.screenContext, conversationId = request.conversationId)
    }
}

fun baselineAgentOrchestrator(): AgentOrchestrator {
    val language = UnsupportedLanguage()
    return LocalAgentOrchestrator(language, language, BaselineContextBuilder(), language, ProposalPolicy())
}
