package app.olauncher.ui.today

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure sizing math for resizing Today's widget cards, plus the persisted height codec. */
internal object TodayWidgetResize {
    /** Free dragging snaps to this grid so heights land on clean values. */
    const val GRID_DP = 8

    /** Floor for any widget card, matching the fixed-height minimum used before resizing existed. */
    const val MIN_DP = 48

    /** Ceiling when the provider declares no maximum of its own. */
    const val NO_MAX_DP = 4096

    /** Drag range and starting height for one widget, in dp. */
    data class Range(val minDp: Int, val maxDp: Int, val defaultDp: Int)

    /**
     * Range from a provider's declarations, all in dp: the ordinary height, `minResizeHeight`
     * (0 when the provider leaves it unset) and `maxResizeHeight` (likewise).
     */
    fun range(defaultDp: Int, minResizeDp: Int, maxResizeDp: Int): Range {
        val min = max(minResizeDp.takeIf { it > 0 } ?: MIN_DP, MIN_DP)
        val max = max(min, if (maxResizeDp > 0) maxResizeDp else NO_MAX_DP)
        val default = defaultDp.coerceIn(min, max)
        return Range(min, max, default)
    }

    /** Snap a dragged height to the grid and keep it inside the provider's range. */
    fun snap(dp: Int, range: Range): Int =
        ((dp.toFloat() / GRID_DP).roundToInt() * GRID_DP).coerceIn(range.minDp, range.maxDp)

    /** `id:dp` pairs, sorted for a stable string. */
    fun encode(heights: Map<Int, Int>): String =
        heights.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    /** Tolerant decode: skips malformed pairs, negative ids and non-positive heights. */
    fun decode(raw: String): Map<Int, Int> = raw.split(",")
        .mapNotNull { part ->
            val pieces = part.split(":")
            val id = pieces.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val dp = pieces.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (id > 0 && dp > 0) id to dp else null
        }.toMap()
}
