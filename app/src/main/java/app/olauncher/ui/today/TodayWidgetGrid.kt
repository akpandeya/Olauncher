package app.olauncher.ui.today

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Launcher-style grid packing for Today's widget panels: children flow
 * left-to-right and wrap into [TodayWidgetResize.COLUMNS] equal columns; a
 * child occupies [LayoutParams.span] columns of the row it lands in, top
 * aligned with its row. Built-in cards stay full-width rows and are never
 * added here — only widget panels are.
 *
 * Rows are as tall as their tallest child, so shorter widgets leave empty
 * grid space below them the way they do on a home screen.
 */
internal class WidgetGridView(context: Context) : ViewGroup(context) {

    /** Column span of a panel, mutable so a live side-edge drag can re-span its panel. */
    class LayoutParams(source: MarginLayoutParams, var span: Int) : MarginLayoutParams(source)

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        LayoutParams(MarginLayoutParams(context, attrs), 1)

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(
            MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            1,
        )

    private data class Spot(val left: Int, val top: Int, val width: Int, val height: Int)

    /** Where each child sits: packed rows of spans, computed during measure. */
    private var placements: List<Spot> = emptyList()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val cell = available.toFloat() / TodayWidgetResize.COLUMNS
        val spots = ArrayList<Spot>(childCount)
        // Packing counts columns, not pixels: a pixel cursor accumulates per-child
        // rounding and can overflow by a pixel or two, wrapping full rows spuriously.
        // Column boundaries are exact, so spans filling the row always sum to its width.
        var col = 0
        var rowTop = paddingTop
        var rowHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val lp = child.layoutParams as LayoutParams
            val span = lp.span.coerceIn(1, TodayWidgetResize.COLUMNS)
            if (col > 0 && col + span > TodayWidgetResize.COLUMNS) {
                rowTop += rowHeight
                col = 0
                rowHeight = 0
            }
            val left = (col * cell).roundToInt()
            val width = ((col + span) * cell).roundToInt() - left
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                getChildMeasureSpec(
                    heightMeasureSpec,
                    paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                    lp.height,
                ),
            )
            val height = child.measuredHeight + lp.topMargin + lp.bottomMargin
            spots.add(Spot(left + paddingLeft, rowTop + lp.topMargin, width, child.measuredHeight))
            col += span
            rowHeight = max(rowHeight, height)
        }
        placements = spots
        setMeasuredDimension(
            resolveSize(available + paddingLeft + paddingRight, widthMeasureSpec),
            rowTop + rowHeight + paddingBottom,
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // Mirrored for RTL so the packing order still reads start to end.
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (index in 0 until childCount) {
            val spot = placements.getOrNull(index) ?: continue
            val child = getChildAt(index)
            val childLeft = if (rtl) width - paddingRight - (spot.left - paddingLeft) - spot.width else spot.left
            child.layout(childLeft, spot.top, childLeft + spot.width, spot.top + spot.height)
        }
    }
}
