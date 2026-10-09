package dev.metis.agent.domain.agent

import java.time.ZoneId

/** Narrow read port: parsing has no mutation or Android executor dependency. */
interface AgentReadPort {
    suspend fun tasks(): String
    suspend fun memories(query: String, at: Long): String
    suspend fun promises(person: String): SpecialistResult
}

class LanguageSpecialist(private val reads: AgentReadPort) : AgentSpecialist {
    override suspend fun respond(request: ParsedRequest): SpecialistResult = when (request.prediction.intent) {
        AgentIntent.CHECK_TASKS -> SpecialistResult.Answer(reads.tasks())
        AgentIntent.CHECK_MEMORY -> SpecialistResult.Answer(reads.memories(
            request.entities.single { it.type == EntityKind.MEMORY }.rawValue, request.context.now.toEpochMilli()))
        AgentIntent.WHAT_DID_I_PROMISE -> reads.promises(
            request.entities.single { it.type == EntityKind.PERSON }.rawValue)
        AgentIntent.CHECK_CALENDAR -> SpecialistResult.Unsupported(
            "Calendar access is unavailable. Your free time is unknown.")
        AgentIntent.CREATE_REMINDER -> reminder(request)
        AgentIntent.CREATE_TASK -> SpecialistResult.Unsupported(
            "I understood a task request. Task creation is not available yet. No data was saved.")
        AgentIntent.CREATE_TIMER -> if (request.entities.single().normalizedValue == null) {
            SpecialistResult.Clarification("Use a timer duration from 1 second to 1 day.", setOf("duration"))
        } else SpecialistResult.Unsupported(
            "I understood a timer request. Timers are not available yet. No timer was started.")
        else -> SpecialistResult.Unsupported("This request is not supported yet. No action was taken.")
    }

    private fun reminder(request: ParsedRequest): SpecialistResult {
        val time = request.entities.first { it.type == EntityKind.TIME }.normalizedValue as? NormalizedValue.Time
        val title = request.entities.firstOrNull { it.type == EntityKind.TITLE }
        val missing = buildSet {
            if (time == null) add("date/time")
            if (title == null) add("title")
        }
        if (missing.isNotEmpty()) return SpecialistResult.Clarification(
            "Specify a future date, time with AM/PM or HH:mm, and what to remember. " +
                "Ambiguous or invalid local times need clarification.", missing)
        val trigger = requireNotNull(time).value
        return SpecialistResult.Unsupported("I understood a reminder for ${trigger.local} (${trigger.zone}). " +
            "Reminder scheduling is not available yet. Nothing was saved or scheduled.")
    }
}

/** Snapshot uses the request's fixed time and one captured zone for both extraction and resolution. */
fun languageAgentOrchestrator(
    reads: AgentReadPort, zone: () -> ZoneId = { ZoneId.systemDefault() },
): AgentOrchestrator =
    object : AgentOrchestrator {
        override suspend fun process(request: AgentRequest): AgentResult {
            val capturedZone = zone()
            val rules = EnglishRules { capturedZone }
            val contexts = object : ContextBuilder {
                override suspend fun snapshot(request: AgentRequest) = ResolvedContext(request.timestamp,
                    capturedZone, AutonomyLevel.ANSWER_ONLY,
                    CapabilitySnapshot(request.timestamp, setOf(Capability.LOCAL_READ)),
                    screenContext = request.screenContext, conversationId = request.conversationId)
            }
            return LocalAgentOrchestrator(rules, rules, contexts, LanguageSpecialist(reads), ProposalPolicy())
                .process(request)
        }
    }
