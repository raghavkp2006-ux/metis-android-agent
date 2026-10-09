package dev.metis.agent.domain.proactive

import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedSchedule
import dev.metis.agent.domain.storage.SavedTask
import dev.metis.agent.domain.storage.ScheduleStatus
import dev.metis.agent.domain.storage.TaskStatus

enum class ProactivePriority { LOW, MEDIUM, IMPORTANT, URGENT }
val ProactivePriority.rank: Int get() = ordinal
enum class ProactiveKind { MISSED_TASK, TASK_DEADLINE, PROMISE_DUE, SCHEDULE_CONFLICT, REPEATED_POSTPONEMENT }

data class ProactiveCandidate(
    val kind: ProactiveKind,
    val identity: String,
    val priority: ProactivePriority,
)

data class ProactivePostponement(val taskId: String, val at: Long)

/** Deterministic, local-only trigger selection. Candidate identities contain record IDs, never content. */
object ProactiveEngine {
    const val MINUTE_MILLIS = 60_000L
    const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    const val DAY_MILLIS = 24 * HOUR_MILLIS
    const val LOOKAHEAD_MILLIS = DAY_MILLIS
    private const val URGENT_WINDOW_MILLIS = 2 * HOUR_MILLIS
    private const val MEDIUM_WINDOW_MILLIS = 12 * HOUR_MILLIS
    const val REPEATED_POSTPONEMENT_COUNT = 3
    const val POSTPONEMENT_WINDOW_MILLIS = 30L * DAY_MILLIS

    fun candidates(
        tasks: List<SavedTask>, promises: List<SavedPromise>, schedules: List<SavedSchedule>,
        postponements: List<ProactivePostponement>, now: Long,
    ): List<ProactiveCandidate> {
        val candidates = taskCandidates(tasks, postponements, now) + promiseCandidates(promises, now) +
            scheduleConflictCandidates(schedules, now)
        return candidates.sortedWith(compareByDescending<ProactiveCandidate> { it.priority.rank }
            .thenBy { it.kind.ordinal }.thenBy { it.identity })
    }

    private fun taskCandidates(
        tasks: List<SavedTask>, postponements: List<ProactivePostponement>, now: Long,
    ): List<ProactiveCandidate> = buildList {
        tasks.filter { it.status == TaskStatus.OPEN }.forEach { task ->
            val count = postponements.count { it.taskId == task.metadata.id && it.at <= now &&
                now - it.at <= POSTPONEMENT_WINDOW_MILLIS }
            if (count >= REPEATED_POSTPONEMENT_COUNT) add(ProactiveCandidate(
                ProactiveKind.REPEATED_POSTPONEMENT, "postponed-task:${task.metadata.id}",
                ProactivePriority.IMPORTANT))
            task.dueAt?.let { due -> taskDeadlineCandidate(task.metadata.id, due, now)?.let(::add) }
        }
    }

    private fun taskDeadlineCandidate(id: String, due: Long, now: Long): ProactiveCandidate? = when {
        due <= now -> ProactiveCandidate(ProactiveKind.MISSED_TASK, "task:$id", ProactivePriority.IMPORTANT)
        due - now > LOOKAHEAD_MILLIS -> null
        due - now <= URGENT_WINDOW_MILLIS -> ProactiveCandidate(
            ProactiveKind.TASK_DEADLINE, "task:$id", ProactivePriority.URGENT)
        due - now <= MEDIUM_WINDOW_MILLIS -> ProactiveCandidate(
            ProactiveKind.TASK_DEADLINE, "task:$id", ProactivePriority.MEDIUM)
        else -> ProactiveCandidate(ProactiveKind.TASK_DEADLINE, "task:$id", ProactivePriority.LOW)
    }

    private fun promiseCandidates(promises: List<SavedPromise>, now: Long) = promises.asSequence()
        .filter { it.status == "OPEN" }.mapNotNull { promise ->
            promise.dueAt?.takeIf { it <= now + LOOKAHEAD_MILLIS }?.let { due ->
                ProactiveCandidate(ProactiveKind.PROMISE_DUE, "promise:${promise.metadata.id}",
                    if (due <= now + URGENT_WINDOW_MILLIS) ProactivePriority.URGENT else ProactivePriority.IMPORTANT)
            }
        }.toList()

    private fun scheduleConflictCandidates(schedules: List<SavedSchedule>, now: Long): List<ProactiveCandidate> {
        val accepted = schedules.filter { it.status == ScheduleStatus.ACCEPTED }
            .sortedWith(compareBy(SavedSchedule::startAt, { it.metadata.id }))
        return buildList {
            accepted.forEachIndexed { index, first ->
                accepted.drop(index + 1).takeWhile { it.startAt < first.endAt }.forEach { second ->
                    if (second.endAt > now && first.startAt < now + LOOKAHEAD_MILLIS) add(ProactiveCandidate(
                        ProactiveKind.SCHEDULE_CONFLICT, "schedule:${first.metadata.id}:${second.metadata.id}",
                        ProactivePriority.IMPORTANT))
                }
            }
        }
    }
}

data class ProactiveSuppressionPolicy(
    val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
    val minimumGapMillis: Long = DEFAULT_MINIMUM_GAP_MILLIS,
    val duplicateWindowMillis: Long = DEFAULT_DUPLICATE_WINDOW_MILLIS,
    val rollingWindowMillis: Long = ProactiveEngine.DAY_MILLIS,
) {
    init { require(dailyLimit > 0 && minimumGapMillis >= 0 && duplicateWindowMillis >= 0 && rollingWindowMillis > 0) }
}

const val DEFAULT_DAILY_LIMIT = 2
const val DEFAULT_MINIMUM_GAP_MILLIS = 4 * ProactiveEngine.HOUR_MILLIS
const val DEFAULT_DUPLICATE_WINDOW_MILLIS = ProactiveEngine.DAY_MILLIS

/** Rolling-window and per-event suppression policy, kept pure so its boundaries can be tested. */
object ProactiveSuppression {
    fun allows(
        now: Long,
        recentPosts: List<Long>,
        lastPostAt: Long?,
        thisEventAt: Long?,
        policy: ProactiveSuppressionPolicy = ProactiveSuppressionPolicy(),
    ): Boolean {
        val inWindow = recentPosts.count { it <= now && now - it < policy.rollingWindowMillis }
        val tooSoon = lastPostAt?.let { now < it || now - it < policy.minimumGapMillis } == true
        val duplicate = thisEventAt?.let { now < it || now - it < policy.duplicateWindowMillis } == true
        return inWindow < policy.dailyLimit && !tooSoon && !duplicate
    }
}
