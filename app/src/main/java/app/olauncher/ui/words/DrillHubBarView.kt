package app.olauncher.ui.words

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import app.olauncher.R

/**
 * A thin score bar for a drills hub row: an [R.color.accentSage] fill over a todayChip track,
 * rounded like the Leitner box bar. The fill width is the smoothed score fraction of the
 * drill type; a never-played type shows the bare track.
 */
class DrillHubBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val clipPath = Path()
    private val barRect = RectF()

    /** Fill fraction of the bar, 0..1. */
    var scoreFraction = 0f
        private set

    fun setScoreFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (scoreFraction == clamped) return
        scoreFraction = clamped
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (4f * density).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availWidth = (width - paddingLeft - paddingRight).toFloat()
        val availHeight = (height - paddingTop - paddingBottom).toFloat()
        if (availWidth <= 0f || availHeight <= 0f) return

        val radius = availHeight / 2f
        clipPath.reset()
        barRect.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat(),
        )
        clipPath.addRoundRect(barRect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        paint.color = trackColor()
        canvas.drawRect(barRect, paint)

        if (scoreFraction > 0f) {
            paint.color = ContextCompat.getColor(context, R.color.accentSage)
            val fillRight = barRect.left + availWidth * scoreFraction
            canvas.drawRect(barRect.left, barRect.top, fillRight, barRect.bottom, paint)
        }
    }

    private fun trackColor(): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(R.attr.todayChipColor, tv, true)
        return tv.data
    }
}
