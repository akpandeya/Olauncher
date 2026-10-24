package app.olauncher.ui.words

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import app.olauncher.R
import app.olauncher.data.store.StudyGrid
import app.olauncher.helper.getColorFromAttr
import kotlin.math.min

/**
 * The 12-week study dot grid, drawn like a contribution graph: one column per
 * week, one row per weekday. Month labels appear above the grid, today is
 * outlined, cells use 3 to 4 shades by review count, and zero stays neutral.
 */
internal class StudyDotGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private var cells: List<StudyGrid.Cell> = emptyList()
    private var monthLabels: List<StudyGrid.MonthLabel> = emptyList()

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColorFromAttr(R.attr.primaryColorTrans50)
    }

    private val todayOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = context.getColorFromAttr(R.attr.primaryColor)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        color = context.getColorFromAttr(R.attr.primaryColorTrans50)
    }

    private val shadePaints = Array(4) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    init {
        contentDescription = context.getString(R.string.words_stats_grid)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun updateColors() {
        val accentColor = context.getColorFromAttr(R.attr.accentColor)
        val inkColor = context.getColorFromAttr(R.attr.primaryColor)
        val faintColor = context.getColorFromAttr(R.attr.primaryColorTrans50)

        emptyPaint.color = faintColor
        todayOutlinePaint.color = inkColor
        labelPaint.color = faintColor

        val fractions = floatArrayOf(0.40f, 0.60f, 0.80f, 1.00f)
        val baseAlpha = (accentColor ushr 24 and 0xff)
        val rgb = accentColor and 0x00ffffff
        for (i in 0 until 4) {
            val alpha = (baseAlpha * fractions[i]).toInt()
            shadePaints[i].color = rgb or (alpha shl 24)
        }
    }

    fun submit(cells: List<StudyGrid.Cell>, studiedCount: Int, windowDays: Int) {
        this.cells = cells
        this.monthLabels = StudyGrid.monthLabels(cells)
        contentDescription = context.getString(
            R.string.words_stats_grid_summary, studiedCount, windowDays,
        )
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val headerHeight = if (monthLabels.isNotEmpty()) (HEADER_HEIGHT_DP * density).toInt() else 0
        val gridHeight = width * 7 / StudyGrid.WEEKS
        setMeasuredDimension(width, gridHeight + headerHeight)
    }

    override fun onDraw(canvas: Canvas) {
        if (cells.isEmpty()) return
        updateColors()

        val cellSize = width.toFloat() / StudyGrid.WEEKS
        val headerHeight = if (monthLabels.isNotEmpty()) HEADER_HEIGHT_DP * density else 0f

        // Month labels above the grid
        if (headerHeight > 0f) {
            val labelY = labelPaint.textSize
            for (label in monthLabels) {
                val textWidth = labelPaint.measureText(label.name)
                val labelX = (cellSize * label.column).coerceAtMost(width - textWidth)
                canvas.drawText(label.name, labelX, labelY, labelPaint)
            }
        }

        // Dot grid cells
        val gridHeight = height.toFloat() - headerHeight
        val radius = min(cellSize / 3f, gridHeight / 14f)
        for (cell in cells) {
            val cx = cellSize * (cell.column + 0.5f)
            val cy = headerHeight + gridHeight * (cell.row + 0.5f) / 7f
            val shade = cell.shadeLevel.coerceIn(0, 4)
            val fillPaint = if (shade == 0) emptyPaint else shadePaints[shade - 1]
            canvas.drawCircle(cx, cy, radius, fillPaint)
            if (cell.isToday) {
                canvas.drawCircle(cx, cy, radius, todayOutlinePaint)
            }
        }
    }

    companion object {
        const val HEADER_HEIGHT_DP = 16f
    }
}
