package app.olauncher.ui.today.cards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import app.olauncher.R
import app.olauncher.helper.getColorFromAttr
import app.olauncher.wellbeing.HourlyUsage
import kotlin.math.roundToInt

/**
 * Context-free geometry calculator for [HeatRowView].
 */
object HeatRowGeometry {

    data class CellGeometry(
        val hour: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val minutes: Int,
        val isCurrent: Boolean,
        val isFuture: Boolean,
    )

    fun compute(
        hours: List<Int>,
        currentHour: Int,
        width: Float,
        paddingLeft: Float = 0f,
        paddingRight: Float = 0f,
        paddingTop: Float = 0f,
        density: Float = 1f,
        cellHeightDp: Float = 14f,
    ): List<CellGeometry> {
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0f)
        val slotWidth = availableWidth / HourlyUsage.HOURS_IN_DAY
        val gap = (1.5f * density).coerceAtMost(slotWidth * 0.25f)
        val cellWidth = (slotWidth - gap).coerceAtLeast(1f)
        val cellHeight = cellHeightDp * density
        val top = paddingTop
        val bottom = top + cellHeight

        return (0 until HourlyUsage.HOURS_IN_DAY).map { i ->
            val left = paddingLeft + i * slotWidth + gap / 2f
            val right = left + cellWidth
            val minutes = hours.getOrElse(i) { 0 }
            CellGeometry(
                hour = i,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                minutes = minutes,
                isCurrent = i == currentHour,
                isFuture = currentHour >= 0 && i > currentHour,
            )
        }
    }
}

/**
 * 24-hour heat row displaying distracting app usage throughout the day.
 * - Highlights active usage with accent color scaling by intensity.
 * - Highlights the current hour with a border and indicator dot.
 * - Leaves future hours empty.
 */
class HeatRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val heatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cellRect = RectF()

    var hours: List<Int> = emptyList()
        private set
    var currentHour: Int = -1
        private set

    init {
        contentDescription = context.getString(R.string.wellbeing_heat_row_description)
    }

    fun setHours(hours: List<Int>, currentHour: Int) {
        this.hours = hours
        this.currentHour = currentHour
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightPx = (HEIGHT_DP * density).roundToInt()
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(heightPx, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val accentColor = context.getColorFromAttr(R.attr.accentColor)
        val inkColor = context.getColorFromAttr(R.attr.primaryColor)
        val faintColor = context.getColorFromAttr(R.attr.todayChipColor)

        basePaint.color = faintColor
        strokePaint.color = inkColor
        strokePaint.strokeWidth = 1.5f * density
        dotPaint.color = inkColor

        val radius = 2f * density
        val cells = HeatRowGeometry.compute(
            hours = hours,
            currentHour = currentHour,
            width = width.toFloat(),
            paddingLeft = paddingLeft.toFloat(),
            paddingRight = paddingRight.toFloat(),
            paddingTop = paddingTop.toFloat(),
            density = density,
            cellHeightDp = CELL_HEIGHT_DP,
        )

        for (cell in cells) {
            cellRect.set(cell.left, cell.top, cell.right, cell.bottom)

            // Base empty cell
            canvas.drawRoundRect(cellRect, radius, radius, basePaint)

            // Fill heat for past or current hours with usage
            if (!cell.isFuture && cell.minutes > 0) {
                val intensity = (cell.minutes / 60f).coerceIn(0.25f, 1f)
                heatPaint.color = accentColor
                heatPaint.alpha = (intensity * 255).toInt()
                canvas.drawRoundRect(cellRect, radius, radius, heatPaint)
            }

            // Mark current hour
            if (cell.isCurrent) {
                canvas.drawRoundRect(cellRect, radius, radius, strokePaint)

                val dotY = cell.bottom + 3f * density
                if (dotY + 1.5f * density <= height) {
                    val dotX = (cell.left + cell.right) / 2f
                    canvas.drawCircle(dotX, dotY, 1.5f * density, dotPaint)
                }
            }
        }
    }

    companion object {
        const val HEIGHT_DP = 20f
        const val CELL_HEIGHT_DP = 14f
    }
}
