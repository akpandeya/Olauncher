package app.olauncher.helper

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import kotlin.math.max

class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var horizontalSpacing: Int = (8 * resources.displayMetrics.density).toInt()
    var verticalSpacing: Int = (8 * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var width = 0
        var height = paddingTop + paddingBottom

        var lineWidth = 0
        var lineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (lineWidth + childWidth > widthSize && lineWidth > 0) {
                width = max(width, lineWidth)
                height += lineHeight + verticalSpacing
                lineWidth = childWidth
                lineHeight = childHeight
            } else {
                lineWidth += childWidth + if (lineWidth > 0) horizontalSpacing else 0
                lineHeight = max(lineHeight, childHeight)
            }
        }
        width = max(width, lineWidth) + paddingLeft + paddingRight
        height += lineHeight

        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val widthSize = r - l - paddingLeft - paddingRight
        var curLeft = paddingLeft
        var curTop = paddingTop
        var lineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (curLeft + childWidth - paddingLeft > widthSize && curLeft > paddingLeft) {
                curLeft = paddingLeft
                curTop += lineHeight + verticalSpacing
                lineHeight = childHeight
            } else {
                lineHeight = max(lineHeight, childHeight)
            }

            child.layout(curLeft, curTop, curLeft + childWidth, curTop + childHeight)
            curLeft += childWidth + horizontalSpacing
        }
    }
}
