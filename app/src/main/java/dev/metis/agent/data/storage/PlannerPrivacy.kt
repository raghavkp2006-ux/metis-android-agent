package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase
import dev.metis.agent.domain.planner.PlannerEngine
import org.json.JSONObject

internal object PlannerPrivacy {
    fun taskBlocks(db: SupportSQLiteDatabase, codec: RecordCodec, taskId: String): List<String> =
        db.query("SELECT id, score_components_json FROM schedule_blocks WHERE score_components_json IS NOT NULL")
            .use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val id = rows.getString(0)
                    val data = JSONObject(codec.decryptJson(rows.getBlob(1), "schedule_blocks", id,
                        "score_components_json"))
                    if (data.optString("version") == PlannerEngine.VERSION &&
                        data.optString("taskId") == taskId) add(id)
                }
            }
        }

    fun runs(db: SupportSQLiteDatabase, codec: RecordCodec, blockId: String): List<String> =
        db.query("SELECT id, payload FROM action_runs WHERE action_type = 'ACCEPT_PLAN' " +
            "AND safe_error_code IS NULL").use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val id = rows.getString(0)
                    val data = JSONObject(codec.decryptJson(rows.getBlob(1), "action_runs", id, "payload"))
                    if (data.optString("operation") == PlanEncoding.OPERATION) {
                        val blocks = data.getJSONArray("blocks")
                        if ((0 until blocks.length()).any { blocks.getString(it) == blockId }) add(id)
                    }
                }
            }
        }
}
