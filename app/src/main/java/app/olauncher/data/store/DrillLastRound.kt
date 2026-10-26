package app.olauncher.data.store

import app.olauncher.data.Prefs
import org.json.JSONObject

/**
 * The result of the most recent drill round, shown on the Today Drills card as
 * "Last round: <type> · <right> of <total>". Stored in [Prefs.DRILL_LAST_ROUND] as
 * `{"type", "right", "total"}`. A malformed or inconsistent record decodes to null,
 * so the card falls back to the "Suggested" line.
 */
data class DrillLastRound(
    val typeId: String,
    /** Items answered right on the first try. */
    val right: Int,
    /** Items in the round. */
    val total: Int,
) {
    companion object {
        /**
         * Decodes the JSON stored in [raw]. Returns null on blank input, malformed JSON,
         * an unknown type, or counts that do not make sense (`total` must be positive and
         * `right` within it).
         */
        fun decode(raw: String?): DrillLastRound? {
            if (raw.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(raw)
                val typeId = obj.optString("type")
                val right = obj.optInt("right", -1)
                val total = obj.optInt("total", -1)
                from(typeId, right, total)
            } catch (e: Exception) {
                null
            }
        }

        /** Builds a round, or null when the values do not make sense. */
        fun from(typeId: String?, right: Int, total: Int): DrillLastRound? {
            if (typeId.isNullOrBlank()) return null
            if (DrillType.fromId(typeId) == null) return null
            if (total <= 0) return null
            if (right < 0 || right > total) return null
            return DrillLastRound(typeId, right, total)
        }

        /** Encodes [round] to JSON. */
        fun encode(round: DrillLastRound): String =
            JSONObject()
                .put("type", round.typeId)
                .put("right", round.right)
                .put("total", round.total)
                .toString()

        /** Makes [round] the one the Drills card shows as the last round. */
        fun record(prefs: Prefs, round: DrillLastRound) {
            prefs.drillLastRound = encode(round)
        }
    }
}
