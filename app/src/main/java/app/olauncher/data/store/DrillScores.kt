package app.olauncher.data.store

import app.olauncher.data.Prefs
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Tracks first-try accuracy per drill type.
 * Stored in [Prefs.DRILL_SCORES] as a JSON object mapping type id to `{asked, firstTryRight}`.
 */
object DrillScores {

    data class Score(val asked: Int, val firstTryRight: Int) {
        val percentage: Int
            get() = if (asked > 0) ((firstTryRight * 100.0) / asked).roundToInt() else 0
    }

    /**
     * Decodes the JSON stored in [raw].
     * Returns an empty map on blank input or on any malformed JSON.
     */
    fun decode(raw: String?): Map<String, Score> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Score>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val scoreObj = obj.optJSONObject(key) ?: continue
                if (!scoreObj.has("asked") || !scoreObj.has("firstTryRight")) continue
                val asked = scoreObj.optInt("asked", -1)
                val firstTryRight = scoreObj.optInt("firstTryRight", -1)
                if (asked >= 0 && firstTryRight >= 0) {
                    result[key] = Score(asked = asked, firstTryRight = firstTryRight)
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Encodes [scores] to JSON. */
    fun encode(scores: Map<String, Score>): String {
        val obj = JSONObject()
        for ((key, score) in scores) {
            val scoreObj = JSONObject()
                .put("asked", score.asked)
                .put("firstTryRight", score.firstTryRight)
            obj.put(key, scoreObj)
        }
        return obj.toString()
    }

    /**
     * Adds [asked] and [firstTryRight] to the score for [typeId].
     */
    fun recordRound(
        existing: Map<String, Score>,
        typeId: String,
        asked: Int,
        firstTryRight: Int,
    ): Map<String, Score> {
        if (asked <= 0) return existing
        val current = existing[typeId] ?: Score(0, 0)
        val updated = Score(
            asked = current.asked + asked,
            firstTryRight = current.firstTryRight + firstTryRight,
        )
        return existing + (typeId to updated)
    }

    fun recordRound(
        prefs: Prefs,
        typeId: String,
        asked: Int,
        firstTryRight: Int,
    ) {
        val current = decode(prefs.drillScores)
        val updated = recordRound(current, typeId, asked, firstTryRight)
        prefs.drillScores = encode(updated)
    }

    fun recordRound(
        prefs: Prefs,
        type: DrillType,
        asked: Int,
        firstTryRight: Int,
    ) {
        recordRound(prefs, type.id, asked, firstTryRight)
    }
}
