package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase
import dev.metis.agent.domain.storage.FoundationCatalog

internal object FoundationConstraints {
    fun install(db: SupportSQLiteDatabase) {
        CHECKS.forEach { (table, condition) ->
            listOf("INSERT", "UPDATE").forEach { operation ->
                db.execSQL("DROP TRIGGER IF EXISTS ${table}_${operation.lowercase()}_integrity")
                db.execSQL("""
                    CREATE TRIGGER ${table}_${operation.lowercase()}_integrity BEFORE $operation ON $table
                    WHEN $condition
                    BEGIN SELECT RAISE(ABORT, 'Invalid foundation record'); END
                """.trimIndent())
            }
        }
    }

    private val CHECKS = mapOf(
        "persons" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at
        """.trimIndent(),
        "relationships" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.kind NOT IN ('FAMILY','FRIEND','PARTNER','COLLEAGUE','OTHER')
        """.trimIndent(),
        "user_profile" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.autonomy_level NOT BETWEEN 0 AND 4 OR
                NEW.behavioral_analysis_consent != 0 OR
                NEW.active != 1
        """.trimIndent(),
        "reminders" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.precision NOT IN ('EXACT','APPROXIMATE') OR
                NEW.scheduling_state NOT IN ('PENDING','SCHEDULED','FIRED','CANCELLED','FAILED',
                    'UNSUPPORTED','DENIED') OR
                (NEW.scheduling_state = 'SCHEDULED' AND NEW.platform_token IS NULL) OR
                (NEW.delivered_at IS NOT NULL AND (NEW.scheduling_state != 'FIRED' OR 
                    NEW.delivered_at < NEW.trigger_at))
        """.trimIndent(),
        "focus_sessions" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.planned_seconds <= 0 OR
                NEW.outcome NOT IN ('RUNNING','COMPLETED','CANCELLED','INTERRUPTED') OR
                (NEW.ended_at IS NOT NULL AND NEW.ended_at < NEW.started_at) OR
                (NEW.outcome = 'RUNNING') != (NEW.ended_at IS NULL)
        """.trimIndent(),
        "events" to """
                NEW.type NOT IN (${FoundationCatalog.EVENT_TYPES.joinToString(",") { "'$it'" }}) OR
                NEW.source NOT IN ('USER','AGENT','ANDROID','WORKER') OR
                NEW.importance NOT BETWEEN 0 AND 1 OR
                NEW.schema_version != 1 OR
                (NEW.entity_type IS NULL) != (NEW.entity_id IS NULL) OR
                (NEW.entity_type IS NOT NULL AND NEW.entity_type NOT IN (
                    ${FoundationCatalog.ENTITY_TYPES.joinToString(",") { "'$it'" }}))
        """.trimIndent(),
        "promises" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.status NOT IN ('OPEN','FULFILLED','CANCELLED')
        """.trimIndent(),
        "routines" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.enabled NOT IN (0,1)
        """.trimIndent(),
        "action_runs" to """
                NEW.action_type NOT IN (${FoundationCatalog.ACTION_TYPES.joinToString(",") { "'$it'" }}) OR
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.risk NOT IN ('LOW','MEDIUM','HIGH','CRITICAL') OR
                NEW.status NOT IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED','HANDED_OFF','UNKNOWN') OR
                NEW.verification NOT IN ('VERIFIED_LOCAL','VERIFIED_PLATFORM','HANDOFF_ONLY','UNVERIFIED') OR
                (NEW.entity_type IS NULL) != (NEW.entity_id IS NULL) OR
                (NEW.entity_type IS NOT NULL AND NEW.entity_type NOT IN (
                    ${FoundationCatalog.ENTITY_TYPES.joinToString(",") { "'$it'" }})) OR
                (NEW.finished_at IS NOT NULL AND NEW.finished_at < NEW.started_at) OR
                (NEW.status = 'SUCCEEDED' AND (NEW.verification NOT IN ('VERIFIED_LOCAL',
                    'VERIFIED_PLATFORM') OR NEW.receipt IS NULL)) OR
                (NEW.status = 'HANDED_OFF' AND (NEW.verification != 'HANDOFF_ONLY' OR NEW.receipt IS NULL)) OR
                (NEW.status IN ('SUCCEEDED','FAILED','CANCELLED','HANDED_OFF') AND NEW.finished_at IS NULL) OR
                (NEW.status IN ('PENDING','RUNNING') AND NEW.verification != 'UNVERIFIED')
        """.trimIndent(),
        "action_audit" to """
                NEW.autonomy_level NOT BETWEEN 0 AND 4 OR
                NEW.decision NOT IN ('ALLOW','DENY','CONFIRM')
        """.trimIndent(),
        "agent_sessions" to """
                (NEW.ended_at IS NOT NULL AND NEW.ended_at < NEW.started_at)
        """.trimIndent(),
        "recommendations" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.score NOT BETWEEN 0 AND 1 OR
                NEW.status NOT IN ('SHOWN','ACCEPTED','REJECTED','EXPIRED') OR
                (NEW.entity_type IS NULL) != (NEW.entity_id IS NULL) OR
                (NEW.entity_type IS NOT NULL AND NEW.entity_type NOT IN (
                    ${FoundationCatalog.ENTITY_TYPES.joinToString(",") { "'$it'" }})) OR
                NEW.expires_at <= NEW.generated_at
        """.trimIndent(),
        "experiments" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.status NOT IN ('ACTIVE','COMPLETED','CANCELLED') OR
                (NEW.ended_at IS NOT NULL AND NEW.ended_at < NEW.started_at) OR
                NEW.consented_at > NEW.started_at
        """.trimIndent(),
        "habits" to """
                NEW.revision < 0 OR
                NEW.updated_at < NEW.created_at OR
                NEW.sample_count < 2 OR
                NEW.confidence NOT BETWEEN 0 AND 1 OR
                NEW.consent_required != 1 OR
                NEW.observation_end < NEW.observation_start
        """.trimIndent(),
        "derived_insights" to """
                NEW.sample_count < 2 OR
                NEW.confidence NOT BETWEEN 0 AND 1 OR
                (NEW.entity_type IS NULL) != (NEW.entity_id IS NULL) OR
                (NEW.entity_type IS NOT NULL AND NEW.entity_type NOT IN (
                    ${FoundationCatalog.ENTITY_TYPES.joinToString(",") { "'$it'" }})) OR
                NEW.observation_end < NEW.observation_start
        """.trimIndent(),
    )
}
