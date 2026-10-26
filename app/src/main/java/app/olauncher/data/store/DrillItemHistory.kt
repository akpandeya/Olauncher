package app.olauncher.data.store

import app.olauncher.data.Prefs
import org.json.JSONObject

/**
 * Per-item maturity: how many rounds each drill item was answered correctly in.
 * Stored in [Prefs.DRILL_ITEM_HISTORY] as a flat JSON object mapping the encoded
 * [DrillItemId] (for example `"prepositions:prep:mit:1"`) to the count.
 *
 * This is what earns typing (#639): an item becomes typeable only after it was
 * answered correctly [TYPED_MATURITY] times across rounds. Flashcards never count
 * — they are not scored — and a wrong or almost answer does not increment.
 * The key is new; no existing prefs key is touched.
 */
object DrillItemHistory {

    /** An item becomes typeable once it has been answered correctly this many times. */
    const val TYPED_MATURITY = DrillRoundPlanner.TYPED_MATURITY

    /** Upper bound kept in storage; typing is earned at [TYPED_MATURITY], the rest is slack. */
    const val MAX_COUNT = 9

    /** The stored maturity of [id], 0 when the item has never been answered right. */
    fun maturity(counts: Map<String, Int>, id: DrillItemId): Int = counts[id.encode()] ?: 0

    /** Decodes [raw] to per-item maturities keyed by [DrillItemId]; malformed ids are dropped. */
    fun maturities(raw: String?): Map<DrillItemId, Int> =
        decode(raw).mapNotNull { (key, count) -> DrillItemId.parse(key)?.let { it to count } }.toMap()

    /**
     * Adds one (capped at [MAX_COUNT]) for every item whose [result] is RIGHT;
     * wrong and almost answers leave the count unchanged.
     */
    fun updated(counts: Map<String, Int>, results: Map<DrillItemId, DrillResult>): Map<String, Int> {
        var changed = false
        val next = counts.toMutableMap()
        for ((id, result) in results) {
            if (result != DrillResult.RIGHT) continue
            val key = id.encode()
            val current = next[key] ?: 0
            if (current < MAX_COUNT) {
                next[key] = current + 1
                changed = true
            }
        }
        return if (changed) next else counts
    }

    /** Records the [results] of one round into [prefs]. */
    fun record(prefs: Prefs, results: Map<DrillItemId, DrillResult>) {
        if (results.isEmpty()) return
        // Decode once and compare: the history grows only with the content tables, so
        // re-decoding the stored JSON would double bounded work on every round.
        val current = decode(prefs.drillItemHistory)
        val next = updated(current, results)
        if (next == current) return
        prefs.drillItemHistory = encode(next)
    }

    /**
     * Decodes the JSON stored in [raw]. Returns an empty map on blank input or on any
     * malformed entry; counts are clamped to 0..[MAX_COUNT].
     */
    fun decode(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                // Only well-formed item ids with a sane count are kept.
                if (DrillItemId.parse(key) == null) continue
                val count = obj.optInt(key, -1)
                if (count in 1..MAX_COUNT) result[key] = count
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Encodes [counts] to JSON. */
    fun encode(counts: Map<String, Int>): String {
        val obj = JSONObject()
        for ((key, count) in counts) {
            obj.put(key, count)
        }
        return obj.toString()
    }
}
