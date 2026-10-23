package app.olauncher.ui.words

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import app.olauncher.R
import app.olauncher.data.store.StudyGrid
import app.olauncher.helper.getColorFromAttr
import kotlin.math.min

/**
 * The 12-week study dot grid, drawn like a contribution graph: one column per
 * week, one row per weekday, studied days in the accent color and the rest a
 * faint hint of the foreground. Chrome is built programmatically because the
 * grid has no layout resource of its own.
 */
internal class StudyDotGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var cells: List<StudyGrid.Cell> = emptyList()

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColorFromAttr(R.attr.accentColor)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColorFromAttr(R.attr.primaryColorTrans50)
    }

    init {
        contentDescription = context.getString(R.string.words_stats_grid)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun submit(cells: List<StudyGrid.Cell>, studiedCount: Int, windowDays: Int) {
        this.cells = cells
        contentDescription = context.getString(
            R.string.words_stats_grid_summary, studiedCount, windowDays,
        )
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Columns drive the spacing; seven rows square it off.
        setMeasuredDimension(width, width * 7 / StudyGrid.WEEKS)
    }

    override fun onDraw(canvas: Canvas) {
        if (cells.isEmpty()) return
        val cellSize = width.toFloat() / StudyGrid.WEEKS
        val radius = min(cellSize / 3f, height / 14f)
        for (cell in cells) {
            val cx = cellSize * (cell.column + 0.5f)
            val cy = height.toFloat() * (cell.row + 0.5f) / 7f
            canvas.drawCircle(cx, cy, radius, if (cell.studied) dotPaint else emptyPaint)
        }
    }
}
