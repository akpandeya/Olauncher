package app.olauncher.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * A [RecyclerView] that always measures itself to its full content height, never to the
 * height its parent offers.
 *
 * Why it exists: the Settings page is a ScrollView whose list row is this RecyclerView
 * with `wrap_content` and `nestedScrollingEnabled="false"` — the outer ScrollView does all
 * the scrolling. A plain ScrollView hands wrap_content children an `AT_MOST(its own height)`
 * spec; a passive child (LinearLayout) ignores the cap and reports its total, but a
 * RecyclerView's layout manager *fills* to the cap, binds only the rows that fit, and
 * reports the filled extent as its size. Sections longer than one screen (Deutsch, the
 * longest, with 15 rows) then rendered only their first screenful and could not scroll —
 * the outer ScrollView had almost no range and the RecyclerView's internal scroll is off.
 *
 * Measuring with an UNSPECIFIED height spec makes the layout manager lay out every item,
 * so the view grows to its real content and the outer ScrollView scrolls it like any
 * other static content. Row counts here are tens, not thousands, so filling everything
 * per measure pass is cheap. Width is left to the parent's spec.
 */
class FullHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.recyclerview.R.attr.recyclerViewStyle,
) : RecyclerView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // UNSPECIFIED height: the layout manager fills every item instead of stopping at
        // the parent's cap, and wrap_content then reports the true content height.
        super.onMeasure(
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
    }
}

