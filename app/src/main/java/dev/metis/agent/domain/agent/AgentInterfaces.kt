package dev.metis.agent.domain.agent

interface IntentClassifier { suspend fun classify(request: AgentRequest): IntentPrediction }
interface EntityExtractor { suspend fun extract(request: AgentRequest): List<ExtractedEntity> }
interface ContextBuilder { suspend fun snapshot(request: AgentRequest): ResolvedContext }
interface AgentSpecialist { suspend fun respond(request: ParsedRequest): SpecialistResult }
interface PolicyEngine { fun review(action: Action, context: ResolvedContext): ValidationResult }
interface AgentOrchestrator { suspend fun process(request: AgentRequest): AgentResult }

/** Specialists can suggest data or an answer, never issue an execution receipt. */
sealed interface SpecialistResult {
    data class Clarification(val question: String, val fields: Set<String>) : SpecialistResult {
        init { requireText(question); require(fields.isNotEmpty()); fields.forEach(::requireText) }
    }
    data class Answer(val message: String) : SpecialistResult { init { requireText(message) } }
    class Suggestion(
        val action: Action, val reason: String, evidence: List<Evidence> = emptyList(),
    ) : SpecialistResult {
        val evidence = frozenList(evidence)
        init { requireText(reason) }
    }
    data class Unsupported(val message: String) : SpecialistResult { init { requireText(message) } }
}
