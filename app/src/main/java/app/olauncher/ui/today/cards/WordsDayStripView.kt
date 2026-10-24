package app.olauncher.ui.today.cards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import app.olauncher.R
import app.olauncher.data.store.StudyGrid
import app.olauncher.helper.ReadableScrim
import app.olauncher.helper.getColorFromAttr
import kotlin.math.roundToInt

/**
 * 7-day strip on the Words card (#509):
 * Seven day cells (M T W ...), filled on review days, today outlined.
 */
class WordsDayStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val cellRect = RectF()

    var days: List<StudyGrid.DayStripCell> = emptyList()
        private set

    init {
        contentDescription = context.getString(R.string.words_card_day_strip_description)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setDays(days: List<StudyGrid.DayStripCell>) {
        this.days = days
        val reviewedCount = days.count { it.reviewed }
        contentDescription = context.getString(
            R.string.words_card_day_strip_summary,
            reviewedCount,
        )
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightPx = (CELL_HEIGHT_DP * density).roundToInt()
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(heightPx, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty() || width <= 0 || height <= 0) return

        val accentColor = context.getColorFromAttr(R.attr.accentColor)
        val inkColor = context.getColorFromAttr(R.attr.primaryColor)
        val faintColor = context.getColorFromAttr(R.attr.todayChipColor)
        val faintTextColor = context.getColorFromAttr(R.attr.primaryColorTrans50)

        // Contrast-safe text color on top of filled accent color
        val whiteContrast = ReadableScrim.contrast(accentColor, 0xFFFFFFFF.toInt())
        val blackContrast = ReadableScrim.contrast(accentColor, 0xFF000000.toInt())
        val textOnAccent = if (whiteContrast >= blackContrast) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        basePaint.color = faintColor
        filledPaint.color = accentColor
        strokePaint.color = inkColor
        strokePaint.strokeWidth = 1.5f * density

        val availableWidth = (width - paddingLeft - paddingRight).toFloat().coerceAtLeast(0f)
        val gap = 4f * density
        val cellCount = days.size
        val totalGaps = (cellCount - 1) * gap
        val cellWidth = ((availableWidth - totalGaps) / cellCount).coerceAtLeast(1f)
        val cellHeight = (height - paddingTop - paddingBottom).toFloat().coerceAtLeast(1f)
        val radius = 4f * density

        val textY = paddingTop + cellHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f

        for ((index, day) in days.withIndex()) {
            val left = paddingLeft + index * (cellWidth + gap)
            val right = left + cellWidth
            val top = paddingTop.toFloat()
            val bottom = top + cellHeight
            cellRect.set(left, top, right, bottom)

            // Draw cell background
            if (day.reviewed) {
                canvas.drawRoundRect(cellRect, radius, radius, filledPaint)
            } else {
                canvas.drawRoundRect(cellRect, radius, radius, basePaint)
            }

            // Outline today
            if (day.isToday) {
                canvas.drawRoundRect(cellRect, radius, radius, strokePaint)
            }

            // Draw letter label
            textPaint.color = when {
                day.reviewed -> textOnAccent
                day.isToday -> inkColor
                else -> faintTextColor
            }
            val textX = (left + right) / 2f
            canvas.drawText(day.label, textX, textY, textPaint)
        }
    }

    companion object {
        const val CELL_HEIGHT_DP = 22f
    }
}
