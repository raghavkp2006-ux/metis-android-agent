package dev.metis.agent.data.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Replaces only the two action_runs integrity triggers; every v7 row and table stays intact. */
object PromiseActionMigration {
    private const val OLD_VERSION = 7
    private const val NEW_VERSION = 8
    val FROM_7_TO_8: Migration = object : Migration(OLD_VERSION, NEW_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf("INSERT", "UPDATE").forEach { operation ->
                db.execSQL("DROP TRIGGER IF EXISTS action_runs_${operation.lowercase()}_integrity")
                db.execSQL("""
                    CREATE TRIGGER action_runs_${operation.lowercase()}_integrity BEFORE $operation ON action_runs
                    WHEN $ACTION_RUN_CONDITION
                    BEGIN SELECT RAISE(ABORT, 'Invalid foundation record'); END
                """.trimIndent())
            }
        }
    }

    /** Frozen v8 predicate. Later protocol additions must not alter a historical migration. */
    private val ACTION_TYPES_V8 = listOf(
        "TASK", "MEMORY", "GOAL", "PREFERENCE", "CREATE_PROMISE", "CREATE_REMINDER", "CANCEL_REMINDER",
        "ALARM", "TIMER", "CALENDAR", "CALL", "MESSAGE", "NAVIGATION", "FOCUS", "ACCEPT_PLAN",
    )
    private val ENTITY_TYPES_V8 = listOf(
        "TASK", "SCHEDULE", "PROJECT", "GOAL", "PERSON", "ROUTINE", "PREFERENCE", "MEMORY",
        "TASK_DEPENDENCY", "USER_PROFILE", "RELATIONSHIP", "REMINDER", "FOCUS_SESSION", "EVENT",
        "PROMISE", "HABIT", "ACTION_RUN", "ACTION_AUDIT", "AGENT_SESSION", "RECOMMENDATION",
        "EXPERIMENT", "DERIVED_INSIGHT",
    )
    private val ACTION_RUN_CONDITION = """
        NEW.action_type NOT IN (${ACTION_TYPES_V8.joinToString(",") { "'$it'" }}) OR
        NEW.revision < 0 OR
        NEW.updated_at < NEW.created_at OR
        NEW.risk NOT IN ('LOW','MEDIUM','HIGH','CRITICAL') OR
        NEW.status NOT IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED','HANDED_OFF','UNKNOWN') OR
        NEW.verification NOT IN ('VERIFIED_LOCAL','VERIFIED_PLATFORM','HANDOFF_ONLY','UNVERIFIED') OR
        (NEW.entity_type IS NULL) != (NEW.entity_id IS NULL) OR
        (NEW.entity_type IS NOT NULL AND NEW.entity_type NOT IN (
            ${ENTITY_TYPES_V8.joinToString(",") { "'$it'" }})) OR
        (NEW.finished_at IS NOT NULL AND NEW.finished_at < NEW.started_at) OR
        (NEW.status = 'SUCCEEDED' AND (NEW.verification NOT IN ('VERIFIED_LOCAL',
            'VERIFIED_PLATFORM') OR NEW.receipt IS NULL)) OR
        (NEW.status = 'HANDED_OFF' AND (NEW.verification != 'HANDOFF_ONLY' OR NEW.receipt IS NULL)) OR
        (NEW.status IN ('SUCCEEDED','FAILED','CANCELLED','HANDED_OFF') AND NEW.finished_at IS NULL) OR
        (NEW.status IN ('PENDING','RUNNING') AND NEW.verification != 'UNVERIFIED')
    """.trimIndent()
}
