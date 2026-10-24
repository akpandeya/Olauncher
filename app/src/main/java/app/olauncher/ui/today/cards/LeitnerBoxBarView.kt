package app.olauncher.ui.today.cards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.data.store.Leitner

/**
 * A thin horizontal bar displaying the distribution of words across the Leitner boxes,
 * shaded from sand (box 1, new) to sage (box [Leitner.learnedBox], learned).
 * Empty boxes take no width.
 */
class LeitnerBoxBarView @JvmOverloads constructor(
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

    var boxCounts: List<Int> = emptyList()
        private set

    init {
        contentDescription = context.getString(R.string.words_card_box_bar_description)
    }

    fun setBoxCounts(counts: List<Int>) {
        if (boxCounts == counts) return
        boxCounts = counts
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
        if (boxCounts.isEmpty() || boxCounts.sum() <= 0) return

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

        val sand = ContextCompat.getColor(context, R.color.accentSand)
        val sage = ContextCompat.getColor(context, R.color.accentSage)
        val colors = Leitner.segmentColors(sand, sage, Leitner.learnedBox)
        val widths = Leitner.segmentWidths(boxCounts, availWidth)

        var currentX = paddingLeft.toFloat()
        val lastNonEmptyIndex = (0 until minOf(Leitner.learnedBox, boxCounts.size)).indexOfLast { boxCounts[it] > 0 }

        for (i in 0 until minOf(Leitner.learnedBox, widths.size)) {
            val segWidth = widths[i]
            if (segWidth <= 0f) continue
            val left = currentX
            val right = if (i == lastNonEmptyIndex) {
                (width - paddingRight).toFloat()
            } else {
                left + segWidth
            }
            paint.color = colors.getOrElse(i) { sage }
            canvas.drawRect(left, paddingTop.toFloat(), right, (height - paddingBottom).toFloat(), paint)
            currentX = right
        }
    }
}
