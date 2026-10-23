package app.olauncher.ui.today

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure sizing math for resizing Today's widget cards, plus the persisted size codec.
 *
 * Widgets sit on a [WidgetGridView] of [COLUMNS] equal columns: width is a
 * column span, height stays a free dp value snapped to the 8dp grid.
 */
internal object TodayWidgetResize {
    /** Free height dragging snaps to this grid so heights land on clean values. */
    const val GRID_DP = 8

    /** Floor for any widget card, matching the fixed-height minimum used before resizing existed. */
    const val MIN_DP = 48

    /** Ceiling when the provider declares no maximum of its own. */
    const val NO_MAX_DP = 4096

    /** Columns in the Today grid; a widget's width spans 1..[COLUMNS] of them. */
    const val COLUMNS = 4

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

    /** Draggable width of one widget, counted in grid columns. */
    data class SpanRange(val minSpan: Int, val maxSpan: Int)

    /**
     * Span range from a provider's width declarations (in dp) at the live cell width:
     * `minResizeWidth` (0 when unset) and `maxResizeWidth` (likewise) translated into
     * columns, always at least one and at most [COLUMNS].
     */
    fun spanRange(minResizeWidthDp: Int, maxResizeWidthDp: Int, cellDp: Float, columns: Int = COLUMNS): SpanRange {
        val max = if (maxResizeWidthDp > 0) floor(maxResizeWidthDp / cellDp).toInt() else columns
        val capped = max.coerceIn(1, columns)
        val min = if (minResizeWidthDp > 0) ceil(minResizeWidthDp / cellDp).toInt().coerceAtLeast(1) else 1
        return SpanRange(min.coerceAtMost(capped), capped)
    }

    /** Snap a dragged width to the nearest column span and keep it inside the range. */
    fun spanFor(widthDp: Float, cellDp: Float, range: SpanRange): Int =
        (widthDp / cellDp).roundToInt().coerceIn(range.minSpan, range.maxSpan)

    /** Persisted card size: height in dp; width as a column span, null meaning full width. */
    data class Size(val widthSpan: Int?, val heightDp: Int)

    /** `id:h` pairs, or `id:w:h` when a width was ever dragged; sorted for a stable string. */
    fun encode(sizes: Map<Int, Size>): String =
        sizes.entries.sortedBy { it.key }.joinToString(",") { (id, size) ->
            if (size.widthSpan == null) "$id:${size.heightDp}" else "$id:${size.widthSpan}:${size.heightDp}"
        }

    /**
     * Tolerant decode: skips malformed pairs, negative ids and non-positive heights;
     * width spans outside 1..[COLUMNS] degrade to full width, and the old height-only
     * format (`id:h`) decodes as full width so nothing is lost across an update.
     */
    fun decode(raw: String): Map<Int, Size> = raw.split(",")
        .mapNotNull { part ->
            val pieces = part.split(":")
            val id = pieces.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val height = pieces.lastOrNull()?.toIntOrNull() ?: return@mapNotNull null
            val width = pieces.getOrNull(1)
                ?.takeIf { pieces.size == 3 }
                ?.toIntOrNull()
                ?.takeIf { it in 1..COLUMNS }
            if (id > 0 && height > 0) id to Size(width, height) else null
        }.toMap()
}
