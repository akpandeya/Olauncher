package app.olauncher.ui.words

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * The drill help slot (#651 design §1): opens above the prompt inside the question
 * card and is capped at a fraction of the scroll body's height (about 40%), so taller
 * help scrolls inside its own slot and the prompt and answer tiles stay on screen.
 *
 * The cap is set by the screen once the body is laid out ([setMaxBodyFraction] of the
 * body height, measured in [onMeasure]).
 */
class DrillHelpCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollView(context, attrs, defStyleAttr) {

    /** Cap fraction of the scroll body's height; the design pins about 0.4. */
    var maxBodyFraction: Float = DEFAULT_MAX_BODY_FRACTION

    /** The body height measured at bind time, or 0 before the screen provides it. */
    var bodyHeight: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = (bodyHeight * maxBodyFraction).toInt()
        if (cap > 0) {
            val capped = MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, capped)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    companion object {
        /** Design §1: the help slot is capped at about 40% of the body height. */
        const val DEFAULT_MAX_BODY_FRACTION = 0.4f
    }
}
