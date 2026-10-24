package app.olauncher.ui.today.cards

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import app.olauncher.R
import app.olauncher.helper.MotionPolicy
import app.olauncher.helper.getColorFromAttr
import app.olauncher.wellbeing.DayBar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Seven bars, one per day, with today highlighted and a dashed line at the daily allowance. */
class DayBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = LABEL_SP * resources.displayMetrics.scaledDensity
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
    }
    private val rect = RectF()

    private var bars: List<DayBar> = emptyList()
    private var scaleMinutes = 1
    private var allowanceMin = 0
    private var progressFraction = 1f
    private var animator: ValueAnimator? = null
    private var hasAnimated = false

    fun setBars(bars: List<DayBar>, scaleMinutes: Int, allowanceMin: Int) {
        this.bars = bars
        this.scaleMinutes = scaleMinutes.coerceAtLeast(1)
        this.allowanceMin = allowanceMin

        val animatorsEnabled = MotionPolicy.isAnimationsEnabled(context)
        val duration = MotionPolicy.durationMs(MotionPolicy.TODAY_DURATION_MS, animatorsEnabled)
        if (animatorsEnabled && duration > 0L && !hasAnimated) {
            hasAnimated = true
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { va ->
                    progressFraction = va.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            progressFraction = 1f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (HEIGHT_DP * density).toInt()
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return
        val ink = context.getColorFromAttr(R.attr.primaryColor)
        val faint = context.getColorFromAttr(R.attr.primaryColorTrans50)
        val labelHeight = labelPaint.textSize + 6 * density
        val chartBottom = height - labelHeight
        val chartHeight = chartBottom - paddingTop
        val slot = (width - paddingLeft - paddingRight).toFloat() / bars.size
        val barWidth = slot * BAR_FRACTION
        val radius = 4 * density
        val locale = Locale.getDefault()

        labelPaint.color = faint
        for ((index, bar) in bars.withIndex()) {
            val centre = paddingLeft + slot * index + slot / 2
            val barHeight = ((chartHeight * bar.minutes / scaleMinutes) * progressFraction).coerceAtLeast(2 * density)
            barPaint.color = if (bar.isToday) ink else faint
            rect.set(centre - barWidth / 2, chartBottom - barHeight, centre + barWidth / 2, chartBottom)
            canvas.drawRoundRect(rect, radius, radius, barPaint)
            val weekday = LocalDate.ofEpochDay(bar.epochDay).dayOfWeek
            canvas.drawText(shortName(weekday, locale), centre, height - 4 * density, labelPaint)
        }
        if (allowanceMin > 0) {
            val y = chartBottom - chartHeight * allowanceMin / scaleMinutes
            linePaint.color = ink
            linePaint.alpha = (progressFraction * 255).toInt()
            canvas.drawLine(paddingLeft.toFloat(), y, (width - paddingRight).toFloat(), y, linePaint)
        }
    }

    private fun shortName(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.SHORT, locale).take(2)

    private companion object {
        const val HEIGHT_DP = 112
        const val LABEL_SP = 12f
        const val BAR_FRACTION = 0.5f
    }
}
