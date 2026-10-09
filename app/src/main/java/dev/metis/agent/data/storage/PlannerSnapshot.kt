package dev.metis.agent.data.storage

import dev.metis.agent.domain.decision.DecisionInputs
import dev.metis.agent.domain.storage.PreferenceValue
import dev.metis.agent.domain.storage.SavedSchedule
import java.security.MessageDigest
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import org.json.JSONObject

internal data class PlannerSnapshot(val inputs: DecisionInputs, val schedules: List<SavedSchedule>,
    val dayStart: LocalTime?, val fingerprint: String, val routinesEnabled: Boolean,
    val bookedTasksUntil: Map<String, Long>)

internal suspend fun plannerSnapshot(repository: LocalPersonalRepository): PlannerSnapshot {
    val inputs = DecisionInputs(repository.observeTasks().first(),
        repository.dependencies.observeDependencies().first(),
        repository.planning.observeGoals().first(), repository.planning.observeProjects().first())
    val schedules = repository.observeSchedules().first()
    val preferences = repository.preferences.observePreferences().first()
    val routines = repository.foundation.routine.observe().first()
    val pieces = listOf(inputs.tasks.sortedBy { it.metadata.id }.toString(),
        inputs.dependencies.sortedBy { it.metadata.id }.toString(), inputs.goals.sortedBy { it.metadata.id }.toString(),
        inputs.projects.sortedBy { it.metadata.id }.toString(), schedules.sortedBy { it.metadata.id }.toString(),
        preferences.sortedBy { it.metadata.id }.toString(), routines.sortedBy { it.metadata.id }.toString())
    val digest = MessageDigest.getInstance("SHA-256")
    pieces.forEach { digest.update(it.toByteArray(Charsets.UTF_8)); digest.update(0.toByte()) }
    val fingerprint = digest.digest().joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }
    val start = preferences.mapNotNull { it.value as? PreferenceValue.DayStart }.singleOrNull()?.time
    val booked = schedules.asSequence().filter { it.status.name == "ACCEPTED" }.mapNotNull { schedule ->
        schedule.scoreComponentsJson?.let { encoded -> runCatching {
            JSONObject(encoded).takeIf { it.optString("version") == PlannerVersion.VALUE }
                ?.optString("taskId")?.takeIf(String::isNotBlank)?.let { it to schedule.endAt }
        }.getOrNull() }
    }.toMap()
    return PlannerSnapshot(inputs, schedules, start, fingerprint, routines.any { it.enabled }, booked)
}

private object PlannerVersion { const val VALUE = "DEADLINE_PLAN_V1" }

private const val BYTE_MASK = 255
