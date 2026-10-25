package dev.metis.agent.evaluation.day1

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.metis.agent.domain.agent.*
import dev.metis.agent.domain.decision.*
import dev.metis.agent.domain.planner.*
import dev.metis.agent.domain.storage.*
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

/** Host-only exporter: production parser/agents/policy, read-only fixture ports; never accepts actions. */
object Day1RulesExporter {
    private val gson = Gson()
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        require(args.size == 3) { "input.jsonl output.jsonl fixture.json" }
        val fixture = JsonParser.parseString(File(args[2]).readText()).asJsonObject
        val output = File(args[0]).readLines().filter { it.isNotBlank() }.map { line ->
            snapshot(JsonParser.parseString(line).asJsonObject, fixture)
        }
        File(args[1]).writeText(output.joinToString("\n", postfix = "\n"))
    }

    private suspend fun snapshot(input: JsonObject, fixture: JsonObject): String {
        val id = input["id"].asInt
        val text = input["text"].asString
        val useFixture = input["context"].asString == "fixture"
        val zone = ZoneId.of("Asia/Kolkata")
        val now = if (useFixture) runCatching { fixture.get("now")?.asString }.getOrNull()?.let {
            runCatching { Instant.parse(it) }.getOrElse { _ ->
                runCatching { LocalDateTime.parse(it).atZone(zone).toInstant() }.getOrNull()
            }
        } else Instant.EPOCH
        val base = linkedMapOf<String, Any>("id" to id, "text_sha256" to input["text_sha256"].asString,
            "intent" to "UNKNOWN", "entities" to emptyMap<String, String>(),
            "clauses" to listOf(mapOf("intent" to "UNKNOWN", "entities" to emptyMap<String, String>())),
            "needs_clarification" to false, "abstained" to true, "confidence" to 0.0,
            "proposed_writes" to emptyList<Any>(), "read_dispatched" to false, "latency_ms" to 0.0,
            "evaluated" to false)
        if (now == null) {
            base["reason"] = "fixture now missing or invalid; no execution under substituted state"
            return gson.toJson(base)
        }
        // Never carry state, prior proposals, or conversation history between rows.
        val tasks = if (useFixture) fixture["tasks"].asJsonArray.map { it.asJsonObject } else emptyList()
        val port = FixturePorts(now, zone, tasks)
        val request = AgentRequest(UUID.nameUUIDFromBytes("day1:$id".toByteArray()), text,
            if (input["style"].asString == "voice") InputSource.VOICE else InputSource.TEXT, now)
        val rules = EnglishRules(ReadCandidate.V16) { zone }
        val taskAgent = ConfirmedTaskAgent(port, port, { zone }, true, ReadCandidate.V16)
        val reminderAgent = ConfirmedReminderAgent(taskAgent, port.reminders(), { zone })
        val agent = PlannerAgent(DecisionAgent(reminderAgent, port.decision()), port.planner())
        val start = System.nanoTime()
        val parsed = rules.parse(request)
        val result = agent.process(request)
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        val entities = entityMap(parsed)
        val writes = result.proposals.map { proposal ->
            val proposalEntities = LinkedHashMap(entities)
            when (val action = proposal.action) {
                is TaskAction -> when (val mutation = action.mutation) {
                    is TaskMutation.Create -> { proposalEntities.clear(); proposalEntities["task"] = mutation.title }
                    else -> Unit
                }
                is CreateReminderAction -> Unit
                else -> error("unmapped proposal: ${action.type}")
            }
            mapOf("intent" to parsed.intent.name, "entities" to proposalEntities,
                "policy_allowed" to (ProposalPolicy().review(proposal.action,
                    port.context(request, ActionRequirements.targets(proposal.action))) == ValidationResult.Valid))
        }
        base.putAll(mapOf("intent" to parsed.intent.name, "entities" to entities,
            // The current parser treats the entire utterance as one request. Do not split on 'and'.
            "clauses" to listOf(mapOf("intent" to parsed.intent.name, "entities" to entities)),
            "needs_clarification" to (result.status == AgentResultStatus.FOLLOW_UP),
            "abstained" to (result.status in setOf(AgentResultStatus.FOLLOW_UP, AgentResultStatus.DENIED,
                AgentResultStatus.UNSUPPORTED, AgentResultStatus.FAILED)),
            "confidence" to parsed.prediction.confidence, "proposed_writes" to writes,
            "read_dispatched" to (port.reads > 0), "latency_ms" to elapsed, "evaluated" to true,
            "status" to result.status.name, "message" to result.message,
            "resolved_task_ids" to port.resolved, "task_state_size" to tasks.size,
            "clock" to now.toString(), "zone" to zone.id,
            "state_scored" to useFixture, "prior_turn" to false))
        return gson.toJson(base)
    }

    private fun entityMap(parsed: LanguageParse): Map<String, String> = parsed.entities.associate {
        val key = when (it.type) {
            EntityKind.TITLE -> if (parsed.intent == AgentIntent.UPDATE_TASK) "new_name" else "task"
            EntityKind.TIME -> if (parsed.intent == AgentIntent.POSTPONE_TASK) "to_time" else "time"
            EntityKind.TASK -> "task"
            EntityKind.PERSON -> "person"
            EntityKind.DURATION -> "duration"
            else -> "anchor"
        }
        key to it.rawValue
    }

    private class FixturePorts(val now: Instant, val zone: ZoneId, val tasks: List<JsonObject>) : AgentReadPort, AcceptedTaskStore {
        var reads = 0
        val resolved = mutableListOf<String>()
        suspend fun context(request: AgentRequest, targets: List<RevisionTarget>) = ResolvedContext(now, zone,
            AutonomyLevel.SUGGEST, CapabilitySnapshot(now, setOf(Capability.LOCAL_READ, Capability.LOCAL_WRITE,
                Capability.REMINDER_SCHEDULE, Capability.NOTIFICATIONS, Capability.PLAN_ACCEPTANCE)),
            screenContext = request.screenContext, conversationId = request.conversationId, referencedEntities = targets)
        override suspend fun context(request: AgentRequest) = context(request, emptyList())
        override suspend fun resolveCompletion(title: String): CompletionSelection {
            // Same exact case-insensitive, unique open/nonrecurring selection as TaskActionMutation.resolve.
            val matches = tasks.filter { it["title"].asString.equals(title, true) &&
                it["status"].asString in setOf("pending", "OPEN") }
            val task = matches.singleOrNull() ?: return CompletionSelection.Unavailable
            if (task.has("recurrenceRule") && !task["recurrenceRule"].isJsonNull) return CompletionSelection.Unavailable
            val key = task["id"].asString
            resolved.add(key)
            val uuid = runCatching { UUID.fromString(key) }.getOrElse { UUID.nameUUIDFromBytes(key.toByteArray()) }
            return CompletionSelection.Selected(task["title"].asString,
                RevisionTarget(EntityReference(MemoryEntityType.TASK, uuid), 0))
        }
        override suspend fun tasks(): String { reads++; return tasks.joinToString { it["title"].asString }.ifBlank { "No tasks." } }
        override suspend fun memories(query: String, at: Long): String { reads++; return "No memories." }
        override suspend fun promises(person: String): SpecialistResult {
            reads++; return SpecialistResult.Clarification("No saved person matches that name.", setOf("person"))
        }
        override suspend fun tasks(scope: ReadScopeV15.Tasks, now: Long, zone: ZoneId): SpecialistResult =
            SpecialistResult.Answer(tasks())
        override suspend fun memories(scope: ReadScopeV15.Memories, now: Long, zone: ZoneId): SpecialistResult =
            SpecialistResult.Answer(memories("", now))
        override suspend fun promises(scope: ReadScopeV15.Promises, now: Long, zone: ZoneId): SpecialistResult = promises("")
        override suspend fun calendar(scope: ReadScopeV15.Calendar, now: Long, zone: ZoneId): SpecialistResult {
            reads++; return SpecialistResult.Answer("No calendar records; availability unknown.")
        }
        override suspend fun calendar(scope: CalendarReadScope, now: Long, zone: ZoneId): SpecialistResult {
            reads++; return SpecialistResult.Answer("No calendar records; availability unknown.")
        }
        override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt = error("accept disabled")
        override suspend fun retry(actionId: UUID): ActionReceipt = error("retry disabled")
        override suspend fun cancelPending(actionId: UUID) = error("cancel disabled")
        override suspend fun undo(receipt: ActionReceipt): UndoResult = error("undo disabled")
        override fun observeHistory() = flowOf(emptyList<TaskActionHistoryEntry>())
        fun reminders() = object : AcceptedReminderStore {
            override suspend fun context(request: AgentRequest) = this@FixturePorts.context(request)
            override suspend fun accept(proposal: ActionProposal, confirmationId: UUID): ActionReceipt = error("accept disabled")
            override suspend fun retry(actionId: UUID): ActionReceipt = error("retry disabled")
            override suspend fun cancel(actionId: UUID): UndoResult = error("cancel disabled")
            override suspend fun undo(receipt: ActionReceipt): UndoResult = error("undo disabled")
            override suspend fun reconcile() = error("reconcile disabled")
            override fun history() = flowOf(emptyList<ReminderHistoryEntry>())
        }
        // Supplied fixture has no task estimates; neither domain engine can produce a planned block.
        private fun savedTasks() = tasks.map { SavedTask(it["title"].asString,
            metadata = RecordMetadata(id = UUID.nameUUIDFromBytes(it["id"].asString.toByteArray()).toString(), createdAt = 0)) }
        fun decision() = DecisionPort { id, seconds ->
            reads++
            val ranked = DecisionEngine.rank(DecisionInputs(savedTasks(), emptyList()), DecisionContext(now.toEpochMilli(), seconds))
            AgentResult(id, AgentResultStatus.ANSWER, ranked.firstOrNull()?.task?.title ?: "No eligible saved task.")
        }
        fun planner() = object : PlannerPort {
            override suspend fun prepare(request: AgentRequest): PlanPreparation {
                reads++
                val window = PlannerWindow.resolve(request.text, now, zone, null)
                    ?: return PlanPreparation(AgentResult(request.id, AgentResultStatus.FOLLOW_UP, "Specify planning window.",
                        followUp = FollowUpQuestion(UUID.randomUUID(), request.id, "Specify planning window.", setOf("window"))))
                val output = PlannerEngine.generate(DecisionInputs(savedTasks(), emptyList()), emptyList(), window.span)
                check(output.blocks.isEmpty()) { "Planner fixture adapter only supports tasks without estimates" }
                return PlanPreparation(AgentResult(request.id, AgentResultStatus.ANSWER, "No eligible whole task fits; no plan saved."))
            }
            override suspend fun accept(draft: PlanDraft, confirmationId: UUID): ActionReceipt = error("accept disabled")
        }
    }
}
