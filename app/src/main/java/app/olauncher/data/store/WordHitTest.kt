package app.olauncher.data.store

import kotlin.math.sqrt

/**
 * A screen position in pixel coordinates. Plain data so hit-testing stays JVM-testable;
 * [android.graphics.Point] and [android.graphics.PointF] stay behind in the service layer.
 */
data class Point(val x: Int, val y: Int)

/**
 * The outcome of a gesture over the screen words. A tap or a single-word stroke yields
 * [Single]; a stroke that touches several words yields [Phrase] with [Phrase.head] as the
 * word the selection started at (the first touched word in reading order).
 */
sealed class Selection {
    /** Every word the selection covers, in reading order. */
    abstract val words: List<ScreenWord>

    /** The word the selection is anchored to, whatever its kind. */
    abstract val head: ScreenWord

    data class Single(val word: ScreenWord) : Selection() {
        override val words: List<ScreenWord> get() = listOf(word)
        override val head: ScreenWord get() = word
    }

    data class Phrase(override val words: List<ScreenWord>, override val head: ScreenWord) : Selection()
}

/**
 * Gesture hit-testing over the words [ScreenWords.build] produced. Works on box geometry
 * only: words without a box (nodes that shipped no character locations) are never hit by
 * a tap or a stroke and are skipped when a phrase is assembled. Context-free and
 * JVM-testable; no Android types.
 */
object WordHitTest {

    /**
     * How close a stroke's last point must be to its first point (in pixels) to count as a
     * closed shape — a circle-to-select gesture — in addition to the segments it traced.
     */
    const val CIRCLE_TOLERANCE_PX = 32

    /**
     * The word at [point]: the word whose box holds it, else the nearest word whose box is
     * at most [slop] pixels away (distance to a box is 0 when the point is inside it).
     * Ties resolve to the earlier word in reading order. Words without a box — or with a
     * zero-area one (hidden nodes park at 0,0,0,0) — are skipped.
     */
    fun tap(words: List<ScreenWord>, point: Point, slop: Int): ScreenWord? {
        var best: ScreenWord? = null
        var bestDistance = Int.MAX_VALUE
        for (word in words) {
            val box = hittable(word.box) ?: continue
            val distance = distanceTo(box, point)
            if (distance < bestDistance) {
                bestDistance = distance
                best = word
            }
        }
        // Squared pixels, so the comparison against slop stays in integers.
        return if (best != null && bestDistance <= slop * slop) best else null
    }

    /**
     * The selection a stroke over [points] produces: every word whose box a stroke segment
     * crosses, plus — when the stroke is closed (first and last point within
     * [CIRCLE_TOLERANCE_PX]) — every word whose box center lies inside the traced polygon,
     * so circling words picks them even where no segment crossed their box. The result
     * keeps reading order (the order of [words]); null-box words between touched words are
     * skipped, so a phrase contains only hittable words. No touched word returns null.
     */
    fun stroke(words: List<ScreenWord>, points: List<Point>): Selection? {
        if (points.size < 2) return null
        val closed = points.size >= 3 &&
            distance(points.first(), points.last()) <= CIRCLE_TOLERANCE_PX
        // The duplicate endpoint would make a degenerate polygon edge; drop it.
        val polygon = if (closed) points.subList(0, points.size - 1) else emptyList()

        val touched = words.filter { word ->
            val box = hittable(word.box) ?: return@filter false
            points.zipWithNext().any { (from, to) -> segmentCrossesBox(from, to, box) } ||
                (closed && polygonContains(polygon, centerOf(box)))
        }
        return when (touched.size) {
            0 -> null
            1 -> Selection.Single(touched[0])
            else -> Selection.Phrase(touched, touched.first())
        }
    }

    /**
     * Long-press plus drag: grows [selection] to the whole lines between the selection's
     * head line and the line under [point], inclusive — box geometry only. Lines are words
     * whose boxes vertically overlap (grouped in reading order, a word joins the line it
     * overlaps most); the point joins the nearest line when it falls between two lines.
     * The selection's head stays the head. A selection with a boxless head cannot be
     * anchored to geometry and is returned unchanged.
     */
    fun extendByLine(selection: Selection, point: Point, words: List<ScreenWord>): Selection {
        val headBox = hittable(selection.head.box) ?: return selection
        val lines = linesOf(words)
        val headLine = lines.indexOfFirst { line -> line.words.any { it === selection.head } }
        if (headLine < 0) return selection

        val pointLine = lines
            .withIndex()
            .minByOrNull { (_, line) -> distanceToBand(line.top, line.bottom, point.y) }
            ?.index
            ?: return selection

        val extended = lines
            .subList(minOf(headLine, pointLine), maxOf(headLine, pointLine) + 1)
            .flatMap { it.words }
        return when (extended.size) {
            0 -> selection
            1 -> Selection.Single(extended[0])
            else -> Selection.Phrase(extended, selection.head)
        }
    }

    // -- geometry helpers ---------------------------------------------------------------

    /**
     * Zero-area boxes are invisible geometry (hidden nodes park at 0,0,0,0): they would
     * be tappable at the frame's corner and form a phantom line band at y=0 — not hittable.
     */
    private fun hittable(box: ScreenBox?): ScreenBox? =
        box?.takeUnless { it.width <= 0 || it.height <= 0 }

    /** Squared distance from [point] to the nearest point of [box]; 0 when inside. */
    private fun distanceTo(box: ScreenBox, point: Point): Int {
        val dx = maxOf(box.left - point.x, 0, point.x - box.right)
        val dy = maxOf(box.top - point.y, 0, point.y - box.bottom)
        return dx * dx + dy * dy
    }

    private fun distance(a: Point, b: Point): Int {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt((dx * dx + dy * dy).toDouble()).toInt()
    }

    private fun centerOf(box: ScreenBox) = Point(
        x = box.left + box.width / 2,
        y = box.top + box.height / 2,
    )

    /**
     * Slab clipping (Liang–Barsky): the segment [from]→[to] meets [box] when the interval
     * of its parameter inside every slab is non-empty, which also covers a segment fully
     * inside the box.
     */
    private fun segmentCrossesBox(from: Point, to: Point, box: ScreenBox): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        var enter = 0.0
        var leave = 1.0
        val slabs = listOf(
            -dx to (from.x - box.left).toDouble(),
            dx to (box.right - from.x).toDouble(),
            -dy to (from.y - box.top).toDouble(),
            dy to (box.bottom - from.y).toDouble(),
        )
        for ((p, q) in slabs) {
            if (p == 0) {
                if (q < 0) return false // parallel to and outside this slab
            } else {
                val t = q / p
                if (p < 0) {
                    if (t > leave) return false
                    if (t > enter) enter = t
                } else {
                    if (t < enter) return false
                    if (t < leave) leave = t
                }
            }
        }
        return true
    }

    /** Even-point ray casting: crosses of a rightward ray with [polygon]'s edges. */
    private fun polygonContains(polygon: List<Point>, point: Point): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > point.y) != (b.y > point.y)) {
                val crossing = (b.x - a.x) * (point.y - a.y) / (b.y - a.y).toDouble() + a.x
                if (point.x < crossing) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** One vertical band of the screen: the union of its member boxes, and those members. */
    private class LineBand(var top: Int, var bottom: Int, val words: MutableList<ScreenWord>)

    /**
     * Groups box-bearing words into lines in reading order: a word joins the line whose
     * band it vertically overlaps most (no overlap starts a new line), and the band grows
     * to the union. Vertical overlap is the only line test, so diagonal or two-column
     * layouts still produce plain top-to-bottom bands here.
     */
    private fun linesOf(words: List<ScreenWord>): List<LineBand> {
        val lines = ArrayList<LineBand>()
        for (word in words) {
            val box = hittable(word.box) ?: continue
            var best: LineBand? = null
            var bestOverlap = 0
            for (line in lines) {
                val overlap = minOf(line.bottom, box.bottom) - maxOf(line.top, box.top)
                if (overlap > bestOverlap) {
                    bestOverlap = overlap
                    best = line
                }
            }
            if (best == null) lines += LineBand(box.top, box.bottom, mutableListOf(word))
            else {
                best.words += word
                best.top = minOf(best.top, box.top)
                best.bottom = maxOf(best.bottom, box.bottom)
            }
        }
        return lines
    }

    /** Distance from [y] to the band [top, bottom]; 0 when inside. */
    private fun distanceToBand(top: Int, bottom: Int, y: Int): Int =
        maxOf(top - y, 0, y - bottom)
}
