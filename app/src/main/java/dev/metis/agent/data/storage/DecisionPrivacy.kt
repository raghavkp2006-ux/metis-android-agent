package dev.metis.agent.data.storage

import androidx.sqlite.db.SupportSQLiteDatabase
import dev.metis.agent.domain.decision.DecisionEngine
import org.json.JSONObject

/** Delete generated recommendation content through encrypted source ownership, without task-link blockers. */
internal object DecisionPrivacy {
    fun linked(db: SupportSQLiteDatabase, codec: RecordCodec, type: String, id: String): List<String> {
        if (type !in setOf("TASK", "GOAL", "PREFERENCE", "PROJECT")) return emptyList()
        return db.query("SELECT id, evidence FROM recommendations").use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val recommendation = rows.getString(0)
                    val evidence = JSONObject(codec.decryptJson(rows.getBlob(1), "recommendations", recommendation,
                        "evidence"))
                    if (matches(evidence, type, id)) add(recommendation)
                }
            }
        }
    }

    private fun matches(evidence: JSONObject, type: String, id: String): Boolean {
        if (evidence.optString("version") != DecisionEngine.VERSION) return false
        val sources = evidence.getJSONArray("sources")
        return (0 until sources.length()).any {
            val source = sources.getJSONObject(it)
            source.getString("type") == type && source.getString("id") == id
        }
    }
}
