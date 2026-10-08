package dev.metis.agent.data.storage

/** Names come from a fixed catalog; only values are bound. No arbitrary SQL is exposed. */
internal fun validateFoundationReference(database: PersonalDatabase, type: String?, id: String?) {
    if (type == null) { require(id == null); return }
    val table = requireNotNull(REFERENCE_TABLES[type])
    database.openHelper.writableDatabase.query("SELECT 1 FROM $table WHERE id = ?", arrayOf(requireNotNull(id)))
        .use { require(it.moveToFirst()) }
}

private val REFERENCE_TABLES = mapOf(
    "TASK" to "tasks",
    "SCHEDULE" to "schedule_blocks",
    "PROJECT" to "projects",
    "GOAL" to "goals",
    "PERSON" to "persons",
    "ROUTINE" to "routines",
    "PREFERENCE" to "preferences",
    "MEMORY" to "memories",
    "TASK_DEPENDENCY" to "task_dependencies",
    "USER_PROFILE" to "user_profile",
    "RELATIONSHIP" to "relationships",
    "REMINDER" to "reminders",
    "FOCUS_SESSION" to "focus_sessions",
    "EVENT" to "events",
    "PROMISE" to "promises",
    "HABIT" to "habits",
    "ACTION_RUN" to "action_runs",
    "ACTION_AUDIT" to "action_audit",
    "AGENT_SESSION" to "agent_sessions",
    "RECOMMENDATION" to "recommendations",
    "EXPERIMENT" to "experiments",
    "DERIVED_INSIGHT" to "derived_insights",
)
