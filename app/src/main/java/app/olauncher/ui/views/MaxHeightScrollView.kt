package app.olauncher.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/** A [ScrollView] that wraps its content up to [maxHeightPx], then scrolls (0 = no cap). */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {

    var maxHeightPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = maxHeightPx
        val spec = if (cap > 0) {
            val available = View.MeasureSpec.getSize(heightMeasureSpec)
            val limit = if (available > 0) minOf(cap, available) else cap
            View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST)
        } else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, spec)
    }
}
