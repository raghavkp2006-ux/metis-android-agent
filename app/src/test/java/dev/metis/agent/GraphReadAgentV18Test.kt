package dev.metis.agent

import dev.metis.agent.domain.agent.AgentReadPort
import dev.metis.agent.domain.agent.AgentResultStatus
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.GraphReadAgentV18
import dev.metis.agent.domain.agent.GraphReadKindV18
import dev.metis.agent.domain.agent.GraphReadPlanV18
import dev.metis.agent.domain.agent.ReadScopeV15
import dev.metis.agent.domain.agent.SpecialistResult
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedProject
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.RequestViewModel
import dev.metis.agent.presentation.navigation.Workspace
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GraphReadAgentV18Test {
    @Test fun rejectedOrUnresolvedGraphRequestsNeverReadOrWrite(): Unit = runBlocking {
        listOf("what is pending with him", "what is pending with Rahul tomorrow",
            "what is pending in not swimming after 9pm",
            "what is pending in RF project and delete tasks", "what is pending in RF project\u202E",
            "what promises to Rahul are still open and export everything",
            "what is pending in " + "R".repeat(513)).forEach { text ->
            val f = GraphFixtureV18()
            val result = f.agent().process(f.request(text))
            assertEquals(text, AgentResultStatus.FOLLOW_UP, result.status)
            assertEquals(0, f.data.reads)
            assertEquals(0, f.store.writes)
            assertTrue(result.proposals.isEmpty())
        }
    }

    @Test fun writeRequestsStayOnTheirExistingPathAndNeverReadGraphData(): Unit = runBlocking {
        listOf("remind me about the RF project", "add a task to finish pending work",
            "add a task to ask what is pending with Rahul").forEach { text ->
            val f = GraphFixtureV18()
            val result = f.agent().process(f.request(text))
            if (text.startsWith("add")) assertEquals(AgentResultStatus.PROPOSAL, result.status)
            assertEquals(0, f.data.reads)
            assertEquals(0, f.store.writes)
        }
    }

    @Test fun plainPromiseReadPinsTheLegacyAllStatusOutputAndOrderingExactly(): Unit = runBlocking {
        val f = GraphFixtureV18()
        // LocalScopedReadsV15's current label and rows, in stored order, including closed promises.
        val legacy = "Matching saved promises to Rahul:\n" +
            "• FULFILLED: Delivered notes; due date: unavailable; record 00000000-0000-0000-0000-000000000001\n" +
            "• OPEN: Send slides; due date: unavailable; record 00000000-0000-0000-0000-000000000002\n" +
            "• CANCELLED: Old plan; due date: unavailable; record 00000000-0000-0000-0000-000000000003"
        var legacyCalls = 0
        val reads = object : AgentReadPort by ConversationReads() {
            override suspend fun promises(scope: ReadScopeV15.Promises, now: Long, zone: ZoneId): SpecialistResult {
                legacyCalls++
                assertEquals("ANY", scope.status)
                return SpecialistResult.Answer(legacy)
            }
        }
        val result = f.agent(reads).process(f.request("what did I promise Rahul?"))
        assertEquals(AgentResultStatus.ANSWER, result.status)
        assertEquals(legacy, result.message)
        assertEquals(1, legacyCalls)
        assertEquals(0, f.data.reads)
        assertTrue(result.proposals.isEmpty())
    }

    @Test fun savedNameChoiceRevalidatesStoredIdsAndNeverGuesses(): Unit = runBlocking {
        val f = GraphFixtureV18()
        f.data.personRows = listOf(SavedPerson("Rahul"), SavedPerson("Rahul"))
        val agent = f.agent()
        val first = agent.process(f.request("what is pending with Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, first.status)
        assertEquals(2, first.followUp?.suggestedChoices?.size)
        assertEquals(1, f.data.reads) // Names only; promises/tasks/reminders have not been read.
        val chosen = agent.process(f.request("2"))
        assertEquals(AgentResultStatus.ANSWER, chosen.status)
        assertTrue(chosen.message.contains("Nothing is pending"))
        val again = agent.process(f.request("what is pending with Rahul"))
        assertEquals(AgentResultStatus.FOLLOW_UP, again.status)
        f.data.personRows = emptyList()
        val deleted = agent.process(f.request("1"))
        assertTrue(deleted.message.contains("No saved person"))
        assertFalse(deleted.message.contains("Nothing is pending"))
        assertEquals(0, f.store.writes)
    }

    @Test fun nameChoicesExpireCancelAndCannotCrossSessions(): Unit = runBlocking {
        listOf("expired", "cancel", "session").forEach { case ->
            val f = GraphFixtureV18()
            f.data.personRows = listOf(SavedPerson("Rahul"), SavedPerson("Rahul"))
            val agent = f.agent()
            agent.process(f.request("what is pending with Rahul"))
            val before = f.data.reads
            if (case == "expired") f.clock.current = f.clock.current.plusSeconds(301)
            val answer = f.request(if (case == "cancel") "cancel" else "1",
                sessionId = if (case == "session") UUID.randomUUID() else f.session)
            val result = agent.process(answer)
            assertTrue(result.status in setOf(AgentResultStatus.FOLLOW_UP, AgentResultStatus.DENIED))
            assertEquals(before, f.data.reads)
            assertEquals(0, f.store.writes)
        }
    }

    @Test fun storageFailureIsNotAnEmptyResultAndForgedPlansCannotEnumerateNames(): Unit = runBlocking {
        val f = GraphFixtureV18()
        f.data.fail = true
        val failed = f.agent().process(f.request("what is pending in Research"))
        assertEquals(AgentResultStatus.FAILED, failed.status)
        assertFalse(failed.message.contains("Nothing is pending"))
        val plan = f.plan("what is pending with Rahul")
        val before = f.data.reads
        val forged = GraphReadPlanV18(plan.request, plan.zone, GraphReadKindV18.PROJECT_PENDING,
            plan.name, plan.start, plan.end)
        var rejected = false
        try { f.port.graph(forged) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals(before, f.data.reads)
    }

    @Test fun actualViewModelReusesReadAndWriteReferentsWithinThreeTurnsAndTenMinutes() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            listOf("what did I promise Rahul", "I promised Rahul the slides",
                "what is pending with Rahul").forEach { prior ->
                val f = GraphFixtureV18()
                f.data.personRows = listOf(SavedPerson("Rahul"))
                val model = model(f)
                model.submit(prior, origin)
                val before = f.data.reads
                model.submit("what is pending with him", origin)
                assertEquals(prior, AgentResultStatus.ANSWER, model.uiState.value.result?.status)
                assertTrue(f.data.reads > before)
                assertEquals(0, f.store.writes)
                model.dismiss()
                val after = f.data.reads
                model.submit("what is pending with him", origin)
                assertEquals("Who do you mean?", model.uiState.value.result?.message)
                assertEquals(after, f.data.reads)
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun missingAmbiguousAndStalePronounsAskWithoutReading() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            listOf("missing", "ambiguous", "minutes", "turns").forEach { case ->
                val f = GraphFixtureV18()
                val model = model(f)
                if (case != "missing") model.submit(if (case == "ambiguous")
                    "promise Rahul the slides and promise Maya the notes" else "I promised Rahul the slides", origin)
                if (case == "minutes") f.clock.current = f.clock.current.plusSeconds(600)
                if (case == "turns") repeat(3) { model.submit("add a task to read", origin) }
                val before = f.data.reads
                model.submit("what is pending with her", origin)
                assertEquals(case, "Who do you mean?", model.uiState.value.result?.message)
                if (case == "ambiguous") assertEquals(listOf("Rahul", "Maya"),
                    model.uiState.value.result?.followUp?.suggestedChoices)
                assertEquals(case, before, f.data.reads)
                assertEquals(0, f.store.writes)
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun projectNamesNeverBecomeTaskMutationReferents() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val f = GraphFixtureV18()
            f.data.projectRows = listOf(SavedProject("Research"))
            val model = model(f)
            model.submit("what is pending in Research", origin)
            assertEquals(AgentResultStatus.ANSWER, model.uiState.value.result?.status)
            model.submit("complete it", origin)
            assertEquals("Which item do you mean?", model.uiState.value.result?.message)
            assertTrue(model.uiState.value.result?.proposals.orEmpty().isEmpty())
            assertEquals(0, f.store.writes)
        } finally { Dispatchers.resetMain() }
    }

    private val origin = ConversationOrigin(Workspace.CHAT)

    private fun model(f: GraphFixtureV18): RequestViewModel {
        val actions = ConversationActionStore(f.store, f.clock)
        val reminders = ConfirmedReminderAgent(ConfirmedTaskAgent(ConversationReads(), f.store, { f.clock.zone }),
            actions, { f.clock.zone }, f.clock)
        val promises = ConfirmedPromiseAgent(reminders, actions, { f.clock.zone }, f.clock)
        return RequestViewModel(GraphReadAgentV18(promises, f.port, f.clock, { f.clock.zone }), f.clock,
            reminderClarification = reminders, promiseClarification = promises)
    }
}
