package app.olauncher.ui.words

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.store.QuizAnswer
import app.olauncher.data.store.WordOrderTile
import app.olauncher.helper.Haptics
import app.olauncher.helper.TouchTarget
import app.olauncher.helper.dpToPx
import app.olauncher.ui.today.attachPressScale

/**
 * Custom ViewGroup that flows child views from left-to-right and wraps to new lines.
 */
internal open class SimpleFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    var horizontalSpacing: Int = 0
    var verticalSpacing: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)

        var totalWidth = 0
        var totalHeight = paddingTop + paddingBottom
        var lineWidth = 0
        var lineHeight = 0

        val maxAvailableWidth = if (widthMode != MeasureSpec.UNSPECIFIED && widthSize > 0) widthSize else Int.MAX_VALUE

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (lineWidth + childWidth > maxAvailableWidth && lineWidth > 0) {
                totalWidth = maxOf(totalWidth, lineWidth)
                totalHeight += lineHeight + verticalSpacing
                lineWidth = childWidth
                lineHeight = childHeight
            } else {
                if (lineWidth > 0) lineWidth += horizontalSpacing
                lineWidth += childWidth
                lineHeight = maxOf(lineHeight, childHeight)
            }
        }
        totalWidth = maxOf(totalWidth, lineWidth) + paddingLeft + paddingRight
        totalHeight += lineHeight

        val measuredW = resolveSize(maxOf(totalWidth, suggestedMinimumWidth), widthMeasureSpec)
        val measuredH = resolveSize(maxOf(totalHeight, suggestedMinimumHeight), heightMeasureSpec)
        setMeasuredDimension(measuredW, measuredH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val widthSize = r - l - paddingRight
        var curLeft = paddingLeft
        var curTop = paddingTop
        var lineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (curLeft + childWidth > widthSize && curLeft > paddingLeft) {
                curLeft = paddingLeft
                curTop += lineHeight + verticalSpacing
                lineHeight = childHeight
            } else {
                lineHeight = maxOf(lineHeight, childHeight)
            }

            child.layout(curLeft, curTop, curLeft + childWidth, curTop + childHeight)
            curLeft += childWidth + horizontalSpacing
        }
    }
}

/**
 * Ordering UI for word order drills.
 *
 * Displays an answer line where selected tiles are placed and a tile bank
 * containing available shuffled tiles.
 *
 * Tapping a tile in the bank appends it to the answer line.
 * Tapping a tile in the answer line sends it back to the bank.
 * Touch targets follow [TouchTarget.MIN_TARGET_DP].
 */
class WordOrderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onTileSelected: ((WordOrderTile) -> Unit)? = null
    var onTileUnselected: ((WordOrderTile) -> Unit)? = null

    private val answerFlow = SimpleFlowLayout(context).apply {
        horizontalSpacing = 8.dpToPx()
        verticalSpacing = 8.dpToPx()
        minimumHeight = TouchTarget.MIN_TARGET_DP.dpToPx()
    }

    private val divider = View(context).apply {
        val tv = TypedValue()
        context.theme.resolveAttribute(R.attr.todayChipColor, tv, true)
        setBackgroundColor(tv.data)
    }

    private val poolFlow = SimpleFlowLayout(context).apply {
        horizontalSpacing = 8.dpToPx()
        verticalSpacing = 8.dpToPx()
        minimumHeight = TouchTarget.MIN_TARGET_DP.dpToPx()
    }

    private val tvEmptyPlaceholder = TextView(context).apply {
        text = context.getString(R.string.drill_word_order_empty_hint)
        setTextAppearance(R.style.TodayCardBodyLight)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = TouchTarget.MIN_TARGET_DP.dpToPx()
    }

    init {
        orientation = VERTICAL

        addView(answerFlow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val divParams = LayoutParams(LayoutParams.MATCH_PARENT, 1.dpToPx()).apply {
            val marginV = 16.dpToPx()
            setMargins(0, marginV, 0, marginV)
        }
        addView(divider, divParams)

        addView(poolFlow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * Renders the current state of answer tiles and available pool tiles.
     */
    fun render(
        answerTiles: List<WordOrderTile>,
        availableTiles: List<WordOrderTile>,
        answerState: QuizAnswer? = null,
    ) {
        val answered = answerState != null
        answerFlow.removeAllViews()
        poolFlow.removeAllViews()

        if (answerTiles.isEmpty() && !answered) {
            (tvEmptyPlaceholder.parent as? ViewGroup)?.removeView(tvEmptyPlaceholder)
            answerFlow.addView(tvEmptyPlaceholder)
        } else {
            for (tile in answerTiles) {
                val tv = createTileView(tile)
                if (answered) {
                    if (answerState?.correct == true) {
                        tv.isActivated = true
                    } else {
                        tv.setBackgroundResource(R.drawable.quiz_choice_wrong)
                    }
                } else {
                    tv.setOnClickListener {
                        Haptics.tick(it)
                        onTileUnselected?.invoke(tile)
                    }
                }
                answerFlow.addView(tv)
            }
        }

        for (tile in availableTiles) {
            val tv = createTileView(tile)
            if (!answered) {
                tv.setOnClickListener {
                    Haptics.tick(it)
                    onTileSelected?.invoke(tile)
                }
            } else {
                tv.isEnabled = false
                tv.alpha = 0.4f
            }
            poolFlow.addView(tv)
        }
    }

    private fun createTileView(tile: WordOrderTile): TextView = TextView(context).apply {
        text = tile.text
        setTextAppearance(R.style.TodayCardBodyBold)
        setBackgroundResource(R.drawable.quiz_choice)
        gravity = Gravity.CENTER

        val minTargetPx = TouchTarget.MIN_TARGET_DP.dpToPx()
        minHeight = minTargetPx
        minWidth = minTargetPx

        val padH = 14.dpToPx()
        val padV = 8.dpToPx()
        setPadding(padH, padV, padH, padV)
        isClickable = true
        attachPressScale()
    }
}
